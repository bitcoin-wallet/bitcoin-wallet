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
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.VersionMessage;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @author Andreas Schildbach
 */
public class PeerListAdapter extends ListAdapter<PeerListAdapter.ListItem, PeerListAdapter.ViewHolder> {
    public static List<ListItem> buildListItems(final Context context, final List<Peer> peers,
            final Map<InetAddress, String> hostnames) {
        final List<ListItem> items = new ArrayList<>(peers.size());
        for (final Peer peer : peers) {
            final PeerAddress peerAddress = peer.getAddress();
            final InetAddress inetAddress = peerAddress.getAddr();
            final String ip = inetAddress.getHostAddress();
            final int port = peerAddress.getPort();
            final HostAndPort hostAndPort = HostAndPort.fromParts(ip, port);
            final String hostname = hostnames.get(inetAddress);
            final String displayHost = hostname != null ? hostname : ip;
            final HostAndPort displayHostAndPort;
            if (port != Constants.NETWORK_PARAMETERS.getPort())
                displayHostAndPort = HostAndPort.fromParts(displayHost, port);
            else
                displayHostAndPort = HostAndPort.fromHost(displayHost);
            final long height = peer.getBestHeight();
            final VersionMessage versionMessage = peer.getPeerVersionMessage();
            final String version = versionMessage.subVer;
            final String protocol = "protocol: " + versionMessage.clientVersion;
            final String services = peer.toStringServices(versionMessage.localServices).toLowerCase(Locale.US);
            final long pingTime = peer.getPingTime();
            final String ping = pingTime < Long.MAX_VALUE ?
                    context.getString(R.string.peer_list_row_ping_time, pingTime) : null;
            final Drawable icon;
            if (peer.isDownloadData()) {
                icon = context.getDrawable(R.drawable.ic_sync_white_24dp);
                icon.setTint(context.getColor(R.color.fg_significant));
            } else {
                icon = null;
            }
            items.add(new ListItem(hostAndPort, displayHostAndPort, height, version, protocol, services, ping, icon));
        }
        return items;
    }

    public static class ListItem {
        // internal item id
        public final long id;
        // external item id
        public final HostAndPort hostAndPort;

        public final HostAndPort displayHostAndPort;
        public final long height;
        public final String version;
        public final String protocol;
        public final String services;
        public final String ping;
        public final Drawable icon;

        public ListItem(final HostAndPort hostAndPort, final HostAndPort displayHostAndPort, final long height,
                        final String version, final String protocol, final String services, final String ping,
                        final Drawable icon) {
            this.id = id(hostAndPort);
            this.hostAndPort = hostAndPort;
            this.displayHostAndPort = displayHostAndPort;
            this.height = height;
            this.version = version;
            this.protocol = protocol;
            this.services = services;
            this.ping = ping;
            this.icon = icon;
        }

        private static long id(final HostAndPort hostAndPort) {
            return ID_HASH.newHasher().putUnencodedChars(hostAndPort.getHost()).putInt(hostAndPort.getPort())
                    .hash().asLong();
        }

        private static final HashFunction ID_HASH = Hashing.farmHashFingerprint64();
    }

    public interface OnClickListener {
        void onPeerClick(View view, HostAndPort peerHostAndPort);
    }

    private final LayoutInflater inflater;
    @Dimension
    private final int cardElevationSelected;

    private enum ChangeType {
        HOST, PING, ICON, SELECTION
    }

    @Nullable
    private final OnClickListener onClickListener;
    @Nullable
    private HostAndPort selectedPeerHostAndPort;

