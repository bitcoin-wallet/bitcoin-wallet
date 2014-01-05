/*
 * Copyright 2012-2014 the original author or authors.
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

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;

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
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.VersionMessage;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class PeerListFragment extends SherlockListFragment
{
	private AbstractWalletActivity activity;
	private LoaderManager loaderManager;

	private BlockchainService service;
	private ArrayAdapter<Peer> adapter;

	private final Handler handler = new Handler();

	private static final long REFRESH_MS = DateUtils.SECOND_IN_MILLIS;

	private static final int ID_PEER_LOADER = 0;
	private static final int ID_REVERSE_DNS_LOADER = 1;

	private final Map<InetAddress, String> hostnames = new WeakHashMap<InetAddress, String>();

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.loaderManager = getLoaderManager();
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
				final InetAddress address = peer.getAddress().getAddr();
				final String hostname = hostnames.get(address);
				rowIp.setText(hostname != null ? hostname : address.getHostAddress());

				final TextView rowHeight = (TextView) row.findViewById(R.id.peer_list_row_height);
				final long bestHeight = peer.getBestHeight();
				rowHeight.setText(bestHeight > 0 ? bestHeight + " blocks" : null);
				rowHeight.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

				final TextView rowVersion = (TextView) row.findViewById(R.id.peer_list_row_version);
				rowVersion.setText(versionMessage.subVer);
				rowVersion.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

				final TextView rowProtocol = (TextView) row.findViewById(R.id.peer_list_row_protocol);
				rowProtocol.setText("protocol: " + versionMessage.clientVersion);
				rowProtocol.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

				final TextView rowPing = (TextView) row.findViewById(R.id.peer_list_row_ping);
				final long pingTime = peer.getPingTime();
				rowPing.setText(pingTime < Long.MAX_VALUE ? getString(R.string.peer_list_row_ping_time, pingTime) : null);
				rowPing.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

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
			@Override
			public void run()
			{
				adapter.notifyDataSetChanged();

				final Loader<String> loader = loaderManager.getLoader(ID_REVERSE_DNS_LOADER);
				final boolean loaderRunning = loader != null && loader.isStarted();

				if (!loaderRunning)
				{
					for (int i = 0; i < adapter.getCount(); i++)
					{
						final Peer peer = adapter.getItem(i);
						final InetAddress address = peer.getAddress().getAddr();

						if (!hostnames.containsKey(address))
						{
							final Bundle args = new Bundle();
							args.putSerializable("address", address);
							loaderManager.initLoader(ID_REVERSE_DNS_LOADER, args, reverseDnsLoaderCallbacks).forceLoad();

							break;
						}
					}
				}

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

		loaderManager.destroyLoader(ID_REVERSE_DNS_LOADER);

		super.onDestroy();
	}

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((BlockchainServiceImpl.LocalBinder) binder).getService();

			loaderManager.initLoader(ID_PEER_LOADER, null, peerLoaderCallbacks);
		}

		@Override
		public void onServiceDisconnected(final ComponentName name)
		{
			loaderManager.destroyLoader(ID_PEER_LOADER);

			service = null;
		}
	};

	private static class PeerLoader extends AsyncTaskLoader<List<Peer>>
	{
		private Context context;
		private BlockchainService service;

		private PeerLoader(final Context context, @Nonnull final BlockchainService service)
		{
			super(context);

			this.context = context.getApplicationContext();
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

	private final LoaderCallbacks<List<Peer>> peerLoaderCallbacks = new LoaderCallbacks<List<Peer>>()
	{
		@Override
		public Loader<List<Peer>> onCreateLoader(final int id, final Bundle args)
		{
			return new PeerLoader(activity, service);
		}

		@Override
		public void onLoadFinished(final Loader<List<Peer>> loader, final List<Peer> peers)
		{
			adapter.clear();

			if (peers != null)
				for (final Peer peer : peers)
					adapter.add(peer);
		}

		@Override
		public void onLoaderReset(final Loader<List<Peer>> loader)
		{
			adapter.clear();
		}
	};

	private static class ReverseDnsLoader extends AsyncTaskLoader<String>
	{
		public final InetAddress address;

		public ReverseDnsLoader(final Context context, @Nonnull final InetAddress address)
		{
			super(context);

			this.address = address;
		}

		@Override
		public String loadInBackground()
		{
			return address.getCanonicalHostName();
		}
	}

	private final LoaderCallbacks<String> reverseDnsLoaderCallbacks = new LoaderCallbacks<String>()
	{
		@Override
		public Loader<String> onCreateLoader(final int id, final Bundle args)
		{
			final InetAddress address = (InetAddress) args.getSerializable("address");

			return new ReverseDnsLoader(activity, address);
		}

		@Override
		public void onLoadFinished(final Loader<String> loader, final String hostname)
		{
			final InetAddress address = ((ReverseDnsLoader) loader).address;
			hostnames.put(address, hostname);

			loaderManager.destroyLoader(ID_REVERSE_DNS_LOADER);
		}

		@Override
		public void onLoaderReset(final Loader<String> loader)
		{
		}
	};
}
