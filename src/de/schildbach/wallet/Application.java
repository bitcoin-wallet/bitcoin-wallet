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

import android.content.Context;
import android.os.Debug;

import com.google.bitcoin.core.BlockStore;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

/**
 * @author Andreas Schildbach
 */
public class Application extends android.app.Application
{
	private Wallet wallet;
	private BlockStore blockStore;

	@Override
	public void onCreate()
	{
		super.onCreate();

		System.out.println("Heap size: " + (Debug.getNativeHeapSize() / 1024) + " kB");

		loadWallet();

		blockStore = new FilesBlockStore(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE));
	}

	public Wallet getWallet()
	{
		return wallet;
	}

	public BlockStore getBlockStore()
	{
		return blockStore;
	}

	private void loadWallet()
	{
		final File file = walletFile();

		try
		{
			wallet = Wallet.loadFromFile(file);
			System.out.println("wallet loaded from: " + file);
		}
		catch (final FileNotFoundException x)
		{
			wallet = new Wallet(Constants.NETWORK_PARAMS);
			wallet.keychain.add(new ECKey());

			try
			{
				wallet.saveToFile(file);
				System.out.println("wallet created: " + file);
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
			final File file = walletFile();
			wallet.saveToFile(file);
			System.out.println("wallet saved to: " + file);
		}
		catch (IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	private File walletFile()
	{
		return new File(Constants.TEST ? getDir("testnet", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE) : getFilesDir(),
				Constants.WALLET_FILENAME);
	}
}
