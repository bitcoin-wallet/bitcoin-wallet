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

package de.schildbach.wallet.ui.monitor;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Peer;

import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.ui.DividerItemDecoration;

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
import android.widget.ViewAnimator;

/**
 * @author Andreas Schildbach
 */
public final class PeerListFragment extends Fragment {
    private Activity activity;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private PeerListAdapter adapter;

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
                viewGroup.setDisplayedChild((peers == null || peers.isEmpty()) ? 1 : 2);
                maybeSubmitList();
                if (peers != null)
                    for (final Peer peer : peers)
                        viewModel.getHostnames().reverseLookup(peer.getAddress().getAddr());
            }
        });
        viewModel.getHostnames().observe(this, new Observer<Map<InetAddress, String>>() {
            @Override
            public void onChanged(final Map<InetAddress, String> hostnames) {
                maybeSubmitList();
            }
        });

        adapter = new PeerListAdapter(activity);
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

    private void maybeSubmitList() {
        final List<Peer> peers = viewModel.getPeers().getValue();
        if (peers != null)
            adapter.submitList(PeerListAdapter.buildListItems(activity, peers, viewModel.getHostnames().getValue()));
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
