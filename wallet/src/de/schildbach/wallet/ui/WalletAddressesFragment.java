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

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.text.ClipboardManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.DetermineFirstSeenThread;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.QrDialog;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressesFragment extends ListFragment
{
	private WalletApplication application;
	private Activity activity;
	private List<ECKey> keys;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		activity = getActivity();
		application = (WalletApplication) activity.getApplication();
		final Wallet wallet = application.getWallet();
		keys = wallet.keychain;

		setListAdapter(new Adapter());
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		registerForContextMenu(getListView());
	}

	@Override
	public void onResume()
	{
		super.onResume();

		activity.getContentResolver().registerContentObserver(AddressBookProvider.CONTENT_URI, true, contentObserver);

		updateView();
	}

	@Override
	public void onPause()
	{
		activity.getContentResolver().unregisterContentObserver(contentObserver);

		super.onPause();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final ECKey key = keys.get(position);
		final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);

		EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
	}

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo info)
	{
		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) info;
		final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);
		final boolean enabled = key.getCreationTimeSeconds() == 0;

		activity.getMenuInflater().inflate(R.menu.wallet_addresses_context, menu);
		final MenuItem item = menu.findItem(R.id.wallet_addresses_context_determine_creation_time);
		item.setEnabled(enabled);
		item.setVisible(enabled);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();

		switch (item.getItemId())
		{
			case R.id.wallet_addresses_context_edit:
			{
				final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);
				final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
				return true;
			}

			case R.id.wallet_addresses_context_show_qr:
			{
				final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);
				final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
				final String uri = BitcoinURI.convertToBitcoinURI(address, null, null, null);
				final int size = (int) (256 * getResources().getDisplayMetrics().density);
				new QrDialog(activity, WalletUtils.getQRCodeBitmap(uri, size)).show();
				return true;
			}

			case R.id.wallet_addresses_context_copy_to_clipboard:
			{
				final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);
				final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
				handleCopyToClipboard(address.toString());
				return true;
			}

			case R.id.wallet_addresses_context_default:
			{
				final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);
				final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
				handleDefault(address);
				return true;
			}

			case R.id.wallet_addresses_context_determine_creation_time:
			{
				final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);
				handleDetermineCreationTime(key);
				return true;
			}

			default:
				return false;
		}
	}

	private void handleDefault(final Address address)
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		prefs.edit().putString(Constants.PREFS_KEY_SELECTED_ADDRESS, address.toString()).commit();
	}

	private void handleCopyToClipboard(final String address)
	{
		final ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboardManager.setText(address);
		((AbstractWalletActivity) activity).toast(R.string.wallet_address_fragment_clipboard_msg);
	}

	private void handleDetermineCreationTime(final ECKey key)
	{
		new DetermineFirstSeenThread(key.toAddress(Constants.NETWORK_PARAMETERS).toString())
		{
			@Override
			protected void succeed(final Date firstSeen)
			{
				if (firstSeen != null)
				{
					activity.runOnUiThread(new Runnable()
					{
						public void run()
						{
							key.setCreationTimeSeconds(firstSeen.getTime() / 1000);
							updateView();
							application.saveWallet();
						}
					});
				}
			}
		};
	}

	private void updateView()
	{
		final ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

	private class Adapter extends BaseAdapter
	{
		final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);
		final Resources res = getResources();

		public int getCount()
		{
			return keys.size();
		}

		public Object getItem(final int position)
		{
			return keys.get(position);
		}

		public long getItemId(final int position)
		{
			return keys.get(position).hashCode();
		}

		public View getView(final int position, View row, final ViewGroup parent)
		{
			final ECKey key = (ECKey) getItem(position);
			final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);

			if (row == null)
				row = getLayoutInflater(null).inflate(R.layout.address_book_row, null);

			final TextView addressView = (TextView) row.findViewById(R.id.address_book_row_address);
			addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));

			final TextView labelView = (TextView) row.findViewById(R.id.address_book_row_label);
			final String label = AddressBookProvider.resolveLabel(activity.getContentResolver(), address.toString());
			if (label != null)
			{
				labelView.setText(label);
				labelView.setTextColor(res.getColor(R.color.less_significant));
			}
			else
			{
				labelView.setText(R.string.wallet_addresses_fragment_unlabeled);
				labelView.setTextColor(res.getColor(R.color.insignificant));
			}

			final TextView createdView = (TextView) row.findViewById(R.id.address_book_row_created);
			final long created = key.getCreationTimeSeconds();
			if (created != 0)
			{
				createdView.setText(dateFormat.format(new Date(created * 1000)));
				createdView.setVisibility(View.VISIBLE);
			}
			else
			{
				createdView.setVisibility(View.GONE);
			}

			return row;
		}
	}

	private final Handler handler = new Handler();

	private final ContentObserver contentObserver = new ContentObserver(handler)
	{
		@Override
		public void onChange(final boolean selfChange)
		{
			updateView();
		}
	};
}
