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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewAnimator;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.R;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import org.bitcoinj.core.Peer;

import java.util.List;

/**
 * @author Andreas Schildbach
 */
public final class PeerListFragment extends Fragment implements PeerListAdapter.OnClickListener {
    private AbstractWalletActivity activity;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private PeerListAdapter adapter;

    private NetworkMonitorViewModel activityViewModel;
    private PeerListViewModel viewModel;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityViewModel = new ViewModelProvider(activity).get(NetworkMonitorViewModel.class);
        activityViewModel.selectedItem.observe(this, item -> {
            if (item instanceof HostAndPort) {
                final HostAndPort peerHostAndPort = (HostAndPort) item;
                adapter.setSelectedPeer(peerHostAndPort);
                final int position = adapter.positionOf(peerHostAndPort);
                if (position != RecyclerView.NO_POSITION)
                    recyclerView.smoothScrollToPosition(position);
            } else {
                adapter.setSelectedPeer(null);
            }
        });
        viewModel = new ViewModelProvider(this).get(PeerListViewModel.class);
        viewModel.peers.observe(this, peers -> {
            viewGroup.setDisplayedChild((peers == null || peers.isEmpty()) ? 1 : 2);
            maybeSubmitList();
            if (peers != null)
                for (final Peer peer : peers)
                    viewModel.getHostnames().reverseLookup(peer.getAddress().getAddr());
        });
        viewModel.getHostnames().observe(this, hostnames -> maybeSubmitList());

        adapter = new PeerListAdapter(activity, this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.peer_list_fragment, container, false);
        viewGroup = view.findViewById(R.id.peer_list_group);
        recyclerView = view.findViewById(R.id.peer_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        return view;
    }

    private void maybeSubmitList() {
        final List<Peer> peers = viewModel.peers.getValue();
        if (peers != null)
            adapter.submitList(PeerListAdapter.buildListItems(activity, peers, viewModel.getHostnames().getValue()));
    }

    @Override
    public void onPeerClick(final View view, final HostAndPort peerpeerHostAndPort) {
        activityViewModel.selectedItem.setValue(peerpeerHostAndPort);
    }
}
