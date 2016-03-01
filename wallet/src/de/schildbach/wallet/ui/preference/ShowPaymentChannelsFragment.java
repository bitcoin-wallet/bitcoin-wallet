/*
 * Copyright 2016 the original author or authors.
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
package de.schildbach.wallet.ui.preference;

import android.app.Activity;
import android.app.ListFragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.google.common.collect.Lists;

import org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates;
import org.bitcoinj.protocols.channels.StoredPaymentChannelServerStates;
import org.bitcoinj.protocols.channels.StoredServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import de.schildbach.wallet.WalletApplication;

public class ShowPaymentChannelsFragment extends ListFragment {

    private static final String FRAGMENT_TAG = ShowPaymentChannelsFragment.class.getName();

    private Activity activity;
    private WalletApplication application;

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
        this.application = (WalletApplication) activity.getApplication();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setListAdapter(adapter);
    }

    private List<String> channels;

    private List<String> getChannels() {
        if (channels != null) {
            return channels;
        }
        StoredPaymentChannelClientStates clients =
                StoredPaymentChannelClientStates.getFromWallet(application.getWallet());
        StoredPaymentChannelServerStates servers =
                StoredPaymentChannelServerStates.getFromWallet(application.getWallet());
        channels = Lists.newArrayList();
        // Non-public class
        for (Object channel : clients.getChannelMap().values()) {
            channels.add(channel.toString());
        }
        for (StoredServerChannel channel : servers.getChannelMap().values()) {
            channels.add(channel.toString());
        }
        return channels;
    }

    private final ListAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return getChannels().size();
        }

        @Override
        public String getItem(int position) {
            return getChannels().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView == null) {
                view = new TextView(getActivity());
            } else {
                view = (TextView) convertView;
            }

            view.setTypeface(Typeface.MONOSPACE);
            view.setText(getItem(position));

            return view;
        }
    };
}
