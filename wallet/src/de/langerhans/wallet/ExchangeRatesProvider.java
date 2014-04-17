/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.langerhans.wallet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Currency;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import de.langerhans.wallet.util.GenericUtils;
import de.langerhans.wallet.util.Io;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider
{
	public static class ExchangeRate
	{
		public ExchangeRate(@Nonnull final String currencyCode, @Nonnull final BigInteger rate, final String source)
		{
			this.currencyCode = currencyCode;
			this.rate = rate;
			this.source = source;
		}

		public final String currencyCode;
		public BigInteger rate;
		public final String source;

		@Override
		public String toString()
		{
			return getClass().getSimpleName() + '[' + currencyCode + ':' + GenericUtils.formatDebugValue(rate) + ']';
		}
	}

	public static final String KEY_CURRENCY_CODE = "currency_code";
	private static final String KEY_RATE = "rate";
	private static final String KEY_SOURCE = "source";

	private Configuration config;
	private String userAgent;

	@CheckForNull
	private Map<String, ExchangeRate> exchangeRates = null;
	private long lastUpdated = 0;
    private float dogeBtcConversion = -1;

	private static final URL BITCOINAVERAGE_URL;
	private static final String[] BITCOINAVERAGE_FIELDS = new String[] { "24h_avg", "last" };
    private static final String BITCOINAVERAGE_SOURCE = "BitcoinAverage.com";
    private static final String BLOCKCHAININFO_SOURCE = "blockchain.info";
	private static final URL BLOCKCHAININFO_URL;
	private static final String[] BLOCKCHAININFO_FIELDS = new String[] { "15m" };
    private static final URL DOGEPOOL_URL;
    private static final URL CRYPTSY_URL;
    private static final URL VIRCUREX_URL;

	// https://bitmarket.eu/api/ticker

	static
	{
		try
		{
			BITCOINAVERAGE_URL = new URL("https://api.bitcoinaverage.com/custom/abw");
            BLOCKCHAININFO_URL = new URL("https://blockchain.info/ticker");
            DOGEPOOL_URL = new URL("http://dogepool.com/lastdoge");
            CRYPTSY_URL = new URL("http://pubapi.cryptsy.com/api.php?method=singlemarketdata&marketid=132");
            VIRCUREX_URL = new URL("https://api.vircurex.com/api/get_last_trade.json?base=DOGE&alt=BTC");
		}
		catch (final MalformedURLException x)
		{
			throw new RuntimeException(x); // cannot happen
		}
	}

	private static final long UPDATE_FREQ_MS = 10 * DateUtils.MINUTE_IN_MILLIS;

	private static final Logger log = LoggerFactory.getLogger(ExchangeRatesProvider.class);

	@Override
	public boolean onCreate()
	{
		final Context context = getContext();

		this.config = new Configuration(PreferenceManager.getDefaultSharedPreferences(context));

		this.userAgent = WalletApplication.httpUserAgent(WalletApplication.packageInfoFromContext(context).versionName);

		final ExchangeRate cachedExchangeRate = config.getCachedExchangeRate();
		if (cachedExchangeRate != null)
		{
			exchangeRates = new TreeMap<String, ExchangeRate>();
			exchangeRates.put(cachedExchangeRate.currencyCode, cachedExchangeRate);
		}

		return true;
	}

	public static Uri contentUri(@Nonnull final String packageName)
	{
		return Uri.parse("content://" + packageName + '.' + "exchange_rates");
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder)
	{
		if (Constants.BUG_OPENSSL_HEARTBLEED)
			return null;

		final long now = System.currentTimeMillis();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        int provider = Integer.parseInt(sp.getString(Configuration.PREFS_KEY_EXCHANGE_PROVIDER, "0"));
        boolean forceRefresh = sp.getBoolean(Configuration.PREFS_KEY_EXCHANGE_FORCE_REFRESH, false);
        if (forceRefresh)
            sp.edit().putBoolean(Configuration.PREFS_KEY_EXCHANGE_FORCE_REFRESH, false).commit();

		if (lastUpdated == 0 || now - lastUpdated > UPDATE_FREQ_MS)
		{
            float newDogeBtcConversion = -1;
            if ((dogeBtcConversion == -1 && newDogeBtcConversion == -1) || forceRefresh)
                newDogeBtcConversion = requestDogeBtcConversion(provider);

            if (newDogeBtcConversion != -1)
                dogeBtcConversion = newDogeBtcConversion;

            if (dogeBtcConversion == -1)
                return null;

			Map<String, ExchangeRate> newExchangeRates = null;
			if (newExchangeRates == null)
				newExchangeRates = requestExchangeRates(BITCOINAVERAGE_URL, dogeBtcConversion, userAgent, BITCOINAVERAGE_SOURCE, BITCOINAVERAGE_FIELDS);
			if (newExchangeRates == null)
				newExchangeRates = requestExchangeRates(BLOCKCHAININFO_URL, dogeBtcConversion, userAgent, BLOCKCHAININFO_SOURCE, BLOCKCHAININFO_FIELDS);

			if (newExchangeRates != null)
			{
                String providerUrl;
                switch (provider) {
                    case 0:
                        providerUrl = "http://www.cryptsy.com";
                        break;
                    case 1:
                        providerUrl = "http://www.vircurex.com";
                        break;
                    default:
                        providerUrl = "";
                        break;
                }
                float mBTCRate = dogeBtcConversion*1000;
                String strmBTCRate = String.format(Locale.US, "%.5f", mBTCRate).replace(',', '.');
                newExchangeRates.put("mBTC", new ExchangeRate("mBTC", new BigDecimal(GenericUtils.toNanoCoins(strmBTCRate, 0)).toBigInteger(), providerUrl));
                newExchangeRates.put("DOGE", new ExchangeRate("DOGE", BigInteger.valueOf(100000000), "priceofdoge.com"));
				exchangeRates = newExchangeRates;
				lastUpdated = now;

				final ExchangeRate exchangeRateToCache = bestExchangeRate(config.getExchangeCurrencyCode());
				if (exchangeRateToCache != null)
					config.setCachedExchangeRate(exchangeRateToCache);
			}
		}

		if (exchangeRates == null || dogeBtcConversion == -1)
			return null;

		final MatrixCursor cursor = new MatrixCursor(new String[] { BaseColumns._ID, KEY_CURRENCY_CODE, KEY_RATE, KEY_SOURCE });

		if (selection == null)
		{
			for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet())
			{
				final ExchangeRate rate = entry.getValue();
				cursor.newRow().add(rate.currencyCode.hashCode()).add(rate.currencyCode).add(rate.rate.longValue()).add(rate.source);
			}
		}
		else if (selection.equals(KEY_CURRENCY_CODE))
		{
			final ExchangeRate rate = bestExchangeRate(selectionArgs[0]);
			if (rate != null)
				cursor.newRow().add(rate.currencyCode.hashCode()).add(rate.currencyCode).add(rate.rate.longValue()).add(rate.source);
		}

		return cursor;
	}

	private ExchangeRate bestExchangeRate(final String currencyCode)
	{
		ExchangeRate rate = currencyCode != null ? exchangeRates.get(currencyCode) : null;
		if (rate != null)
			return rate;

		final String defaultCode = defaultCurrencyCode();
		rate = defaultCode != null ? exchangeRates.get(defaultCode) : null;

		if (rate != null)
			return rate;

		return exchangeRates.get(Constants.DEFAULT_EXCHANGE_CURRENCY);
	}

    private String defaultCurrencyCode()
	{
		try
		{
			return Currency.getInstance(Locale.getDefault()).getCurrencyCode();
		}
		catch (final IllegalArgumentException x)
		{
			return null;
		}
	}

	public static ExchangeRate getExchangeRate(@Nonnull final Cursor cursor)
	{
		final String currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE));
		final BigInteger rate = BigInteger.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE)));
		final String source = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_SOURCE));

		return new ExchangeRate(currencyCode, rate, source);
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public String getType(final Uri uri)
	{
		throw new UnsupportedOperationException();
	}

	private static Map<String, ExchangeRate> requestExchangeRates(final URL url, float dogeBtcConversion, final String userAgent, final String source, final String... fields)
	{
		final long start = System.currentTimeMillis();

		HttpURLConnection connection = null;
		Reader reader = null;

		try
		{
			connection = (HttpURLConnection) url.openConnection();

			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
			connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
			connection.addRequestProperty("User-Agent", userAgent);
			connection.addRequestProperty("Accept-Encoding", "gzip");
			connection.connect();

			final int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK)
			{
				final String contentEncoding = connection.getContentEncoding();

				InputStream is = new BufferedInputStream(connection.getInputStream(), 1024);
				if ("gzip".equalsIgnoreCase(contentEncoding))
					is = new GZIPInputStream(is);

				reader = new InputStreamReader(is, Constants.UTF_8);
				final StringBuilder content = new StringBuilder();
				final long length = Io.copy(reader, content);

				final Map<String, ExchangeRate> rates = new TreeMap<String, ExchangeRate>();

				final JSONObject head = new JSONObject(content.toString());
				for (final Iterator<String> i = head.keys(); i.hasNext();)
				{
					final String currencyCode = i.next();
					if (!"timestamp".equals(currencyCode))
					{
						final JSONObject o = head.getJSONObject(currencyCode);

						for (final String field : fields)
						{
							final String rate = o.optString(field, null);

							if (rate != null)
							{
								try
								{
									BigDecimal btcRate = new BigDecimal(GenericUtils.toNanoCoins(rate, 0));
                                	BigInteger dogeRate = btcRate.multiply(BigDecimal.valueOf(dogeBtcConversion)).toBigInteger();

									if (dogeRate.signum() > 0)
									{
										rates.put(currencyCode, new ExchangeRate(currencyCode, dogeRate, source));
										break;
									}
								}
								catch (final ArithmeticException x)
								{
									log.warn("problem fetching {} exchange rate from {} ({}): {}", currencyCode, url, contentEncoding, x.getMessage());
								}
							}
						}
					}
				}

				log.info("fetched exchange rates from {} ({}), {} chars, took {} ms", url, contentEncoding, length, System.currentTimeMillis()
						- start);

				return rates;
			}
			else
			{
				log.warn("http status {} when fetching {}", responseCode, url);
			}
		}
		catch (final Exception x)
		{
			log.warn("problem fetching exchange rates from " + url, x);
		}
		finally
		{
			if (reader != null)
			{
				try
				{
					reader.close();
				}
				catch (final IOException x)
				{
					// swallow
				}
			}

			if (connection != null)
				connection.disconnect();
		}

		return null;
	}

    private static float requestDogeBtcConversion(int provider) {
        HttpURLConnection connection = null;
        Reader reader = null;
        URL providerUrl;
        switch (provider) {
            case 0:
                providerUrl = DOGEPOOL_URL;
                break;
            case 1:
                providerUrl = VIRCUREX_URL;
                break;
            default:
                providerUrl = DOGEPOOL_URL;
                break;
        }

        try
        {
            connection = (HttpURLConnection) providerUrl.openConnection();
            connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
            connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
            connection.connect();

            final int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                reader = new InputStreamReader(new BufferedInputStream(connection.getInputStream(), 1024), Constants.UTF_8);
                final StringBuilder content = new StringBuilder();
                Io.copy(reader, content);

                try
                {
                    float rate;
                    switch (provider) {
                        case 0:
                            /*rate = Float.parseFloat(
                                json.getJSONObject("return")
                                    .getJSONObject("markets")
                                    .getJSONObject("DOGE")
                                    .getString("lasttradeprice"));*/ //For later use.
                            rate = Float.parseFloat(content.toString());
                            break;
                        case 1:
                            final JSONObject json = new JSONObject(content.toString());
                            rate = Float.parseFloat(
                                    json.getString("value"));
                            break;
                        default:
                            return -1;
                    }
                    return rate;
                } catch (NumberFormatException e)
                {
                    log.debug("Couldn't get the current exchnage rate from provider " + String.valueOf(provider));
                    return -1;
                }

            }
            else
            {
                log.debug("http status " + responseCode + " when fetching " + providerUrl);
            }
        }
        catch (final Exception x)
        {
            log.debug("problem reading exchange rates", x);
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (final IOException x)
                {
                    // swallow
                }
            }

            if (connection != null)
                connection.disconnect();
        }

        return -1;
    }
}
