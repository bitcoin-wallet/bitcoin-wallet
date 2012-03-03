/*
 * Copyright 2012-2015 the original author or authors.
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;

import org.bitcoinj.core.Peer;
import org.bitcoinj.core.VersionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.R;

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
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ViewAnimator;

/**
 * @author Andreas Schildbach
 */
public final class PeerListFragment extends Fragment {
    private AbstractWalletActivity activity;
    private LoaderManager loaderManager;

    private BlockchainService service;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private PeerViewAdapter adapter;

    private final Handler handler = new Handler();

    private static final long REFRESH_MS = DateUtils.SECOND_IN_MILLIS;

    private static final int ID_PEER_LOADER = 0;
    private static final int ID_REVERSE_DNS_LOADER = 1;

    private final Map<InetAddress, String> hostnames = new WeakHashMap<InetAddress, String>();

    private static final Logger log = LoggerFactory.getLogger(PeerListFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractWalletActivity) activity;
        this.loaderManager = getLoaderManager();
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        activity.bindService(new Intent(activity, BlockchainServiceImpl.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adapter = new PeerViewAdapter();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.peer_list_fragment, container, false);

        viewGroup = (ViewAnimator) view.findViewById(R.id.peer_list_group);

        recyclerView = (RecyclerView) view.findViewById(R.id.peer_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();

                final Loader<String> loader = loaderManager.getLoader(ID_REVERSE_DNS_LOADER);
                final boolean loaderRunning = loader != null && loader.isStarted();

                if (!loaderRunning) {
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        final Peer peer = adapter.getItem(i);
                        final InetAddress address = peer.getAddress().getAddr();

                        if (!hostnames.containsKey(address)) {
                            final Bundle args = new Bundle();
                            args.putSerializable("address", address);
                            loaderManager.initLoader(ID_REVERSE_DNS_LOADER, args, reverseDnsLoaderCallbacks)
                                    .forceLoad();

                            break;
                        }
                    }
                }

                handler.postDelayed(this, REFRESH_MS);
            }
        }, REFRESH_MS);
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);

        super.onPause();
    }

    @Override
    public void onDestroy() {
        activity.unbindService(serviceConnection);

        loaderManager.destroyLoader(ID_REVERSE_DNS_LOADER);

        super.onDestroy();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = ((BlockchainServiceImpl.LocalBinder) binder).getService();

            loaderManager.initLoader(ID_PEER_LOADER, null, peerLoaderCallbacks);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            loaderManager.destroyLoader(ID_PEER_LOADER);

            service = null;
        }
    };

    private class PeerViewAdapter extends RecyclerView.Adapter<PeerViewHolder> {
        private final LayoutInflater inflater = LayoutInflater.from(activity);
        private final List<Peer> peers = new LinkedList<Peer>();

        public PeerViewAdapter() {
            setHasStableIds(true);
        }

        public void clear() {
            peers.clear();

            notifyDataSetChanged();
        }

        public void replace(final List<Peer> peers) {
            this.peers.clear();
            this.peers.addAll(peers);

            notifyDataSetChanged();
        }

        public Peer getItem(final int position) {
            return peers.get(position);
        }

        @Override
        public int getItemCount() {
            return peers.size();
        }

        @Override
        public long getItemId(final int position) {
            return peers.get(position).getAddress().hashCode();
        }

        @Override
        public PeerViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            return new PeerViewHolder(inflater.inflate(R.layout.peer_list_row, parent, false));
        }

        @Override
        public void onBindViewHolder(final PeerViewHolder holder, final int position) {
            final Peer peer = getItem(position);
            final VersionMessage versionMessage = peer.getPeerVersionMessage();
            final boolean isDownloading = peer.isDownloadData();

            final InetAddress address = peer.getAddress().getAddr();
            final String hostname = hostnames.get(address);
            holder.ipView.setText(hostname != null ? hostname : address.getHostAddress());

            final long bestHeight = peer.getBestHeight();
            holder.heightView.setText(bestHeight > 0 ? bestHeight + " blocks" : null);
            holder.heightView.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

            holder.versionView.setText(versionMessage.subVer);
            holder.versionView.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

            holder.protocolView.setText("protocol: " + versionMessage.clientVersion);
            holder.protocolView.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

            final long pingTime = peer.getPingTime();
            holder.pingView
                    .setText(pingTime < Long.MAX_VALUE ? getString(R.string.peer_list_row_ping_time, pingTime) : null);
            holder.pingView.setTypeface(isDownloading ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private static class PeerViewHolder extends RecyclerView.ViewHolder {
        private final TextView ipView;
        private final TextView heightView;
        private final TextView versionView;
        private final TextView protocolView;
        private final TextView pingView;

        private PeerViewHolder(final View itemView) {
            super(itemView);

            ipView = (TextView) itemView.findViewById(R.id.peer_list_row_ip);
            heightView = (TextView) itemView.findViewById(R.id.peer_list_row_height);
            versionView = (TextView) itemView.findViewById(R.id.peer_list_row_version);
            protocolView = (TextView) itemView.findViewById(R.id.peer_list_row_protocol);
            pingView = (TextView) itemView.findViewById(R.id.peer_list_row_ping);
        }
    }

    private static class PeerLoader extends AsyncTaskLoader<List<Peer>> {
        private LocalBroadcastManager broadcastManager;
        private BlockchainService service;

        private PeerLoader(final Context context, final BlockchainService service) {
            super(context);

            this.broadcastManager = LocalBroadcastManager.getInstance(context.getApplicationContext());
            this.service = service;
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();

            broadcastManager.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_PEER_STATE));

            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            broadcastManager.unregisterReceiver(broadcastReceiver);

            super.onStopLoading();
        }

        @Override
        public List<Peer> loadInBackground() {
            return service.getConnectedPeers();
        }

        private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                try {
                    forceLoad();
                } catch (final RejectedExecutionException x) {
                    log.info("rejected execution: " + PeerLoader.this.toString());
                }
            }
        };
    }

    private final LoaderCallbacks<List<Peer>> peerLoaderCallbacks = new LoaderCallbacks<List<Peer>>() {
        @Override
        public Loader<List<Peer>> onCreateLoader(final int id, final Bundle args) {
            return new PeerLoader(activity, service);
        }

        @Override
        public void onLoadFinished(final Loader<List<Peer>> loader, final List<Peer> peers) {
            if (peers == null || peers.isEmpty()) {
                viewGroup.setDisplayedChild(1);
                adapter.clear();
            } else {
                viewGroup.setDisplayedChild(2);
                adapter.replace(peers);
            }
        }

        @Override
        public void onLoaderReset(final Loader<List<Peer>> loader) {
            adapter.clear();
        }
    };

    private static class ReverseDnsLoader extends AsyncTaskLoader<String> {
        public final InetAddress address;

        public ReverseDnsLoader(final Context context, final InetAddress address) {
            super(context);

            this.address = address;
        }

        @Override
        public String loadInBackground() {
            return address.getCanonicalHostName();
        }
    }

    private final LoaderCallbacks<String> reverseDnsLoaderCallbacks = new LoaderCallbacks<String>() {
        @Override
        public Loader<String> onCreateLoader(final int id, final Bundle args) {
            final InetAddress address = (InetAddress) args.getSerializable("address");

            return new ReverseDnsLoader(activity, address);
        }

        @Override
        public void onLoadFinished(final Loader<String> loader, final String hostname) {
            final InetAddress address = ((ReverseDnsLoader) loader).address;
            hostnames.put(address, hostname);

            loaderManager.destroyLoader(ID_REVERSE_DNS_LOADER);
        }

        @Override
        public void onLoaderReset(final Loader<String> loader) {
        }
    };
}
