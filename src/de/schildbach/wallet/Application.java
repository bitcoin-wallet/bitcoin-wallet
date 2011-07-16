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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.google.bitcoin.core.Address;
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

		ErrorReporter.getInstance().init(this);

		networkParameters = Constants.TEST ? NetworkParameters.testNet() : NetworkParameters.prodNet();

		loadWallet();

		backupKeys();

		wallet.addEventListener(walletEventListener);
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
		final String filename = Constants.TEST ? Constants.WALLET_FILENAME_TEST : Constants.WALLET_FILENAME_PROD;
		final int mode = Constants.TEST ? Constants.WALLET_MODE_TEST : Constants.WALLET_MODE_PROD;

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
		catch (final StackOverflowError x)
		{
			wallet = restoreWallet();
			saveWallet();
		}
		catch (final IOException x)
		{
			throw new Error("cannot load wallet", x);
		}
	}

	public void addNewKeyToWallet()
	{
		wallet.keychain.add(new ECKey());

		saveWallet();

		backupKeys();
	}

	public void saveWallet()
	{
		final String filename = Constants.TEST ? Constants.WALLET_FILENAME_TEST : Constants.WALLET_FILENAME_PROD;
		final int mode = Constants.TEST ? Constants.WALLET_MODE_TEST : Constants.WALLET_MODE_PROD;

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

	private void backupKeys()
	{
		final ECKey firstKey = wallet.keychain.get(0);

		if (firstKey != null)
		{
			try
			{
				final byte[] asn1 = firstKey.toASN1();

				final OutputStream os = openFileOutput(Constants.WALLET_KEY_BACKUP_ASN1, Constants.WALLET_MODE);
				os.write(asn1);
				os.close();
			}
			catch (final IOException x)
			{
				x.printStackTrace();
			}
		}

		try
		{
			final Writer out = new OutputStreamWriter(openFileOutput(Constants.WALLET_KEY_BACKUP_BASE58, Constants.WALLET_MODE), "UTF-8");

			for (final ECKey key : wallet.keychain)
			{
				out.write(key.toOwnBase58());
				out.write('\n');
			}

			out.close();
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}
	}

	private Wallet restoreWallet()
	{
		try
		{
			final Wallet wallet = new Wallet(networkParameters);
			final BufferedReader in = new BufferedReader(new InputStreamReader(openFileInput(Constants.WALLET_KEY_BACKUP_BASE58), "UTF-8"));

			while (true)
			{
				final String line = in.readLine();
				if (line == null)
					break;

				final ECKey key = ECKey.fromOwnBase58(line);
				wallet.keychain.add(key);
			}

			in.close();

			final File file = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE),
					Constants.BLOCKCHAIN_FILENAME);
			file.delete();

			return wallet;
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	public Address determineSelectedAddress()
	{
		final ArrayList<ECKey> keychain = wallet.keychain;

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String defaultAddress = keychain.get(0).toAddress(networkParameters).toString();
		final String selectedAddress = prefs.getString(Constants.PREFS_KEY_SELECTED_ADDRESS, defaultAddress);

		// sanity check
		for (final ECKey key : keychain)
		{
			final Address address = key.toAddress(networkParameters);
			if (address.toString().equals(selectedAddress))
				return address;
		}

		throw new IllegalStateException("address not in keychain: " + selectedAddress);
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

	public final int versionCode()
	{
		try
		{
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		}
		catch (NameNotFoundException x)
		{
			return 0;
		}
	}
}
