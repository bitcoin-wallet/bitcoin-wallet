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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.ActionBarFragment;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class AddressBookActivity extends AbstractWalletActivity
{
	private static final String EXTRA_SENDING = "sending";

	public static void start(final Context context, final boolean sending)
	{
		final Intent intent = new Intent(context, AddressBookActivity.class);
		intent.putExtra(EXTRA_SENDING, sending);
		context.startActivity(intent);
	}

	private WalletAddressesFragment walletAddressesFragment;
	private SendingAddressesFragment sendingAddressesFragment;
	private ImageButton addButton;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.address_book_content);

		final ActionBarFragment actionBar = getActionBar();

		actionBar.setPrimaryTitle(R.string.address_book_activity_title);

		actionBar.setBack(new OnClickListener()
		{
			public void onClick(final View v)
			{
				finish();
			}
		});

		final ViewPager pager = (ViewPager) findViewById(R.id.address_book_pager);

		if (pager != null)
		{
			final ViewPagerTabs pagerTabs = (ViewPagerTabs) findViewById(R.id.address_book_pager_tabs);
			pagerTabs.addTabLabels(R.string.address_book_list_receiving_title, R.string.address_book_list_sending_title);
			final ProxyOnPageChangeListener pagerListener = new ProxyOnPageChangeListener(pagerTabs)
			{
				@Override
				public void onPageSelected(final int position)
				{
					if (position == 0 && addButton == null)
					{
						addButton = actionBar.addButton(R.drawable.ic_action_add);
						addButton.setOnClickListener(addAddressClickListener);
					}
					else if (position == 1 && addButton != null)
					{
						actionBar.removeButton(addButton);
						addButton = null;
					}

					super.onPageSelected(position);
				}
			};

			final PagerAdapter pagerAdapter = new PagerAdapter(getSupportFragmentManager());

			pager.setAdapter(pagerAdapter);
			pager.setOnPageChangeListener(pagerListener);
			final int position = getIntent().getBooleanExtra(EXTRA_SENDING, true) == true ? 1 : 0;
			pager.setCurrentItem(position);
			pager.setPageMargin(2);
			pager.setPageMarginDrawable(R.color.background_less_bright);

			pagerListener.onPageSelected(position);
			pagerListener.onPageScrolled(position, 0, 0);

			walletAddressesFragment = new WalletAddressesFragment();
			sendingAddressesFragment = new SendingAddressesFragment();
		}
		else
		{
			addButton = actionBar.addButton(R.drawable.ic_action_add);
			addButton.setOnClickListener(addAddressClickListener);

			walletAddressesFragment = (WalletAddressesFragment) getSupportFragmentManager().findFragmentById(R.id.wallet_addresses_fragment);
			sendingAddressesFragment = (SendingAddressesFragment) getSupportFragmentManager().findFragmentById(R.id.sending_addresses_fragment);
		}

		updateFragments();
	}

	private void updateFragments()
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

	private final OnClickListener addAddressClickListener = new OnClickListener()
	{
		public void onClick(final View v)
		{
			handleAddAddress();
		}
	};

	private void handleAddAddress()
	{
		new AlertDialog.Builder(AddressBookActivity.this).setTitle(R.string.wallet_addresses_fragment_add_dialog_title)
				.setMessage(R.string.wallet_addresses_fragment_add_dialog_message)
				.setPositiveButton(R.string.wallet_addresses_fragment_add_dialog_positive, new DialogInterface.OnClickListener()
				{
					public void onClick(final DialogInterface dialog, final int which)
					{
						getWalletApplication().addNewKeyToWallet();

						updateFragments();
					}
				}).setNegativeButton(R.string.button_cancel, null).show();
	}

	private class ProxyOnPageChangeListener implements OnPageChangeListener
	{
		private final OnPageChangeListener onPageChangeListener;

		public ProxyOnPageChangeListener(final OnPageChangeListener onPageChangeListener)
		{
			this.onPageChangeListener = onPageChangeListener;
		}

		public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels)
		{
			onPageChangeListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
		}

		public void onPageSelected(final int position)
		{
			onPageChangeListener.onPageSelected(position);
		}

		public void onPageScrollStateChanged(final int state)
		{
			onPageChangeListener.onPageScrollStateChanged(state);
		}
	}
}
