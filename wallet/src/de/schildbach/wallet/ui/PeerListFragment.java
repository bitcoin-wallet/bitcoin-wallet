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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Peer;
import org.bitcoinj.core.VersionMessage;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ViewAnimator;

/**
 * @author Andreas Schildbach
 */
public final class PeerListFragment extends Fragment {
    private Activity activity;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private PeerViewAdapter adapter;

    private ViewModel viewModel;

    public static class ViewModel extends AndroidViewModel {
        private final WalletApplication application;
        private PeersLiveData peers;
        private HostnamesLiveData hostnames;

        public ViewModel(final Application application) {
            super(application);
            this.application = (WalletApplication) application;
        }

        public PeersLiveData getPeers() {
            if (peers == null)
                peers = new PeersLiveData(application);
            return peers;
        }

        public HostnamesLiveData getHostnames() {
            if (hostnames == null)
                hostnames = new HostnamesLiveData(application);
            return hostnames;
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (Activity) context;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        viewModel.getPeers().observe(this, new Observer<List<Peer>>() {
            @Override
            public void onChanged(final List<Peer> peers) {
                if (peers == null || peers.isEmpty()) {
                    viewGroup.setDisplayedChild(1);
                    adapter.clear();
                } else {
                    viewGroup.setDisplayedChild(2);
                    adapter.replace(peers);
                    for (final Peer peer : peers)
                        viewModel.getHostnames().reverseLookup(peer.getAddress().getAddr());
                }
            }
        });
        viewModel.getHostnames().observe(this, new Observer<Map<InetAddress, String>>() {
            @Override
            public void onChanged(final Map<InetAddress, String> hostnames) {
                adapter.notifyItemsChanged();
            }
        });

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

        public void notifyItemsChanged() {
            notifyItemRangeChanged(0, getItemCount());
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
            final Map<InetAddress, String> hostnames = viewModel.getHostnames().getValue();

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

    private static class PeersLiveData extends LiveData<List<Peer>> implements ServiceConnection {
        private final WalletApplication application;
        private LocalBroadcastManager broadcastManager;
        private BlockchainService blockchainService;

        private PeersLiveData(final WalletApplication application) {
            this.application = application;
            this.broadcastManager = LocalBroadcastManager.getInstance(application);
        }

        @Override
        protected void onActive() {
            broadcastManager.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_PEER_STATE));
            application.bindService(new Intent(application, BlockchainService.class), this, Context.BIND_AUTO_CREATE);
        }

        @Override
        protected void onInactive() {
            application.unbindService(this);
            broadcastManager.unregisterReceiver(broadcastReceiver);
        }

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            blockchainService = ((BlockchainService.LocalBinder) service).getService();
            setValue(blockchainService.getConnectedPeers());
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            blockchainService = null;
        }

        private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                if (blockchainService != null)
                    setValue(blockchainService.getConnectedPeers());
            }
        };
    }

    private static class HostnamesLiveData extends LiveData<Map<InetAddress, String>> {
        private final Handler handler = new Handler();

        public HostnamesLiveData(final WalletApplication application) {
            setValue(new HashMap<InetAddress, String>());
        }

        public void reverseLookup(final InetAddress address) {
            final Map<InetAddress, String> hostnames = getValue();
            if (!hostnames.containsKey(address)) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        final String hostname = address.getCanonicalHostName();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                hostnames.put(address, hostname);
                                setValue(hostnames);
                            }
                        });
                    }
                });
            }
        }
    }
}
