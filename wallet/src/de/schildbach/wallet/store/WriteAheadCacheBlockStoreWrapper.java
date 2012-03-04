/*
 * Copyright 2012 the original author or authors.
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
package de.schildbach.wallet.store;

import java.util.HashMap;
import java.util.Map;

import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;

/**
 * @author Andreas Schildbach
 */
public class WriteAheadCacheBlockStoreWrapper implements BlockStore
{
	private BlockStore wrappedBlockStore;
	private static final int WRITE_AHEAD_CACHE_CAPACITY = 128;
	private Map<Sha256Hash, StoredBlock> writeAheadCache = new HashMap<Sha256Hash, StoredBlock>(WRITE_AHEAD_CACHE_CAPACITY);
	private StoredBlock chainHeadBlock;

	public WriteAheadCacheBlockStoreWrapper(final BlockStore wrappedBlockStore)
	{
		this.wrappedBlockStore = wrappedBlockStore;

		try
		{
			chainHeadBlock = wrappedBlockStore.getChainHead();
		}
		catch (final BlockStoreException x)
		{
			throw new RuntimeException(x);
		}
	}

	public synchronized void close() throws BlockStoreException
	{
		flushCache();
	}

	public synchronized void put(final StoredBlock block) throws BlockStoreException
	{
		writeAheadCache.put(block.getHeader().getHash(), block);

		if (writeAheadCache.size() >= WRITE_AHEAD_CACHE_CAPACITY)
			flushCache();
	}

	private void flushCache() throws BlockStoreException
	{
		// write blocks
		for (final StoredBlock block : writeAheadCache.values())
			wrappedBlockStore.put(block);

		// write chain head
		wrappedBlockStore.setChainHead(chainHeadBlock);

		// clear cache
		writeAheadCache.clear();
	}

	public synchronized StoredBlock get(final Sha256Hash hash) throws BlockStoreException
	{
		final StoredBlock storedBlock = writeAheadCache.get(hash);
		if (storedBlock != null)
			return storedBlock;

		return wrappedBlockStore.get(hash);
	}

	public synchronized void setChainHead(final StoredBlock chainHead) throws BlockStoreException
	{
		chainHeadBlock = chainHead;
	}

	public synchronized StoredBlock getChainHead() throws BlockStoreException
	{
		return chainHeadBlock;
	}
}
