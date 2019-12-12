/*
 * Copyright 2011-2015 the original author or authors.
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

package de.schildbach.wallet.ui;

import de.schildbach.wallet.R;
import de.schildbach.wallet.util.ViewPagerTabs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * @author Andreas Schildbach
 */
public final class AddressBookActivity extends AbstractWalletActivity {
    public static void start(final Context context) {
        context.startActivity(new Intent(context, AddressBookActivity.class));
    }

    private WalletAddressesFragment walletAddressesFragment;
    private SendingAddressesFragment sendingAddressesFragment;

    private static final String TAG_LEFT = "wallet_addresses";
    private static final String TAG_RIGHT = "sending_addresses";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.address_book_content);

        final FragmentManager fragmentManager = getSupportFragmentManager();

        walletAddressesFragment = (WalletAddressesFragment) fragmentManager.findFragmentByTag(TAG_LEFT);
        sendingAddressesFragment = (SendingAddressesFragment) fragmentManager.findFragmentByTag(TAG_RIGHT);

        final FragmentTransaction removal = fragmentManager.beginTransaction();

        if (walletAddressesFragment == null)
            walletAddressesFragment = new WalletAddressesFragment();
        else
            removal.remove(walletAddressesFragment);

        if (sendingAddressesFragment == null)
            sendingAddressesFragment = new SendingAddressesFragment();
        else
            removal.remove(sendingAddressesFragment);

        if (!removal.isEmpty()) {
            removal.commit();
            fragmentManager.executePendingTransactions();
        }

        final ViewPager pager = findViewById(R.id.address_book_pager);
        if (pager != null) {
            pager.setAdapter(
                    new TwoFragmentAdapter(fragmentManager, walletAddressesFragment, sendingAddressesFragment));

            final ViewPagerTabs pagerTabs = findViewById(R.id.address_book_pager_tabs);
            pagerTabs.addTabLabels(R.string.address_book_list_receiving_title,
                    R.string.address_book_list_sending_title);

            pager.setOnPageChangeListener(pagerTabs);
            final int position = 1;
            pager.setCurrentItem(position);
            pager.setPageMargin(2);
            pager.setPageMarginDrawable(R.color.bg_level0);

            pagerTabs.onPageSelected(position);
            pagerTabs.onPageScrolled(position, 0, 0);
        } else {
            fragmentManager.beginTransaction().add(R.id.wallet_addresses_fragment, walletAddressesFragment, TAG_LEFT)
                    .add(R.id.sending_addresses_fragment, sendingAddressesFragment, TAG_RIGHT).commit();
        }
    }

    private static class TwoFragmentAdapter extends PagerAdapter {
        private final FragmentManager fragmentManager;
        private final Fragment left;
        private final Fragment right;

        private FragmentTransaction currentTransaction = null;
        private Fragment currentPrimaryItem = null;

        public TwoFragmentAdapter(final FragmentManager fragmentManager, final Fragment left, final Fragment right) {
            this.fragmentManager = fragmentManager;
            this.left = left;
            this.right = right;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            if (currentTransaction == null)
                currentTransaction = fragmentManager.beginTransaction();

            final String tag = (position == 0) ? TAG_LEFT : TAG_RIGHT;
            final Fragment fragment = (position == 0) ? left : right;
            currentTransaction.add(container.getId(), fragment, tag);

            if (fragment != currentPrimaryItem) {
                fragment.setMenuVisibility(false);
                fragment.setUserVisibleHint(false);
            }

            return fragment;
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position, final Object object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPrimaryItem(final ViewGroup container, final int position, final Object object) {
            final Fragment fragment = (Fragment) object;
            if (fragment != currentPrimaryItem) {
                if (currentPrimaryItem != null) {
                    currentPrimaryItem.setMenuVisibility(false);
                    currentPrimaryItem.setUserVisibleHint(false);
                }
                if (fragment != null) {
                    fragment.setMenuVisibility(true);
                    fragment.setUserVisibleHint(true);
                }
                currentPrimaryItem = fragment;
            }
        }

        @Override
        public void finishUpdate(final ViewGroup container) {
            if (currentTransaction != null) {
                currentTransaction.commitAllowingStateLoss();
                currentTransaction = null;
                fragmentManager.executePendingTransactions();
            }
        }

        @Override
        public boolean isViewFromObject(final View view, final Object object) {
            return ((Fragment) object).getView() == view;
        }
    }
}
