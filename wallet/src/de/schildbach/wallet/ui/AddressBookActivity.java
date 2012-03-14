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
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.ClipboardManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.ActionBarFragment;
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
	private ImageButton addButton;
	private ImageButton pasteButton;
	private ImageButton scanButton;

	private final Handler handler = new Handler();

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
					if (position == 0)
					{
						if (scanButton != null)
						{
							actionBar.removeButton(scanButton);
							scanButton = null;
						}

						if (pasteButton != null)
						{
							actionBar.removeButton(pasteButton);
							pasteButton = null;
						}

						if (addButton == null)
						{
							addButton = actionBar.addButton(R.drawable.ic_action_add);
							addButton.setOnClickListener(addAddressClickListener);
						}
					}
					else if (position == 1)
					{
						if (addButton != null)
						{
							actionBar.removeButton(addButton);
							addButton = null;
						}

						if (scanButton == null)
						{
							scanButton = actionBar.addButton(R.drawable.ic_action_qr);
							scanButton.setOnClickListener(scanClickListener);
						}

						if (pasteButton == null)
						{
							pasteButton = actionBar.addButton(R.drawable.ic_action_paste);
							pasteButton.setOnClickListener(pasteClipboardClickListener);
						}
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
			scanButton = actionBar.addButton(R.drawable.ic_action_qr);
			scanButton.setOnClickListener(scanClickListener);

			pasteButton = actionBar.addButton(R.drawable.ic_action_paste);
			pasteButton.setOnClickListener(pasteClipboardClickListener);

			addButton = actionBar.addButton(R.drawable.ic_action_add);
			addButton.setOnClickListener(addAddressClickListener);

			walletAddressesFragment = (WalletAddressesFragment) getSupportFragmentManager().findFragmentById(R.id.wallet_addresses_fragment);
			sendingAddressesFragment = (SendingAddressesFragment) getSupportFragmentManager().findFragmentById(R.id.sending_addresses_fragment);
		}

		updateFragments();
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK && "QR_CODE".equals(intent.getStringExtra("SCAN_RESULT_FORMAT")))
		{
			final String contents = intent.getStringExtra("SCAN_RESULT");

			try
			{
				final Address address;

				if (contents.matches("[a-zA-Z0-9]*"))
				{
					address = new Address(Constants.NETWORK_PARAMETERS, contents);
				}
				else
				{
					final BitcoinURI bitcoinUri = new BitcoinURI(Constants.NETWORK_PARAMETERS, contents);
					address = bitcoinUri.getAddress();
				}

				handler.postDelayed(new Runnable()
				{
					public void run()
					{
						EditAddressBookEntryFragment.edit(getSupportFragmentManager(), address.toString());
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

	private final OnClickListener scanClickListener = new OnClickListener()
	{
		public void onClick(final View v)
		{
			handleScan();
		}
	};

	private void handleScan()
	{
		if (getPackageManager().resolveActivity(Constants.INTENT_QR_SCANNER, 0) != null)
		{
			startActivityForResult(Constants.INTENT_QR_SCANNER, REQUEST_CODE_SCAN);
		}
		else
		{
			showMarketPage(Constants.PACKAGE_NAME_ZXING);
			longToast(R.string.send_coins_install_qr_scanner_msg);
		}
	}

	private final OnClickListener pasteClipboardClickListener = new OnClickListener()
	{
		public void onClick(final View v)
		{
			handlePasteClipboard();
		}
	};

	private void handlePasteClipboard()
	{
		final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

		if (clipboardManager.hasText())
		{
			final String text = clipboardManager.getText().toString().trim();

			try
			{
				final Address address = new Address(Constants.NETWORK_PARAMETERS, text);
				EditAddressBookEntryFragment.edit(getSupportFragmentManager(), address.toString());
			}
			catch (final AddressFormatException x)
			{
				toast(R.string.send_coins_parse_address_error_msg);
			}
		}
		else
		{
			toast(R.string.address_book_msg_clipboard_empty);
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
