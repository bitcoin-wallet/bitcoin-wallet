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

package de.schildbach.wallet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.format.DateUtils;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;
import com.google.common.base.Charsets;

import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.Io;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider
{
	public static class ExchangeRate
	{
		public ExchangeRate(@Nonnull final com.google.bitcoin.utils.ExchangeRate rate, final String source)
		{
			this.rate = rate;
			this.source = source;
		}

		public final com.google.bitcoin.utils.ExchangeRate rate;
		public final String source;

		public String getCurrencyCode()
		{
			return rate.fiat.currencyCode;
		}

		@Override
		public String toString()
		{
			return getClass().getSimpleName() + '[' + rate.fiat + ']';
		}
	}

	public static final String KEY_CURRENCY_CODE = "currency_code";
	private static final String KEY_RATE_COIN = "rate_coin";
	private static final String KEY_RATE_FIAT = "rate_fiat";
	private static final String KEY_SOURCE = "source";

	public static final String QUERY_PARAM_Q = "q";
	private static final String QUERY_PARAM_OFFLINE = "offline";

	private Configuration config;
	private String userAgent;

	@CheckForNull
	private Map<String, ExchangeRate> exchangeRates = null;
	private long lastUpdated = 0;

	private static final URL BITCOINAVERAGE_URL;
	private static final String[] BITCOINAVERAGE_FIELDS = new String[] { "24h_avg", "last" };
	private static final String BITCOINAVERAGE_SOURCE = "BitcoinAverage.com";
	private static final URL BLOCKCHAININFO_URL;
	private static final String[] BLOCKCHAININFO_FIELDS = new String[] { "15m" };
	private static final String BLOCKCHAININFO_SOURCE = "blockchain.info";

	// https://bitmarket.eu/api/ticker

	static
	{
		try
		{
			BITCOINAVERAGE_URL = new URL("https://api.bitcoinaverage.com/custom/abw");
			BLOCKCHAININFO_URL = new URL("https://blockchain.info/ticker");
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
			exchangeRates.put(cachedExchangeRate.getCurrencyCode(), cachedExchangeRate);
		}

		return true;
	}

	public static Uri contentUri(@Nonnull final String packageName, final boolean offline)
	{
		final Uri.Builder uri = Uri.parse("content://" + packageName + '.' + "exchange_rates").buildUpon();
		if (offline)
			uri.appendQueryParameter(QUERY_PARAM_OFFLINE, "1");
		return uri.build();
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder)
	{
		final long now = System.currentTimeMillis();

		final boolean offline = uri.getQueryParameter(QUERY_PARAM_OFFLINE) != null;

		if (!offline && (lastUpdated == 0 || now - lastUpdated > UPDATE_FREQ_MS))
		{
			Map<String, ExchangeRate> newExchangeRates = null;
			if (newExchangeRates == null)
				newExchangeRates = requestExchangeRates(BITCOINAVERAGE_URL, userAgent, BITCOINAVERAGE_SOURCE, BITCOINAVERAGE_FIELDS);
			if (newExchangeRates == null)
				newExchangeRates = requestExchangeRates(BLOCKCHAININFO_URL, userAgent, BLOCKCHAININFO_SOURCE, BLOCKCHAININFO_FIELDS);

			if (newExchangeRates != null)
			{
				exchangeRates = newExchangeRates;
				lastUpdated = now;

				final ExchangeRate exchangeRateToCache = bestExchangeRate(config.getExchangeCurrencyCode());
				if (exchangeRateToCache != null)
					config.setCachedExchangeRate(exchangeRateToCache);
			}
		}

		if (exchangeRates == null)
			return null;

		final MatrixCursor cursor = new MatrixCursor(new String[] { BaseColumns._ID, KEY_CURRENCY_CODE, KEY_RATE_COIN, KEY_RATE_FIAT, KEY_SOURCE });

		if (selection == null)
		{
			for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet())
			{
				final ExchangeRate exchangeRate = entry.getValue();
				final com.google.bitcoin.utils.ExchangeRate rate = exchangeRate.rate;
				final String currencyCode = exchangeRate.getCurrencyCode();
				cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(rate.coin.value).add(rate.fiat.value).add(exchangeRate.source);
			}
		}
		else if (selection.equals(QUERY_PARAM_Q))
		{
			final String selectionArg = selectionArgs[0].toLowerCase(Locale.US);
			for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet())
			{
				final ExchangeRate exchangeRate = entry.getValue();
				final com.google.bitcoin.utils.ExchangeRate rate = exchangeRate.rate;
				final String currencyCode = exchangeRate.getCurrencyCode();
				final String currencySymbol = GenericUtils.currencySymbol(currencyCode);
				if (currencyCode.toLowerCase(Locale.US).contains(selectionArg) || currencySymbol.toLowerCase(Locale.US).contains(selectionArg))
					cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(rate.coin.value).add(rate.fiat.value).add(exchangeRate.source);
			}
		}
		else if (selection.equals(KEY_CURRENCY_CODE))
		{
			final String selectionArg = selectionArgs[0];
			final ExchangeRate exchangeRate = bestExchangeRate(selectionArg);
			if (exchangeRate != null)
			{
				final com.google.bitcoin.utils.ExchangeRate rate = exchangeRate.rate;
				final String currencyCode = exchangeRate.getCurrencyCode();
				cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(rate.coin.value).add(rate.fiat.value).add(exchangeRate.source);
			}
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
		final Coin rateCoin = Coin.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_COIN)));
		final Fiat rateFiat = Fiat.valueOf(currencyCode, cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_FIAT)));
		final String source = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_SOURCE));

		return new ExchangeRate(new com.google.bitcoin.utils.ExchangeRate(rateCoin, rateFiat), source);
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

	private static Map<String, ExchangeRate> requestExchangeRates(final URL url, final String userAgent, final String source, final String... fields)
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

				reader = new InputStreamReader(is, Charsets.UTF_8);
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
							final String rateStr = o.optString(field, null);

							if (rateStr != null)
							{
								try
								{
									final Fiat rate = Fiat.parseFiat(currencyCode, rateStr);

									if (rate.signum() > 0)
									{
										rates.put(currencyCode, new ExchangeRate(new com.google.bitcoin.utils.ExchangeRate(rate), source));
										break;
									}
								}
								catch (final NumberFormatException x)
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
				log.warn("http status {} when fetching exchange rates from {}", responseCode, url);
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
}
