/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.monitor;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Peer;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * @author Andreas Schildbach
 */
public class PeerListViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private PeersLiveData peers;
    private HostnamesLiveData hostnames;

    public PeerListViewModel(final Application application) {
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

    public static class PeersLiveData extends LiveData<List<Peer>> implements ServiceConnection {
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

    public static class HostnamesLiveData extends LiveData<Map<InetAddress, String>> {
        private final Handler handler = new Handler();

        public HostnamesLiveData(final WalletApplication application) {
            setValue(new HashMap<InetAddress, String>());
        }

        public void reverseLookup(final InetAddress address) {
            final Map<InetAddress, String> hostnames = getValue();
            if (!hostnames.containsKey(address)) {
                AsyncTask.execute(() -> {
                    final String hostname = address.getCanonicalHostName();
                    handler.post(() -> {
                        hostnames.put(address, hostname);
                        setValue(hostnames);
                    });
                });
            }
        }
    }
}
