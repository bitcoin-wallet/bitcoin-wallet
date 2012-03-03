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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.Fragment;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ViewAnimator;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class BlockListFragment extends Fragment implements BlockListAdapter.OnClickListener
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Configuration config;
	private Wallet wallet;
	private LoaderManager loaderManager;

	private BlockchainService service;

	private ViewAnimator viewGroup;
	private RecyclerView recyclerView;
	private BlockListAdapter adapter;

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

		adapter = new BlockListAdapter(activity, wallet, this);
		adapter.setFormat(config.getFormat());
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.block_list_fragment, container, false);

		viewGroup = (ViewAnimator) view.findViewById(R.id.block_list_group);

		recyclerView = (RecyclerView) view.findViewById(R.id.block_list);
		recyclerView.setLayoutManager(new LinearLayoutManager(activity));
		recyclerView.setAdapter(adapter);

		return view;
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
	public void onBlockMenuClick(final View view, final StoredBlock block)
	{
		final PopupMenu popupMenu = new PopupMenu(activity, view);
		popupMenu.inflate(R.menu.blocks_context);

		popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener()
		{
			@Override
			public boolean onMenuItemClick(final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.blocks_context_browse:
						startActivity(new Intent(Intent.ACTION_VIEW,
								Uri.withAppendedPath(config.getBlockExplorer(), "block/" + block.getHeader().getHashAsString())));
						return true;
				}
				return false;
			}
		});
		popupMenu.show();
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
			viewGroup.setDisplayedChild(1);

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
			adapter.replaceTransactions(transactions);
		}

		@Override
		public void onLoaderReset(final Loader<Set<Transaction>> loader)
		{
			adapter.clearTransactions();
		}
	};
}
