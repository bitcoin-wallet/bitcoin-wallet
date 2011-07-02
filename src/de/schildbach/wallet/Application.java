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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;

import android.content.Context;
import android.os.Debug;
import android.os.Handler;

import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.BoundedOverheadBlockStore;

/**
 * @author Andreas Schildbach
 */
public class Application extends android.app.Application
{
	private Wallet wallet;
	private BlockStore blockStore;
	private BlockChain blockChain;

	private final Handler handler = new Handler();

	final private WalletEventListener walletEventListener = new WalletEventListener()
	{
		@Override
		public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					saveWallet();
				}
			});
		}

		@Override
		public void onReorganize()
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

		System.out.println("Heap size: " + (Debug.getNativeHeapSize() / 1024) + " kB");

		loadWallet();

		wallet.addEventListener(walletEventListener);

		try
		{
			blockStore = new BoundedOverheadBlockStore(Constants.NETWORK_PARAMS, new File(getDir("blockstore", Context.MODE_WORLD_READABLE
					| Context.MODE_WORLD_WRITEABLE), "blockchain"));

			blockChain = new BlockChain(Constants.NETWORK_PARAMS, wallet, blockStore);
		}
		catch (final BlockStoreException x)
		{
			throw new Error("blockstore cannot be created", x);
		}
	}

	public Wallet getWallet()
	{
		return wallet;
	}

	public BlockStore getBlockStore()
	{
		return blockStore;
	}

	public BlockChain getBlockChain()
	{
		return blockChain;
	}

	private void loadWallet()
	{
		final String filename = walletFilename();

		try
		{
			wallet = Wallet.loadFromFileStream(openFileInput(filename));
			System.out.println("wallet loaded from: " + getFilesDir() + "/" + filename);
		}
		catch (final FileNotFoundException x)
		{
			wallet = new Wallet(Constants.NETWORK_PARAMS);
			wallet.keychain.add(new ECKey());

			try
			{
				wallet.saveToFileStream(openFileOutput(filename, walletMode()));
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
		try
		{
			final String filename = walletFilename();
			wallet.saveToFileStream(openFileOutput(filename, walletMode()));
			System.out.println("wallet saved to: " + getFilesDir() + "/" + filename);
		}
		catch (IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	private static String walletFilename()
	{
		return "wallet" + (Constants.TEST ? "-testnet" : "");
	}

	private static int walletMode()
	{
		return (Constants.TEST ? Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE : Context.MODE_PRIVATE);
	}
}
