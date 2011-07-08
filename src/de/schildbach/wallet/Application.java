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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;

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
}
