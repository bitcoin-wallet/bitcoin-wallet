/*
 * Copyright 2011-2012 the original author or authors.
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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet_test.R;

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

	private static final int REQUEST_CODE_SCAN = 0;

	private WalletAddressesFragment walletAddressesFragment;
	private SendingAddressesFragment sendingAddressesFragment;

	private FragmentManager fm;
	private final Handler handler = new Handler();

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.address_book_content);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setTitle(R.string.address_book_activity_title);
		actionBar.setDisplayHomeAsUpEnabled(true);

		final ViewPager pager = (ViewPager) findViewById(R.id.address_book_pager);

		fm = getSupportFragmentManager();

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
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK)
		{
			final String contents = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

			try
			{
				final Address address;

				if (contents.matches("[a-zA-Z0-9]*"))
				{
					address = new Address(Constants.NETWORK_PARAMETERS, contents);
				}
				else
				{
					// TODO nicer cross-network handling
					final BitcoinURI bitcoinUri = new BitcoinURI(Constants.NETWORK_PARAMETERS, contents);
					address = bitcoinUri.getAddress();
				}

				handler.postDelayed(new Runnable()
				{
					public void run()
					{
						EditAddressBookEntryFragment.edit(fm, address.toString());
					}
				}, 500);
			}
			catch (final AddressFormatException x)
			{
				parseErrorDialog(contents);
			}
			catch (final BitcoinURIParseException x)
			{
				parseErrorDialog(contents);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;

			case R.id.sending_addresses_options_scan:
				handleScan();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleScan()
	{
		startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	/* private */void updateFragments()
	{
		final ArrayList<ECKey> keychain = getWalletApplication().getWallet().keychain;
		final ArrayList<Address> addresses = new ArrayList<Address>(keychain.size());

		for (final ECKey key : keychain)
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
