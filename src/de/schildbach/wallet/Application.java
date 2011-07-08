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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;

import android.content.Context;
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

		ErrorReporter.getInstance().init(this);

		loadWallet();

		wallet.addEventListener(walletEventListener);

		try
		{
			final File file = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE),
					Constants.BLOCKCHAIN_FILENAME);

			if (!file.exists())
			{
				// copy snapshot
				try
				{
					final long t = System.currentTimeMillis();

					final InputStream is = new BufferedInputStream(getAssets().open(Constants.BLOCKCHAIN_SNAPSHOT_FILENAME));
					final OutputStream os = new FileOutputStream(file);

					System.out.println("copying blockchain snapshot");
					final byte[] buf = new byte[8192];
					int read;
					while (-1 != (read = is.read(buf)))
						os.write(buf, 0, read);
					os.close();
					is.close();
					System.out.println("finished copying, took " + (System.currentTimeMillis() - t) + " ms");
				}
				catch (final IOException x)
				{
					file.delete();
				}
			}

			blockStore = new BoundedOverheadBlockStore(Constants.NETWORK_PARAMS, file);

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
		try
		{
			wallet = Wallet.loadFromFileStream(openFileInput(Constants.WALLET_FILENAME));
			System.out.println("wallet loaded from: " + getFilesDir() + "/" + Constants.WALLET_FILENAME);
		}
		catch (final FileNotFoundException x)
		{
			wallet = new Wallet(Constants.NETWORK_PARAMS);
			wallet.keychain.add(new ECKey());

			try
			{
				wallet.saveToFileStream(openFileOutput(Constants.WALLET_FILENAME, Constants.WALLET_MODE));
				System.out.println("wallet created: " + getFilesDir() + "/" + Constants.WALLET_FILENAME);
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
			wallet.saveToFileStream(openFileOutput(Constants.WALLET_FILENAME, Constants.WALLET_MODE));
			System.out.println("wallet saved to: " + getFilesDir() + "/" + Constants.WALLET_FILENAME);
		}
		catch (IOException x)
		{
			throw new RuntimeException(x);
		}
	}
}
