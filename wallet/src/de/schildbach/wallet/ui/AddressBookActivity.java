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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.Wallet;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.collect.Iterables;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class AddressBookActivity extends AbstractWalletActivity
{
	public static void start(final Context context)
	{
		context.startActivity(new Intent(context, AddressBookActivity.class));
	}

	private WalletAddressesFragment walletAddressesFragment;
	private SendingAddressesFragment sendingAddressesFragment;

	private static final String TAG_LEFT = "wallet_addresses";
	private static final String TAG_RIGHT = "sending_addresses";

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.address_book_content);

		final FragmentManager fragmentManager = getFragmentManager();

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

		if (!removal.isEmpty())
		{
			removal.commit();
			fragmentManager.executePendingTransactions();
		}

		final ViewPager pager = (ViewPager) findViewById(R.id.address_book_pager);
		if (pager != null)
		{
			pager.setAdapter(new TwoFragmentAdapter(fragmentManager, walletAddressesFragment, sendingAddressesFragment));

			final ViewPagerTabs pagerTabs = (ViewPagerTabs) findViewById(R.id.address_book_pager_tabs);
			pagerTabs.addTabLabels(R.string.address_book_list_receiving_title, R.string.address_book_list_sending_title);

			pager.setOnPageChangeListener(pagerTabs);
			final int position = 1;
			pager.setCurrentItem(position);
			pager.setPageMargin(2);
			pager.setPageMarginDrawable(R.color.bg_less_bright);

			pagerTabs.onPageSelected(position);
			pagerTabs.onPageScrolled(position, 0, 0);
		}
		else
		{
			fragmentManager.beginTransaction().add(R.id.wallet_addresses_fragment, walletAddressesFragment, TAG_LEFT)
					.add(R.id.sending_addresses_fragment, sendingAddressesFragment, TAG_RIGHT).commit();
		}

		updateFragments();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/* private */void updateFragments()
	{
		final Wallet wallet = getWalletApplication().getWallet();
		final List<ECKey> derivedKeys = wallet.getIssuedReceiveKeys();
		Collections.sort(derivedKeys, DeterministicKey.CHILDNUM_ORDER);
		final List<ECKey> randomKeys = wallet.getImportedKeys();
		final ArrayList<Address> addresses = new ArrayList<Address>(derivedKeys.size() + randomKeys.size());

		for (final ECKey key : Iterables.concat(derivedKeys, randomKeys))
		{
			final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
			addresses.add(address);
		}

		sendingAddressesFragment.setWalletAddresses(addresses);
	}

	private static class TwoFragmentAdapter extends PagerAdapter
	{
		private final FragmentManager fragmentManager;
		private final Fragment left;
		private final Fragment right;

		private FragmentTransaction currentTransaction = null;
		private Fragment currentPrimaryItem = null;

		public TwoFragmentAdapter(final FragmentManager fragmentManager, final Fragment left, final Fragment right)
		{
			this.fragmentManager = fragmentManager;
			this.left = left;
			this.right = right;
		}

		@Override
		public int getCount()
		{
			return 2;
		}

		@Override
		public Object instantiateItem(final ViewGroup container, final int position)
		{
			if (currentTransaction == null)
				currentTransaction = fragmentManager.beginTransaction();

			final String tag = (position == 0) ? TAG_LEFT : TAG_RIGHT;
			final Fragment fragment = (position == 0) ? left : right;
			currentTransaction.add(container.getId(), fragment, tag);

			if (fragment != currentPrimaryItem)
			{
				fragment.setMenuVisibility(false);
				fragment.setUserVisibleHint(false);
			}

			return fragment;
		}

		@Override
		public void destroyItem(final ViewGroup container, final int position, final Object object)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setPrimaryItem(final ViewGroup container, final int position, final Object object)
		{
			final Fragment fragment = (Fragment) object;
			if (fragment != currentPrimaryItem)
			{
				if (currentPrimaryItem != null)
				{
					currentPrimaryItem.setMenuVisibility(false);
					currentPrimaryItem.setUserVisibleHint(false);
				}
				if (fragment != null)
				{
					fragment.setMenuVisibility(true);
					fragment.setUserVisibleHint(true);
				}
				currentPrimaryItem = fragment;
			}
		}

		@Override
		public void finishUpdate(final ViewGroup container)
		{
			if (currentTransaction != null)
			{
				currentTransaction.commitAllowingStateLoss();
				currentTransaction = null;
				fragmentManager.executePendingTransactions();
			}
		}

		@Override
		public boolean isViewFromObject(final View view, final Object object)
		{
			return ((Fragment) object).getView() == view;
		}
	}
}
