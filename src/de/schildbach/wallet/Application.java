/*
 * Copyright 2010 the original author or authors.
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Handler;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

/**
 * @author Andreas Schildbach
 */
public class Application extends android.app.Application
{
	private NetworkParameters networkParameters;
	private Wallet wallet;

	private final Handler handler = new Handler();

	final private WalletEventListener walletEventListener = new WalletEventListener()
	{
		@Override
		public void onPendingCoinsReceived(final Wallet wallet, final Transaction tx)
		{
			onEverything();
		}

		@Override
		public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			onEverything();
		}

		@Override
		public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			onEverything();
		}

		@Override
		public void onReorganize()
		{
			onEverything();
		}

		@Override
		public void onDeadTransaction(final Transaction deadTx, final Transaction replacementTx)
		{
			onEverything();
		}

		private void onEverything()
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					saveWallet();
				}
			});
		}
	};

	@Override
	public void onCreate()
	{
		super.onCreate();

		ErrorReporter.getInstance().init(this, isTest());

		networkParameters = isTest() ? NetworkParameters.testNet() : NetworkParameters.prodNet();

		loadWallet();

		wallet.addEventListener(walletEventListener);
	}

	public boolean isTest()
	{
		return getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getBoolean(Constants.PREFS_KEY_TEST,
				Constants.TEST);
	}

	public NetworkParameters getNetworkParameters()
	{
		return networkParameters;
	}

	public Wallet getWallet()
	{
		return wallet;
	}

	private void loadWallet()
	{
		final String filename = isTest() ? Constants.WALLET_FILENAME_TEST : Constants.WALLET_FILENAME_PROD;
		final int mode = isTest() ? Constants.WALLET_MODE_TEST : Constants.WALLET_MODE_PROD;

		try
		{
			wallet = Wallet.loadFromFileStream(openFileInput(filename));
			System.out.println("wallet loaded from: " + getFilesDir() + "/" + filename);
		}
		catch (final FileNotFoundException x)
		{
			wallet = new Wallet(networkParameters);
			wallet.keychain.add(new ECKey());

			try
			{
				wallet.saveToFileStream(openFileOutput(filename, mode));
				System.out.println("wallet created: " + getFilesDir() + "/" + filename);
			}
			catch (final IOException x2)
			{
				throw new Error("wallet cannot be created", x2);
			}
		}
		catch (final IOException x)
		{
			throw new Error("cannot load wallet", x);
		}
	}

	public void saveWallet()
	{
		final String filename = isTest() ? Constants.WALLET_FILENAME_TEST : Constants.WALLET_FILENAME_PROD;
		final int mode = isTest() ? Constants.WALLET_MODE_TEST : Constants.WALLET_MODE_PROD;

		try
		{
			wallet.saveToFileStream(openFileOutput(filename, mode));
			System.out.println("wallet saved to: " + getFilesDir() + "/" + filename);
		}
		catch (IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	public Map<String, Double> getExchangeRates()
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
			copy(is, content);
			is.close();

			final Map<String, Double> rates = new HashMap<String, Double>();

			final JSONObject head = new JSONObject(content.toString());
			for (final Iterator<String> i = head.keys(); i.hasNext();)
			{
				final String currencyCode = i.next();

				final JSONObject o = head.getJSONObject(currencyCode);
				double rate = o.optDouble("24h", 0);
				if (rate == 0)
					rate = o.optDouble("7d", 0);
				if (rate == 0)
					rate = o.optDouble("30d", 0);

				if (rate != 0)
					rates.put(currencyCode, rate);
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

	private static final long copy(final Reader reader, final StringBuilder builder) throws IOException
	{
		final char[] buffer = new char[256];
		long count = 0;
		int n = 0;
		while (-1 != (n = reader.read(buffer)))
		{
			builder.append(buffer, 0, n);
			count += n;
		}
		return count;
	}
}
