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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;

import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockStore;
import com.google.bitcoin.core.BlockStoreException;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.Utils;

/**
 * @author Andreas Schildbach
 */
public class FilesBlockStore implements BlockStore
{
	private static final int FORMAT_VERSION = 1;

	private final File dir;

	public FilesBlockStore(final File dir)
	{
		this.dir = dir;
	}

	public void put(final StoredBlock block) throws BlockStoreException
	{
		try
		{
			final String filename = Utils.bytesToHexString(block.getHeader().getHash());

			final ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(new File(dir, filename)));
			os.writeInt(FORMAT_VERSION);
			os.writeObject(block.getHeader());
			os.writeObject(block.getChainWork());
			os.writeInt(block.getHeight());
			os.close();
		}
		catch (final IOException x)
		{
			throw new BlockStoreException(x);
		}
	}

	public StoredBlock get(final byte[] hash) throws BlockStoreException
	{
		try
		{
			final String filename = Utils.bytesToHexString(hash);
			final ObjectInputStream is = new ObjectInputStream(new FileInputStream(new File(dir, filename)));
			if (is.readInt() != FORMAT_VERSION)
				throw new IllegalStateException("invalid version");
			final Block header = (Block) is.readObject();
			final BigInteger chainWork = (BigInteger) is.readObject();
			final int height = is.readInt();
			is.close();
			final StoredBlock block = new StoredBlock(header, chainWork, height);

			return block;

		}
		catch (final FileNotFoundException x)
		{
			return null;
		}
		catch (final IOException x)
		{
			throw new BlockStoreException(x);
		}
		catch (final ClassNotFoundException x)
		{
			throw new BlockStoreException(x);
		}
	}
}
