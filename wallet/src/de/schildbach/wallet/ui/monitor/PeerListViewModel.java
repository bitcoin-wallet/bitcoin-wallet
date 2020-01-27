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

import android.app.Application;
import android.os.AsyncTask;
import android.os.Handler;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.BlockchainServiceLiveData;
import de.schildbach.wallet.service.BlockchainService;
import org.bitcoinj.core.Peer;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Andreas Schildbach
 */
public class PeerListViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private final BlockchainServiceLiveData blockchainService;
    public final MediatorLiveData<List<Peer>> peers;
    private HostnamesLiveData hostnames;

    public PeerListViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.blockchainService = new BlockchainServiceLiveData(application);
        this.peers = new MediatorLiveData<>();
        this.peers.addSource(blockchainService, blockchainService -> maybeRefreshPeers());
        this.peers.addSource(this.application.peerState, numPeers -> maybeRefreshPeers());
    }

    private void maybeRefreshPeers() {
        final BlockchainService blockchainService = this.blockchainService.getValue();
        if (blockchainService != null)
            this.peers.setValue(blockchainService.getConnectedPeers());
    }

    public HostnamesLiveData getHostnames() {
        if (hostnames == null)
            hostnames = new HostnamesLiveData(application);
        return hostnames;
    }

    public static class HostnamesLiveData extends LiveData<Map<InetAddress, String>> {
        private final Handler handler = new Handler();

        public HostnamesLiveData(final WalletApplication application) {
            setValue(new HashMap<>());
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
