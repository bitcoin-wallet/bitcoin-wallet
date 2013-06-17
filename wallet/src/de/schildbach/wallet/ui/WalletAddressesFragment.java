/*
 * Copyright 2011-2013 the original author or authors.
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

import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.DetermineFirstSeenThread;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressesFragment extends SherlockListFragment
{
	private AddressBookActivity activity;
	private WalletApplication application;
	private ContentResolver contentResolver;
	private SharedPreferences prefs;

	private WalletAddressesAdapter adapter;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AddressBookActivity) activity;
		application = (WalletApplication) activity.getApplication();
		contentResolver = activity.getContentResolver();
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);

		final List<ECKey> keys = application.getWallet().getKeys();
		adapter = new WalletAddressesAdapter(activity, keys, true);

		final Address selectedAddress = application.determineSelectedAddress();
		adapter.setSelectedAddress(selectedAddress.toString());

		setListAdapter(adapter);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		contentResolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true, contentObserver);

		updateView();
	}

	@Override
	public void onPause()
	{
		contentResolver.unregisterContentObserver(contentObserver);

		super.onPause();
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.wallet_addresses_fragment_options, menu);

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_addresses_options_add:
				handleAddAddress();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleAddAddress()
	{
		new AlertDialog.Builder(activity).setTitle(R.string.wallet_addresses_fragment_add_dialog_title)
				.setMessage(R.string.wallet_addresses_fragment_add_dialog_message)
				.setPositiveButton(R.string.button_add, new DialogInterface.OnClickListener()
				{
					public void onClick(final DialogInterface dialog, final int which)
					{
						application.addNewKeyToWallet();
						adapter.notifyDataSetChanged();

						activity.updateFragments();
					}
				}).setNegativeButton(R.string.button_cancel, null).show();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		activity.startActionMode(new ActionMode.Callback()
		{
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.wallet_addresses_context, menu);

				return true;
			}

			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				final ECKey key = getKey(position);
				final boolean enabled = key.getCreationTimeSeconds() == 0;

				final MenuItem item = menu.findItem(R.id.wallet_addresses_context_determine_creation_time);
				item.setEnabled(enabled);
				item.setVisible(enabled);

				final String address = key.toAddress(Constants.NETWORK_PARAMETERS).toString();
				final String label = AddressBookProvider.resolveLabel(activity, address);
				mode.setTitle(label != null ? label : WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, 0));

				return true;
			}

			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.wallet_addresses_context_edit:
						handleEdit(getAddress(position));

						mode.finish();
						return true;

					case R.id.wallet_addresses_context_show_qr:
						handleShowQr(getAddress(position));

						mode.finish();
						return true;

					case R.id.wallet_addresses_context_copy_to_clipboard:
						handleCopyToClipboard(getAddress(position));

						mode.finish();
						return true;

					case R.id.wallet_addresses_context_default:
						handleDefault(getAddress(position));

						mode.finish();
						return true;

					case R.id.wallet_addresses_context_determine_creation_time:
						final ECKey key = getKey(position);
						handleDetermineCreationTime(key);

						mode.finish();
						return true;
				}

				return false;
			}

			public void onDestroyActionMode(final ActionMode mode)
			{
			}

			private ECKey getKey(final int position)
			{
				return (ECKey) getListAdapter().getItem(position);
			}

			private Address getAddress(final int position)
			{
				return getKey(position).toAddress(Constants.NETWORK_PARAMETERS);
			}

			private void handleEdit(final Address address)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}

			private void handleShowQr(final Address address)
			{
				final String uri = BitcoinURI.convertToBitcoinURI(address, null, null, null);
				final int size = (int) (256 * getResources().getDisplayMetrics().density);
				BitmapFragment.show(getFragmentManager(), WalletUtils.getQRCodeBitmap(uri, size));
			}

			private void handleCopyToClipboard(final Address address)
			{
				final ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
				clipboardManager.setText(address.toString());
				activity.toast(R.string.wallet_address_fragment_clipboard_msg);
			}

			private void handleDefault(final Address address)
			{
				prefs.edit().putString(Constants.PREFS_KEY_SELECTED_ADDRESS, address.toString()).commit();
				adapter.setSelectedAddress(address.toString());
			}
		});
	}

	private void handleDetermineCreationTime(final ECKey key)
	{
		new DetermineFirstSeenThread(key.toAddress(Constants.NETWORK_PARAMETERS).toString())
		{
			@Override
			protected void succeed(final Date firstSeen)
			{
				key.setCreationTimeSeconds((firstSeen != null ? firstSeen.getTime() : System.currentTimeMillis()) / DateUtils.SECOND_IN_MILLIS);
				updateView();
				application.saveWallet();
			}

			@Override
			protected void fail(final Exception x)
			{
				CrashReporter.saveBackgroundTrace(x);
			}
		};
	}

	private void updateView()
	{
		final ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
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
