/*
 * Copyright 2012-2013 the original author or authors.
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

import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.VersionMessage;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class PeerListFragment extends SherlockListFragment implements LoaderCallbacks<List<Peer>>
{
	private AbstractWalletActivity activity;
	private BlockchainService service;
	private ArrayAdapter<Peer> adapter;

	private final Handler handler = new Handler();

	private static final long REFRESH_MS = 1000;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		activity.bindService(new Intent(activity, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		setEmptyText(getString(R.string.peer_list_fragment_empty));
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		adapter = new ArrayAdapter<Peer>(activity, 0)
		{
			@Override
			public View getView(final int position, View row, final ViewGroup parent)
			{
				if (row == null)
					row = getLayoutInflater(null).inflate(R.layout.peer_list_row, null);

				final Peer peer = getItem(position);
				final VersionMessage versionMessage = peer.getPeerVersionMessage();
				final boolean isDownloading = peer.getDownloadData();

				final TextView rowIp = (TextView) row.findViewById(R.id.peer_list_row_ip);
				rowIp.setText(peer.getAddress().getAddr().getHostAddress());

				final TextView rowHeight = (TextView) row.findViewById(R.id.peer_list_row_height);
				final long bestHeight = versionMessage.bestHeight;
				rowHeight.setText(bestHeight > 0 ? bestHeight + " blocks" : null);
				rowHeight.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

				final TextView rowVersion = (TextView) row.findViewById(R.id.peer_list_row_version);
				rowVersion.setText(versionMessage.subVer);
				rowVersion.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

				return row;
			}

			@Override
			public boolean isEnabled(final int position)
			{
				return false;
			}
		};
		setListAdapter(adapter);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		handler.postDelayed(new Runnable()
		{
			public void run()
			{
				adapter.notifyDataSetChanged();

				handler.postDelayed(this, REFRESH_MS);
			}
		}, REFRESH_MS);
	}

	@Override
	public void onPause()
	{
		handler.removeCallbacksAndMessages(null);

		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		activity.unbindService(serviceConnection);

		super.onDestroy();
	}

	public Loader<List<Peer>> onCreateLoader(final int id, final Bundle args)
	{
		return new PeerLoader(activity, service);
	}

	public void onLoadFinished(final Loader<List<Peer>> loader, final List<Peer> peers)
	{
		adapter.clear();

		if (peers != null)
			for (final Peer peer : peers)
				adapter.add(peer);
	}

	public void onLoaderReset(final Loader<List<Peer>> loader)
	{
		adapter.clear();
	}

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((BlockchainServiceImpl.LocalBinder) binder).getService();

			getLoaderManager().initLoader(0, null, PeerListFragment.this);
		}

		public void onServiceDisconnected(final ComponentName name)
		{
			getLoaderManager().destroyLoader(0);

			service = null;
		}
	};

	private static class PeerLoader extends AsyncTaskLoader<List<Peer>>
	{
		private Context context;
		private BlockchainService service;

		private PeerLoader(final Context context, final BlockchainService service)
		{
			super(context);

			this.context = context;
			this.service = service;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			context.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_PEER_STATE));
		}

		@Override
		protected void onStopLoading()
		{
			context.unregisterReceiver(broadcastReceiver);

			super.onStopLoading();
		}

		@Override
		public List<Peer> loadInBackground()
		{
			return service.getConnectedPeers();
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
}
