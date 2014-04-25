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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.utils.Threading;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressesFragment extends SherlockListFragment
{
	private AddressBookActivity activity;
	private WalletApplication application;
	private Configuration config;
	private Wallet wallet;
	private ContentResolver contentResolver;

	private WalletAddressesAdapter adapter;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AddressBookActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.config = application.getConfiguration();
		this.wallet = application.getWallet();
		this.contentResolver = activity.getContentResolver();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);

		adapter = new WalletAddressesAdapter(activity, wallet);

		final Address selectedAddress = application.determineSelectedAddress();
		adapter.setSelectedAddress(selectedAddress.toString());

		setListAdapter(adapter);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		contentResolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true, contentObserver);

		wallet.addEventListener(walletListener, Threading.SAME_THREAD);
		walletListener.onKeysAdded(null); // trigger initial load of keys

		updateView();
	}

	@Override
	public void onPause()
	{
		wallet.removeEventListener(walletListener);

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
		final DialogBuilder dialog = new DialogBuilder(activity);
		dialog.setTitle(R.string.wallet_addresses_fragment_add_dialog_title);
		dialog.setMessage(R.string.wallet_addresses_fragment_add_dialog_message);
		dialog.setPositiveButton(R.string.button_add, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				application.addNewKeyToWallet();

				activity.updateFragments();
			}
		});
		dialog.setNegativeButton(R.string.button_cancel, null);
		dialog.show();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		activity.startActionMode(new ActionMode.Callback()
		{
			@Override
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.wallet_addresses_context, menu);

				return true;
			}

			@Override
			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				final ECKey key = getKey(position);

				final String address = key.toAddress(Constants.NETWORK_PARAMETERS).toString();
				final String label = AddressBookProvider.resolveLabel(activity, address);
				mode.setTitle(label != null ? label : WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, 0));

				return true;
			}

			@Override
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

					case R.id.wallet_addresses_context_browse:
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.EXPLORE_BASE_URL + "address/"
								+ getAddress(position).toString())));

						mode.finish();
						return true;
				}

				return false;
			}

			@Override
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

			private void handleEdit(@Nonnull final Address address)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}

			private void handleShowQr(@Nonnull final Address address)
			{
				final String uri = BitcoinURI.convertToBitcoinURI(address, null, null, null);
				final int size = (int) (256 * getResources().getDisplayMetrics().density);
				BitmapFragment.show(getFragmentManager(), Qr.bitmap(uri, size));
			}

			private void handleCopyToClipboard(@Nonnull final Address address)
			{
				final ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
				clipboardManager.setText(address.toString());
				activity.toast(R.string.wallet_address_fragment_clipboard_msg);
			}

			private void handleDefault(@Nonnull final Address address)
			{
				final String addressStr = address.toString();
				config.setSelectedAddress(addressStr);
				adapter.setSelectedAddress(addressStr);
			}
		});
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

	private final WalletEventListener walletListener = new AbstractWalletEventListener()
	{
		@Override
		public void onKeysAdded(final List<ECKey> keysAdded)
		{
			final List<ECKey> keys = wallet.getImportedKeys();

			Collections.sort(keys, new Comparator<ECKey>()
			{
				@Override
				public int compare(final ECKey lhs, final ECKey rhs)
				{
					final boolean lhsRotating = wallet.isKeyRotating(lhs);
					final boolean rhsRotating = wallet.isKeyRotating(rhs);

					if (lhsRotating != rhsRotating)
						return lhsRotating ? 1 : -1;

					if (lhs.getCreationTimeSeconds() != rhs.getCreationTimeSeconds())
						return lhs.getCreationTimeSeconds() > rhs.getCreationTimeSeconds() ? 1 : -1;

					return 0;
				}
			});

			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					adapter.replace(keys);
				}
			});
		}
	};
}
