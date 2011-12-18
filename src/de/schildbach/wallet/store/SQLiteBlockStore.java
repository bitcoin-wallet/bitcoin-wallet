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

package de.schildbach.wallet.store;

import java.math.BigInteger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;

import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;

/**
 * @author Andreas Schildbach
 */
public class SQLiteBlockStore implements BlockStore
{
	private static final int DATABASE_VERSION = 1;

	private static final String TABLE_BLOCKS = "blocks";
	private static final String COLUMN_BLOCKS_HEADER = "header";
	private static final String COLUMN_BLOCKS_HEIGHT = "height";
	private static final String COLUMN_BLOCKS_CHAINWORK = "chainWork";
	private static final String COLUMN_BLOCKS_HASH = "hash";

	private static final String TABLE_SETTINGS = "settings";
	private static final String COLUMN_SETTINGS_VALUE = "value";
	private static final String COLUMN_SETTINGS_NAME = "name";

	private static final String SETTING_CHAINHEAD = "chainhead";

	private static final String CREATE_BLOCKS_TABLE = "CREATE TABLE " + TABLE_BLOCKS + " ( " //
			+ COLUMN_BLOCKS_HASH + " BLOB NOT NULL CONSTRAINT " + TABLE_BLOCKS + "_pk PRIMARY KEY," //
			+ COLUMN_BLOCKS_CHAINWORK + " BLOB NOT NULL," //
			+ COLUMN_BLOCKS_HEIGHT + " INTEGER NOT NULL," //
			+ COLUMN_BLOCKS_HEADER + " BLOB NOT NULL" //
			+ ")";
	private static final String CREATE_SETTINGS_TABLE = "CREATE TABLE " + TABLE_SETTINGS + " ( " //
			+ COLUMN_SETTINGS_NAME + " TEXT NOT NULL CONSTRAINT " + TABLE_SETTINGS + "_pk PRIMARY KEY," //
			+ COLUMN_SETTINGS_VALUE + " BLOB" //
			+ ")";

	private final class Helper extends SQLiteOpenHelper
	{
		public Helper(final Context context, final String databaseName)
		{
			super(context, databaseName, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(final SQLiteDatabase db)
		{
			db.execSQL(CREATE_BLOCKS_TABLE);
			db.execSQL(CREATE_SETTINGS_TABLE);
		}

		@Override
		public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion)
		{
			throw new UnsupportedOperationException();
		}
	}

	private final SQLiteOpenHelper helper;
	private final NetworkParameters networkParameters;
	private StoredBlock chainHeadBlock;
	private Sha256Hash chainHeadHash;

	public SQLiteBlockStore(final Context context, final NetworkParameters networkParameters, final String databaseName) throws BlockStoreException
	{
		this.networkParameters = networkParameters;

		helper = new Helper(context, databaseName);

		final SQLiteDatabase db = helper.getReadableDatabase();
		final Cursor query = db.query(TABLE_SETTINGS, new String[] { COLUMN_SETTINGS_VALUE }, COLUMN_SETTINGS_NAME + "=?",
				new String[] { SETTING_CHAINHEAD }, null, null, null);
		if (query.moveToFirst())
		{
			final Sha256Hash hash = new Sha256Hash(query.getBlob(query.getColumnIndexOrThrow(COLUMN_SETTINGS_VALUE)));
			chainHeadBlock = get(hash);
			if (chainHeadBlock == null)
				throw new BlockStoreException("could not find block for chain head");
			chainHeadHash = hash;
		}
		else
		{
			try
			{
				// set up the genesis block
				final Block genesis = networkParameters.genesisBlock.cloneAsHeader();
				final StoredBlock storedGenesis = new StoredBlock(genesis, genesis.getWork(), 0);

				put(storedGenesis);
				setChainHead(storedGenesis);
			}
			catch (final VerificationException x)
			{
				throw new BlockStoreException(x);
			}
		}
	}

	public void close()
	{
		// nothing to do for now
	}

	public void put(final StoredBlock block) throws BlockStoreException
	{
		final ContentValues values = new ContentValues();
		values.put(COLUMN_BLOCKS_HASH, block.getHeader().getHash().getBytes());
		values.put(COLUMN_BLOCKS_CHAINWORK, block.getChainWork().toByteArray());
		values.put(COLUMN_BLOCKS_HEIGHT, block.getHeight());
		values.put(COLUMN_BLOCKS_HEADER, block.getHeader().unsafeBitcoinSerialize());

		final SQLiteDatabase db = helper.getWritableDatabase();

		try
		{
			db.beginTransaction();
			db.replaceOrThrow(TABLE_BLOCKS, null, values);
			db.setTransactionSuccessful();
		}
		catch (final SQLException x)
		{
			throw new BlockStoreException(x);
		}
		finally
		{
			db.endTransaction();
		}

	}

	public StoredBlock get(final Sha256Hash hash) throws BlockStoreException
	{
		// optimize for chain head
		if (chainHeadHash != null && chainHeadHash.equals(hash))
			return chainHeadBlock;

		// ugly workaround for binding a blob value
		final CursorFactory cursorFactory = new CursorFactory()
		{
			public Cursor newCursor(final SQLiteDatabase db, final SQLiteCursorDriver masterQuery, final String editTable, final SQLiteQuery query)
			{
				query.bindBlob(1, hash.getBytes());
				return new SQLiteCursor(db, masterQuery, editTable, query);
			}
		};
		final Cursor query = helper.getReadableDatabase().queryWithFactory(cursorFactory, false, TABLE_BLOCKS,
				new String[] { COLUMN_BLOCKS_CHAINWORK, COLUMN_BLOCKS_HEIGHT, COLUMN_BLOCKS_HEADER }, COLUMN_BLOCKS_HASH + "=?", null, null, null,
				null, null);

		if (query.moveToFirst())
		{
			try
			{
				final BigInteger chainWork = new BigInteger(query.getBlob(query.getColumnIndexOrThrow(COLUMN_BLOCKS_CHAINWORK)));
				final int height = query.getInt(query.getColumnIndexOrThrow(COLUMN_BLOCKS_HEIGHT));
				final Block block = new Block(networkParameters, query.getBlob(query.getColumnIndexOrThrow(COLUMN_BLOCKS_HEADER)));

				block.verifyHeader();

				return new StoredBlock(block, chainWork, height);
			}
			catch (final ProtocolException x)
			{
				// corrupted database
				throw new BlockStoreException(x);
			}
			catch (final VerificationException x)
			{
				// should not be able to happen unless the database contains bad blocks
				throw new BlockStoreException(x);
			}
		}
		else
		{
			return null;
		}
	}

	public void setChainHead(final StoredBlock chainHead) throws BlockStoreException
	{
		final Sha256Hash hash = chainHead.getHeader().getHash();
		chainHeadHash = hash;
		chainHeadBlock = chainHead;

		final ContentValues values = new ContentValues();
		values.put(COLUMN_SETTINGS_NAME, SETTING_CHAINHEAD);
		values.put(COLUMN_SETTINGS_VALUE, hash.getBytes());

		final SQLiteDatabase db = helper.getWritableDatabase();

		try
		{
			db.beginTransaction();
			db.replaceOrThrow(TABLE_SETTINGS, null, values);
			db.setTransactionSuccessful();
		}
		catch (final SQLException x)
		{
			throw new BlockStoreException(x);
		}
		finally
		{
			db.endTransaction();
		}
	}

	public StoredBlock getChainHead() throws BlockStoreException
	{
		return chainHeadBlock;
	}
}
