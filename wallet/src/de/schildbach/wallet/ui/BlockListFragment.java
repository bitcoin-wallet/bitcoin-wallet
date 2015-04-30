/*
 * Copyright 2013-2015 the original author or authors.
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
import java.util.concurrent.RejectedExecutionException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class BlockListFragment extends ListFragment
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Configuration config;
	private Wallet wallet;
	private LoaderManager loaderManager;

	private BlockchainService service;

	private BlockListAdapter adapter;
	private Set<Transaction> transactions;

	private static final int ID_BLOCK_LOADER = 0;
	private static final int ID_TRANSACTION_LOADER = 1;

	private static final int MAX_BLOCKS = 32;

	private static final Logger log = LoggerFactory.getLogger(BlockListFragment.class);

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = this.activity.getWalletApplication();
		this.config = application.getConfiguration();
		this.wallet = application.getWallet();
		this.loaderManager = getLoaderManager();
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

	private boolean resumed = false;

	@Override
	public void onResume()
	{
		super.onResume();

		activity.registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
		loaderManager.initLoader(ID_TRANSACTION_LOADER, null, transactionLoaderCallbacks);

		adapter.notifyDataSetChanged();

		resumed = true;
	}

	@Override
	public void onPause()
	{
		// workaround: under high load, it can happen that onPause() is called twice (recursively via destroyLoader)
		if (resumed)
		{
			resumed = false;

			loaderManager.destroyLoader(ID_TRANSACTION_LOADER);
			activity.unregisterReceiver(tickReceiver);
		}
		else
		{
			log.warn("onPause() called without onResume(), appending stack trace", new RuntimeException());
		}

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

		private final List<StoredBlock> blocks = new ArrayList<StoredBlock>(MAX_BLOCKS);

		private final LayoutInflater inflater = LayoutInflater.from(activity);
		private final String textCoinBase = activity.getString(R.string.wallet_transactions_fragment_coinbase);
		private final String textInternal = activity.getString(R.string.wallet_transactions_fragment_internal);
		private final MonetaryFormat format = config.getFormat().noCode();

		public void clear()
		{
			blocks.clear();

			adapter.notifyDataSetChanged();
		}

		public void replace(final Collection<StoredBlock> blocks)
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
				row = (ViewGroup) inflater.inflate(R.layout.block_row, null);
			else
				row = (ViewGroup) convertView;

			final StoredBlock storedBlock = getItem(position);
			final Block header = storedBlock.getHeader();

			final TextView rowHeight = (TextView) row.findViewById(R.id.block_list_row_height);
			final int height = storedBlock.getHeight();
			rowHeight.setText(Integer.toString(height));

			final TextView rowTime = (TextView) row.findViewById(R.id.block_list_row_time);
			final long timeMs = header.getTimeSeconds() * DateUtils.SECOND_IN_MILLIS;
			if (timeMs < System.currentTimeMillis())
				rowTime.setText(DateUtils.getRelativeDateTimeString(activity, timeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
			else
				rowTime.setText(R.string.block_row_now);

			final TextView rowHash = (TextView) row.findViewById(R.id.block_list_row_hash);
			rowHash.setText(WalletUtils.formatHash(null, header.getHashAsString(), 8, 0, ' '));

			final int transactionChildCount = row.getChildCount() - ROW_BASE_CHILD_COUNT;
			int iTransactionView = 0;

			if (transactions != null)
			{
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
							view = inflater.inflate(R.layout.block_row_transaction, null);
							row.addView(view, ROW_INSERT_INDEX + iTransactionView);
						}

						bindView(view, tx);

						iTransactionView++;
					}
				}
			}

			final int leftoverTransactionViews = transactionChildCount - iTransactionView;
			if (leftoverTransactionViews > 0)
				row.removeViews(ROW_INSERT_INDEX + iTransactionView, leftoverTransactionViews);

			return row;
		}

		public void bindView(final View row, final Transaction tx)
		{
			final boolean isCoinBase = tx.isCoinBase();
			final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;

			final Coin value = tx.getValue(wallet);
			final boolean sent = value.signum() < 0;
			final Address address;
			if (sent)
				address = WalletUtils.getToAddressOfSent(tx, wallet);
			else
				address = WalletUtils.getWalletAddressOfReceived(tx, wallet);

			// receiving or sending
			final TextView rowFromTo = (TextView) row.findViewById(R.id.block_row_transaction_fromto);
			if (isInternal)
				rowFromTo.setText(R.string.symbol_internal);
			else if (sent)
				rowFromTo.setText(R.string.symbol_to);
			else
				rowFromTo.setText(R.string.symbol_from);

			// address
			final TextView rowAddress = (TextView) row.findViewById(R.id.block_row_transaction_address);
			final String label;
			if (isCoinBase)
				label = textCoinBase;
			else if (isInternal)
				label = textInternal;
			else if (address != null)
				label = AddressBookProvider.resolveLabel(activity, address.toString());
			else
				label = "?";
			rowAddress.setText(label != null ? label : address.toString());
			rowAddress.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

			// value
			final CurrencyTextView rowValue = (CurrencyTextView) row.findViewById(R.id.block_row_transaction_value);
			rowValue.setAlwaysSigned(true);
			rowValue.setFormat(format);
			rowValue.setAmount(value);
		}
	}

	private static class BlockLoader extends AsyncTaskLoader<List<StoredBlock>>
	{
		private LocalBroadcastManager broadcastManager;
		private BlockchainService service;

		private BlockLoader(final Context context, final BlockchainService service)
		{
			super(context);

			this.broadcastManager = LocalBroadcastManager.getInstance(context.getApplicationContext());
			this.service = service;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			broadcastManager.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));

			forceLoad();
		}

		@Override
		protected void onStopLoading()
		{
			broadcastManager.unregisterReceiver(broadcastReceiver);

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
				try
				{
					forceLoad();
				}
				catch (final RejectedExecutionException x)
				{
					log.info("rejected execution: " + BlockLoader.this.toString());
				}
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
