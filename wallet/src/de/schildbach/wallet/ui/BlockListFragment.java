/*
 * Copyright 2013-2014 the original author or authors.
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

package de.schildbach.wallet.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class BlockListFragment extends SherlockListFragment
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Wallet wallet;
	private LoaderManager loaderManager;
	private SharedPreferences prefs;

	private BlockchainService service;

	private BlockListAdapter adapter;
	private Set<Transaction> transactions;

	private static final int ID_BLOCK_LOADER = 0;
	private static final int ID_TRANSACTION_LOADER = 1;

	private static final int MAX_BLOCKS = 32;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = this.activity.getWalletApplication();
		this.wallet = application.getWallet();
		this.loaderManager = getLoaderManager();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		activity.bindService(new Intent(activity, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		adapter = new BlockListAdapter();
		setListAdapter(adapter);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		activity.registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

		loaderManager.initLoader(ID_TRANSACTION_LOADER, null, transactionLoaderCallbacks);

		adapter.notifyDataSetChanged();
	}

	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_TRANSACTION_LOADER);

		activity.unregisterReceiver(tickReceiver);

		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		activity.unbindService(serviceConnection);

		super.onDestroy();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final StoredBlock storedBlock = adapter.getItem(position);

		activity.startActionMode(new ActionMode.Callback()
		{
			@Override
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.blocks_context, menu);

				return true;
			}

			@Override
			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				mode.setTitle(Integer.toString(storedBlock.getHeight()));
				mode.setSubtitle(storedBlock.getHeader().getHashAsString());

				return true;
			}

			@Override
			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.blocks_context_browse:
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.EXPLORE_BASE_URL + "block/"
								+ storedBlock.getHeader().getHashAsString())));

						mode.finish();
						return true;
				}

				return false;
			}

			@Override
			public void onDestroyActionMode(final ActionMode mode)
			{
			}
		});
	}

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((BlockchainServiceImpl.LocalBinder) binder).getService();

			loaderManager.initLoader(ID_BLOCK_LOADER, null, blockLoaderCallbacks);
		}

		@Override
		public void onServiceDisconnected(final ComponentName name)
		{
			loaderManager.destroyLoader(ID_BLOCK_LOADER);

			service = null;
		}
	};

	private final BroadcastReceiver tickReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			adapter.notifyDataSetChanged();
		}
	};

	private final class BlockListAdapter extends BaseAdapter
	{
		private static final int ROW_BASE_CHILD_COUNT = 2;
		private static final int ROW_INSERT_INDEX = 1;
		private final TransactionsListAdapter transactionsAdapter = new TransactionsListAdapter(activity, wallet, application.maxConnectedPeers(),
				false);

		private final List<StoredBlock> blocks = new ArrayList<StoredBlock>(MAX_BLOCKS);

		public void clear()
		{
			blocks.clear();

			adapter.notifyDataSetChanged();
		}

		public void replace(@Nonnull final Collection<StoredBlock> blocks)
		{
			this.blocks.clear();
			this.blocks.addAll(blocks);

			notifyDataSetChanged();
		}

		@Override
		public int getCount()
		{
			return blocks.size();
		}

		@Override
		public StoredBlock getItem(final int position)
		{
			return blocks.get(position);
		}

		@Override
		public long getItemId(final int position)
		{
			return WalletUtils.longHash(blocks.get(position).getHeader().getHash());
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent)
		{
			final ViewGroup row;
			if (convertView == null)
				row = (ViewGroup) getLayoutInflater(null).inflate(R.layout.block_row, null);
			else
				row = (ViewGroup) convertView;

			final StoredBlock storedBlock = getItem(position);
			final Block header = storedBlock.getHeader();

			final TextView rowHeight = (TextView) row.findViewById(R.id.block_list_row_height);
			final int height = storedBlock.getHeight();
			rowHeight.setText(Integer.toString(height));

			final TextView rowTime = (TextView) row.findViewById(R.id.block_list_row_time);
			final long timeMs = header.getTimeSeconds() * DateUtils.SECOND_IN_MILLIS;
			rowTime.setText(DateUtils.getRelativeDateTimeString(activity, timeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));

			final TextView rowHash = (TextView) row.findViewById(R.id.block_list_row_hash);
			rowHash.setText(WalletUtils.formatHash(null, header.getHashAsString(), 8, 0, ' '));

			final int transactionChildCount = row.getChildCount() - ROW_BASE_CHILD_COUNT;
			int iTransactionView = 0;

			if (transactions != null)
			{
				final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
				final int btcPrecision = precision.charAt(0) - '0';
				final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

				transactionsAdapter.setPrecision(btcPrecision, btcShift);

				for (final Transaction tx : transactions)
				{
					if (tx.getAppearsInHashes().containsKey(header.getHash()))
					{
						final View view;
						if (iTransactionView < transactionChildCount)
						{
							view = row.getChildAt(ROW_INSERT_INDEX + iTransactionView);
						}
						else
						{
							view = getLayoutInflater(null).inflate(R.layout.transaction_row_oneline, null);
							row.addView(view, ROW_INSERT_INDEX + iTransactionView);
						}

						transactionsAdapter.bindView(view, tx);

						iTransactionView++;
					}
				}
			}

			final int leftoverTransactionViews = transactionChildCount - iTransactionView;
			if (leftoverTransactionViews > 0)
				row.removeViews(ROW_INSERT_INDEX + iTransactionView, leftoverTransactionViews);

			return row;
		}
	}

	private static class BlockLoader extends AsyncTaskLoader<List<StoredBlock>>
	{
		private Context context;
		private BlockchainService service;

		private BlockLoader(final Context context, final BlockchainService service)
		{
			super(context);

			this.context = context.getApplicationContext();
			this.service = service;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			context.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
		}

		@Override
		protected void onStopLoading()
		{
			context.unregisterReceiver(broadcastReceiver);

			super.onStopLoading();
		}

		@Override
		public List<StoredBlock> loadInBackground()
		{
			return service.getRecentBlocks(MAX_BLOCKS);
		}

		private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(final Context context, final Intent intent)
			{
				forceLoad();
			}
		};
	}

	private final LoaderCallbacks<List<StoredBlock>> blockLoaderCallbacks = new LoaderCallbacks<List<StoredBlock>>()
	{
		@Override
		public Loader<List<StoredBlock>> onCreateLoader(final int id, final Bundle args)
		{
			return new BlockLoader(activity, service);
		}

		@Override
		public void onLoadFinished(final Loader<List<StoredBlock>> loader, final List<StoredBlock> blocks)
		{
			adapter.replace(blocks);

			final Loader<Set<Transaction>> transactionLoader = loaderManager.getLoader(ID_TRANSACTION_LOADER);
			if (transactionLoader != null && transactionLoader.isStarted())
				transactionLoader.forceLoad();
		}

		@Override
		public void onLoaderReset(final Loader<List<StoredBlock>> loader)
		{
			adapter.clear();
		}
	};

	private static class TransactionsLoader extends AsyncTaskLoader<Set<Transaction>>
	{
		private final Wallet wallet;

		private TransactionsLoader(final Context context, final Wallet wallet)
		{
			super(context);

			this.wallet = wallet;
		}

		@Override
		public Set<Transaction> loadInBackground()
		{
			final Set<Transaction> transactions = wallet.getTransactions(true);

			final Set<Transaction> filteredTransactions = new HashSet<Transaction>(transactions.size());
			for (final Transaction tx : transactions)
			{
				final Map<Sha256Hash, Integer> appearsIn = tx.getAppearsInHashes();
				if (appearsIn != null && !appearsIn.isEmpty()) // TODO filter by updateTime
					filteredTransactions.add(tx);
			}

			return filteredTransactions;
		}
	}

	private final LoaderCallbacks<Set<Transaction>> transactionLoaderCallbacks = new LoaderCallbacks<Set<Transaction>>()
	{
		@Override
		public Loader<Set<Transaction>> onCreateLoader(final int id, final Bundle args)
		{
			return new TransactionsLoader(activity, wallet);
		}

		@Override
		public void onLoadFinished(final Loader<Set<Transaction>> loader, final Set<Transaction> transactions)
		{
			BlockListFragment.this.transactions = transactions;

			adapter.notifyDataSetChanged();
		}

		@Override
		public void onLoaderReset(final Loader<Set<Transaction>> loader)
		{
			BlockListFragment.this.transactions.clear(); // be nice
			BlockListFragment.this.transactions = null;

			adapter.notifyDataSetChanged();
		}
	};
}
