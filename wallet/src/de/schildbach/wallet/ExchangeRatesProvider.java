/*
 * Copyright 2011-2012 the original author or authors.
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
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import de.schildbach.wallet.util.IOUtils;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider
{
	public static final Uri CONTENT_URI = Uri.parse("content://" + Constants.PACKAGE_NAME + '.' + "exchange_rates");

	public static final String KEY_CURRENCY_CODE = "currency_code";
	public static final String KEY_EXCHANGE_RATE = "exchange_rate";

	private Map<String, Double> exchangeRates = null;

	@Override
	public boolean onCreate()
	{
		return true;
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder)
	{
		if (exchangeRates == null)
		{
			exchangeRates = getExchangeRates();
			if (exchangeRates == null)
				return null;
		}

		final MatrixCursor cursor = new MatrixCursor(new String[] { BaseColumns._ID, KEY_CURRENCY_CODE, KEY_EXCHANGE_RATE });

		if (selection == null)
		{
			for (final Map.Entry<String, Double> entry : exchangeRates.entrySet())
				cursor.newRow().add(entry.getKey().hashCode()).add(entry.getKey()).add(entry.getValue());
		}
		else if (selection.equals(KEY_CURRENCY_CODE))
		{
			final String code = selectionArgs[0];
			final Double rate = exchangeRates.get(code);
			cursor.newRow().add(code.hashCode()).add(code).add(rate);
		}

		return cursor;
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

	private static Map<String, Double> getExchangeRates()
	{
		try
		{
			final URLConnection connection = new URL("http://bitcoincharts.com/t/weighted_prices.json").openConnection();
			// https://mtgox.com/code/data/ticker.php
			// https://bitmarket.eu/api/ticker
			// http://bitcoincharts.com/t/weighted_prices.json

			connection.connect();
			final Reader is = new InputStreamReader(new BufferedInputStream(connection.getInputStream()));
			final StringBuilder content = new StringBuilder();
			IOUtils.copy(is, content);
			is.close();

			final Map<String, Double> rates = new TreeMap<String, Double>();

			final JSONObject head = new JSONObject(content.toString());
			for (final Iterator<String> i = head.keys(); i.hasNext();)
			{
				final String currencyCode = i.next();
				if (!"timestamp".equals(currencyCode))
				{
					final JSONObject o = head.getJSONObject(currencyCode);
					double rate = o.optDouble("24h", 0);
					if (rate == 0)
						rate = o.optDouble("7d", 0);
					if (rate == 0)
						rate = o.optDouble("30d", 0);

					if (rate != 0)
						rates.put(currencyCode, rate);
				}
			}

			return rates;
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}
		catch (final JSONException x)
		{
			x.printStackTrace();
		}

		return null;
	}
}