    public PeerListAdapter(final Context context, @Nullable final OnClickListener onClickListener) {
        super(new DiffUtil.ItemCallback<ListItem>() {
            @Override
            public boolean areItemsTheSame(final ListItem oldItem, final ListItem newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(final ListItem oldItem, final ListItem newItem) {
                if (!Objects.equals(oldItem.displayHostAndPort, newItem.displayHostAndPort))
                    return false;
                if (!Objects.equals(oldItem.ping, newItem.ping))
                    return false;
                if (!Objects.equals(oldItem.icon, newItem.icon))
                    return false;
                return true;
            }

            @Nullable
            @Override
            public Object getChangePayload(final ListItem oldItem, final ListItem newItem) {
                final EnumSet<ChangeType> changes = EnumSet.noneOf(ChangeType.class);
                if (!Objects.equals(oldItem.displayHostAndPort, newItem.displayHostAndPort))
                    changes.add(ChangeType.HOST);
                if (!Objects.equals(oldItem.ping, newItem.ping))
                    changes.add(ChangeType.PING);
                if (!Objects.equals(oldItem.icon, newItem.icon))
                    changes.add(ChangeType.ICON);
                return changes;
            }
        });

        this.inflater = LayoutInflater.from(context);
        this.cardElevationSelected = context.getResources().getDimensionPixelOffset(R.dimen.card_elevation_selected);
        this.onClickListener = onClickListener;

        setHasStableIds(true);
    }

    @MainThread
    public void setSelectedPeer(final HostAndPort newSelectedPeerHostAndPort) {
        if (Objects.equals(newSelectedPeerHostAndPort, selectedPeerHostAndPort))
            return;
        if (selectedPeerHostAndPort != null)
            notifyItemChanged(positionOf(selectedPeerHostAndPort), EnumSet.of(ChangeType.SELECTION));
        if (newSelectedPeerHostAndPort != null)
            notifyItemChanged(positionOf(newSelectedPeerHostAndPort), EnumSet.of(ChangeType.SELECTION));
        this.selectedPeerHostAndPort = newSelectedPeerHostAndPort;
    }

    @MainThread
    public int positionOf(final HostAndPort peerHostAndPort) {
        if (peerHostAndPort != null) {
            final List<ListItem> list = getCurrentList();
            for (int i = 0; i < list.size(); i++) {
                final ListItem item = list.get(i);
                if (item.hostAndPort.equals(peerHostAndPort))
                    return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public long getItemId(final int position) {
        final ListItem listItem = getItem(position);
        return listItem.id;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.peer_list_row, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position, final List<Object> payloads) {
        final boolean fullBind = payloads.isEmpty();
        final EnumSet<ChangeType> changes = EnumSet.noneOf(ChangeType.class);
        for (final Object payload : payloads)
            changes.addAll((EnumSet<ChangeType>) payload);

        final ListItem listItem = getItem(position);
        if (fullBind || changes.contains(ChangeType.SELECTION)) {
            final boolean isSelected = listItem.hostAndPort.equals(selectedPeerHostAndPort);
            holder.itemView.setSelected(isSelected);
            ((CardView) holder.itemView).setCardElevation(isSelected ? cardElevationSelected : 0);
        }
        if (fullBind || changes.contains(ChangeType.HOST)) {
            holder.hostView.setText(listItem.displayHostAndPort.toString());
        }
        if (fullBind || changes.contains(ChangeType.PING)) {
            holder.pingView.setText(listItem.ping);
        }
        if (fullBind || changes.contains(ChangeType.ICON)) {
            holder.iconView.setImageDrawable(listItem.icon);
        }
        if (fullBind) {
            holder.heightView.setText(listItem.height > 0 ? listItem.height + " blocks" : null);
            holder.versionView.setText(listItem.version);
            holder.protocolView.setText(listItem.protocol);
            holder.servicesView.setText(listItem.services);
            if (onClickListener != null)
                holder.itemView.setOnClickListener(v -> onClickListener.onPeerClick(v, listItem.hostAndPort));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView hostView;
        private final TextView heightView;
        private final TextView versionView;
        private final TextView protocolView;
        private final TextView servicesView;
        private final TextView pingView;
        private final ImageView iconView;

        private ViewHolder(final View itemView) {
            super(itemView);
            hostView = itemView.findViewById(R.id.peer_list_row_host);
            heightView = itemView.findViewById(R.id.peer_list_row_height);
            versionView = itemView.findViewById(R.id.peer_list_row_version);
            protocolView = itemView.findViewById(R.id.peer_list_row_protocol);
            servicesView = itemView.findViewById(R.id.peer_list_row_services);
            pingView = itemView.findViewById(R.id.peer_list_row_ping);
            iconView = itemView.findViewById(R.id.peer_list_row_icon);
        }
    }
}
