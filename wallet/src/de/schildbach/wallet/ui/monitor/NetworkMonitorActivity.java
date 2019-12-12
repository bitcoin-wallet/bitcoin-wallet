/*
 * Copyright 2013-2015 the original author or authors.
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

import de.schildbach.wallet.R;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.util.ViewPagerTabs;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * @author Andreas Schildbach
 */
public final class NetworkMonitorActivity extends AbstractWalletActivity {
    private PeerListFragment peerListFragment;
    private BlockListFragment blockListFragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.network_monitor_content);

        final ViewPager pager = findViewById(R.id.network_monitor_pager);

        final FragmentManager fm = getSupportFragmentManager();

        if (pager != null) {
            final ViewPagerTabs pagerTabs = findViewById(R.id.network_monitor_pager_tabs);
            pagerTabs.addTabLabels(R.string.network_monitor_peer_list_title, R.string.network_monitor_block_list_title);

            final PagerAdapter pagerAdapter = new PagerAdapter(fm);

            pager.setAdapter(pagerAdapter);
            pager.setOnPageChangeListener(pagerTabs);
            pager.setPageMargin(2);
            pager.setPageMarginDrawable(R.color.bg_level0);

            peerListFragment = new PeerListFragment();
            blockListFragment = new BlockListFragment();
        } else {
            peerListFragment = (PeerListFragment) fm.findFragmentById(R.id.peer_list_fragment);
            blockListFragment = (BlockListFragment) fm.findFragmentById(R.id.block_list_fragment);
        }
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {
        public PagerAdapter(final FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Fragment getItem(final int position) {
            if (position == 0)
                return peerListFragment;
            else
                return blockListFragment;
        }
    }
}
