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

package de.schildbach.wallet.ui;

import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.bitcoin.core.Peer;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class PeerListFragment extends ListFragment implements LoaderCallbacks<List<Peer>>
{
	private Activity activity;
	private ArrayAdapter<Peer> adapter;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		getLoaderManager().initLoader(0, null, this);
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
					row = getLayoutInflater(null).inflate(android.R.layout.simple_list_item_2, null);

				final Peer peer = getItem(position);

				final TextView rowText1 = (TextView) row.findViewById(android.R.id.text1);
				rowText1.setText(peer.getAddress().toString());

				final TextView rowText2 = (TextView) row.findViewById(android.R.id.text2);
				rowText2.setText(peer.getVersionMessage().toString());

				return row;
			}
		};
		setListAdapter(adapter);
	}

	@Override
	public void onDestroy()
	{
		getLoaderManager().destroyLoader(0);

		super.onDestroy();
	}

	public Loader<List<Peer>> onCreateLoader(final int id, final Bundle args)
	{
		return new PeerLoader(activity);
	}

	public void onLoadFinished(final Loader<List<Peer>> loader, final List<Peer> peers)
	{
		adapter.clear();

		for (final Peer peer : peers)
			adapter.add(peer);
	}

	public void onLoaderReset(final Loader<List<Peer>> loader)
	{
		adapter.clear();
	}

	private static class PeerLoader extends AsyncTaskLoader<List<Peer>>
	{
		private Context context;
		private BlockchainService service;

		private PeerLoader(final Context context)
		{
			super(context);

			this.context = context;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			context.bindService(new Intent(context, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);

			context.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_PEER_STATE));

			forceLoad();
		}

		@Override
		protected void onStopLoading()
		{
			context.unregisterReceiver(broadcastReceiver);

			context.unbindService(serviceConnection);

			super.onStopLoading();
		}

		@Override
		public List<Peer> loadInBackground()
		{
			return service != null ? service.getConnectedPeers() : null;
		}

		private final ServiceConnection serviceConnection = new ServiceConnection()
		{
			public void onServiceConnected(final ComponentName name, final IBinder binder)
			{
				service = ((BlockchainServiceImpl.LocalBinder) binder).getService();

				forceLoad();
			}

			public void onServiceDisconnected(final ComponentName name)
			{
				service = null;

				forceLoad();
			}
		};

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
