/*
 * Copyright 2011-2013 the original author or authors.
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

package de.schildbach.wallet;

import java.io.BufferedInputStream;
import java.io.IOException;
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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import android.util.Log;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.Io;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.format.DateUtils;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public class ExchangeRatesProvider extends ContentProvider
{
    static final protected String TAG = ExchangeRatesProvider.class.getName();

	public static class ExchangeRate
	{
		public ExchangeRate(@Nonnull final String currencyCode, @Nonnull final BigInteger rate, @Nonnull final String source)
		{
			this.currencyCode = currencyCode;
			this.rate = rate;
			this.source = source;
		}

		public final String currencyCode;
		public final BigInteger rate;
		public final String source;

		@Override
		public String toString()
		{
			return getClass().getSimpleName() + '[' + currencyCode + ':' + GenericUtils.formatValue(rate, Constants.BTC_MAX_PRECISION, 0) + ']';
		}
	}

	public static final String KEY_CURRENCY_CODE = "currency_code";
	private static final String KEY_RATE = "rate";
	private static final String KEY_SOURCE = "source";

	@CheckForNull
	private Map<String, ExchangeRate> exchangeRates = null;
	private long lastUpdated = 0;

	private static final URL BTCE_URL;
	private static final String[] BTCE_FIELDS = new String[] { "avg" };
	private static final URL VIRCUREX_URL;
	private static final String[] VIRCUREX_FIELDS = new String[] { "value" };

	static
	{
		try
		{
			BTCE_URL = new URL("https://btc-e.com/api/2/ltc_usd/ticker");
            VIRCUREX_URL = new URL("https://vircurex.com/api/get_last_trade.json?base=LTC&alt=USD");
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
		return true;
	}

	public static Uri contentUri(@Nonnull final String packageName)
	{
		return Uri.parse("content://" + packageName + '.' + "exchange_rates");
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder)
	{
		final long now = System.currentTimeMillis();

		if (exchangeRates == null || now - lastUpdated > UPDATE_FREQ_MS)
		{
			Map<String, ExchangeRate> newExchangeRates = null;
            // Attempt to get exchange rates from all providers.  Stop after first.
			if (exchangeRates == null)
				newExchangeRates = requestExchangeRates(BTCE_URL, BTCE_FIELDS);
			if (exchangeRates == null && newExchangeRates == null)
				newExchangeRates = requestExchangeRates(VIRCUREX_URL, VIRCUREX_FIELDS);

			if (newExchangeRates != null)
			{
                // Get USD conversion exchange rates from Yahoo!
                Iterator<ExchangeRate> it = newExchangeRates.values().iterator();
                Map<String, ExchangeRate> fiatRates = new YahooRatesProvider().getRates(it.next());
                if(fiatRates != null)
                    newExchangeRates.putAll(fiatRates);
				exchangeRates = newExchangeRates;
				lastUpdated = now;
			}
		}

		if (exchangeRates == null)
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
			final String selectedCode = selectionArgs[0];
			ExchangeRate rate = selectedCode != null ? exchangeRates.get(selectedCode) : null;

			if (rate == null)
			{
				final String defaultCode = defaultCurrencyCode();
				rate = defaultCode != null ? exchangeRates.get(defaultCode) : null;

				if (rate == null)
				{
					rate = exchangeRates.get(Constants.DEFAULT_EXCHANGE_CURRENCY);

					if (rate == null)
						return null;
				}
			}

			cursor.newRow().add(rate.currencyCode.hashCode()).add(rate.currencyCode).add(rate.rate.longValue()).add(rate.source);
		}

		return cursor;
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

	private static Map<String, ExchangeRate> requestExchangeRates(final URL url, final String... fields)
	{
		HttpURLConnection connection = null;
		Reader reader = null;

		try
		{
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
			connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
			connection.connect();

			final int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK)
			{
				reader = new InputStreamReader(new BufferedInputStream(connection.getInputStream(), 1024), Constants.UTF_8);
				final StringBuilder content = new StringBuilder();
				Io.copy(reader, content);

				final Map<String, ExchangeRate> rates = new TreeMap<String, ExchangeRate>();

				final JSONObject head = new JSONObject(content.toString());
				for (final Iterator<String> i = head.keys(); i.hasNext();)
				{
					String currencyCode = i.next();
					if (!"timestamp".equals(currencyCode))
					{
						final JSONObject o = head.getJSONObject(currencyCode);

						for (final String field : fields)
						{
							final String rateStr = o.optString(field, null);

							if (rateStr != null)
							{
								try
								{
									final BigInteger rate = GenericUtils.toNanoCoins(rateStr, 0);

									if (rate.signum() > 0)
									{
                                        // HACK because the only supported currency in LTC exchange rates for now
                                        // is USD
                                        currencyCode = "USD";
										rates.put(currencyCode, new ExchangeRate(currencyCode, rate, url.getHost()));
										break;
									}
								}
								catch (final ArithmeticException x)
								{
									log.warn("problem fetching exchange rate: " + currencyCode, x);
								}
							}
						}
					}
				}

				log.info("fetched exchange rates from " + url);

				return rates;
			}
			else
			{
				log.warn("http status " + responseCode + " when fetching " + url);
			}
		}
		catch (final Exception x)
		{
			log.warn("problem fetching exchange rates", x);
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

    class YahooRatesProvider {
        Map<String, ExchangeRate> getRates(ExchangeRate usdRate) {
            // Fetch all the currencies from Yahoo!
            URL url = null;
            final BigDecimal decUsdRate = GenericUtils.fromNanoCoins(usdRate.rate, 0);
            try {
                // TODO: make this look less crappy and make it easier to add currencies.
                url = new URL("http://query.yahooapis.com/v1/public/yql?q=select%20id%2C%20Rate%20from%20yahoo.finance.xchange" +
                        "%20where%20pair%20in%20(%22USDEUR%22%2C%20%22USDJPY%22%2C%20%22USDBGN%22%2C%20%22USDCZK%22%2C%20" +
                        "%22USDDKK%22%2C%20%22USDGBP%22%2C%20%22USDHUF%22%2C%20%22USDLTL%22%2C%20%22USDLVL%22%2C%20%22USDPLN" +
                        "%22%2C%20%22USDRON%22%2C%20%22USDSEK%22%2C%20%22USDCHF%22%2C%20%22USDNOK%22%2C%20%22USDHRK%22%2C%20" +
                        "%22USDRUB%22%2C%20%22USDTRY%22%2C%20%22USDAUD%22%2C%20%22USDBRL%22%2C%20%22USDCAD%22%2C%20%22USDCNY" +
                        "%22%2C%20%22USDHKD%22%2C%20%22USDIDR%22%2C%20%22USDILS%22%2C%20%22USDINR%22%2C%20%22USDKRW%22%2C%20" +
                        "%22USDMXN%22%2C%20%22USDMYR%22%2C%20%22USDNZD%22%2C%20%22USDPHP%22%2C%20%22USDSGD%22%2C%20%22USDTHB" +
                        "%22%2C%20%22USDZAR%22%2C%20%22USDISK%22)&format=json&env=store%3A%2F%2Fdatatables.org" +
                        "%2Falltableswithkeys&callback=");
            } catch (MalformedURLException e) {
                Log.i(ExchangeRatesProvider.TAG, "Failed to parse Yahoo! Finance URL");
                return null;
            }

            HttpURLConnection connection = null;
            Reader reader = null;

            try
            {
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
                connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
                connection.connect();

                final int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK)
                {
                    reader = new InputStreamReader(new BufferedInputStream(connection.getInputStream(), 1024), Constants.UTF_8);
                    final StringBuilder content = new StringBuilder();
                    Io.copy(reader, content);

                    final Map<String, ExchangeRate> rates = new TreeMap<String, ExchangeRate>();
                    JSONObject head = new JSONObject(content.toString());
                    JSONArray resultArray;
                    try {
                        head = head.getJSONObject("query");
                        head = head.getJSONObject("results");
                        resultArray = head.getJSONArray("rate");
                    } catch(JSONException e) {
                        Log.i(ExchangeRatesProvider.TAG, "Bad JSON response from Yahoo!: " + content.toString());
                        return null;
                    }
                    for(int i = 0; i < resultArray.length(); ++i) {
                        final JSONObject rateObj = resultArray.getJSONObject(i);
                        String currencyCd = rateObj.getString("id").substring(3);
                        Log.d(ExchangeRatesProvider.TAG, "Currency: " + currencyCd);
                        String rateStr = rateObj.getString("Rate");
                        Log.d(ExchangeRatesProvider.TAG, "Rate: " + rateStr);
                        Log.d(ExchangeRatesProvider.TAG, "USD Rate: " + decUsdRate.toString());
                        BigDecimal rate = new BigDecimal(rateStr);
                        Log.d(ExchangeRatesProvider.TAG, "Converted Rate: " + rate.toString());
                        rate = decUsdRate.multiply(rate);
                        Log.d(ExchangeRatesProvider.TAG, "Final Rate: " + rate.toString());
                        if (rate.signum() > 0)
                        {
                            rates.put(currencyCd, new ExchangeRate(currencyCd,
                                    GenericUtils.toNanoCoins(rate.toString(), 0), url.getHost()));
                        }
                    }
                    Log.i(ExchangeRatesProvider.TAG, "Fetched exchange rates from " + url);
                    return rates;
                } else {
                    Log.i(ExchangeRatesProvider.TAG, "Bad response code from Yahoo!: " + responseCode);
                }
            }
            catch (final Exception x)
            {
                Log.w(ExchangeRatesProvider.TAG, "Problem fetching exchange rates", x);
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
    }
}
