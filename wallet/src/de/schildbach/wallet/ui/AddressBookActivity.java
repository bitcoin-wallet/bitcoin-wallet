/*
 * Copyright 2011-2014 the original author or authors.
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
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class AddressBookActivity extends AbstractWalletActivity
{
	public static void start(final Context context, final boolean sending)
	{
		final Intent intent = new Intent(context, AddressBookActivity.class);
		intent.putExtra(EXTRA_SENDING, sending);
		context.startActivity(intent);
	}

	private static final String EXTRA_SENDING = "sending";

	private WalletAddressesFragment walletAddressesFragment;
	private SendingAddressesFragment sendingAddressesFragment;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.address_book_content);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		final ViewPager pager = (ViewPager) findViewById(R.id.address_book_pager);

		final FragmentManager fm = getSupportFragmentManager();

		if (pager != null)
		{
			final ViewPagerTabs pagerTabs = (ViewPagerTabs) findViewById(R.id.address_book_pager_tabs);
			pagerTabs.addTabLabels(R.string.address_book_list_receiving_title, R.string.address_book_list_sending_title);

			final PagerAdapter pagerAdapter = new PagerAdapter(fm);

			pager.setAdapter(pagerAdapter);
			pager.setOnPageChangeListener(pagerTabs);
			final int position = getIntent().getBooleanExtra(EXTRA_SENDING, true) == true ? 1 : 0;
			pager.setCurrentItem(position);
			pager.setPageMargin(2);
			pager.setPageMarginDrawable(R.color.bg_less_bright);

			pagerTabs.onPageSelected(position);
			pagerTabs.onPageScrolled(position, 0, 0);

			walletAddressesFragment = new WalletAddressesFragment();
			sendingAddressesFragment = new SendingAddressesFragment();
		}
		else
		{
			walletAddressesFragment = (WalletAddressesFragment) fm.findFragmentById(R.id.wallet_addresses_fragment);
			sendingAddressesFragment = (SendingAddressesFragment) fm.findFragmentById(R.id.sending_addresses_fragment);
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
		final List<ECKey> keys = getWalletApplication().getWallet().getKeys();
		final ArrayList<Address> addresses = new ArrayList<Address>(keys.size());

		for (final ECKey key : keys)
		{
			final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
			addresses.add(address);
		}

		sendingAddressesFragment.setWalletAddresses(addresses);
	}

	private class PagerAdapter extends FragmentStatePagerAdapter
	{
		public PagerAdapter(final FragmentManager fm)
		{
			super(fm);
		}

		@Override
		public int getCount()
		{
			return 2;
		}

		@Override
		public Fragment getItem(final int position)
		{
			if (position == 0)
				return walletAddressesFragment;
			else
				return sendingAddressesFragment;
		}
	}
}
