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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.text.ClipboardManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class SendingAddressesFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	private Activity activity;
	private SimpleCursorAdapter adapter;
	private String walletAddressesSelection;

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		activity = getActivity();

		setEmptyText(getString(R.string.address_book_empty_text));

		adapter = new SimpleCursorAdapter(activity, R.layout.address_book_row, null, new String[] { AddressBookProvider.KEY_LABEL,
				AddressBookProvider.KEY_ADDRESS }, new int[] { R.id.address_book_row_label, R.id.address_book_row_address }, 0);
		adapter.setViewBinder(new ViewBinder()
		{
			public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex)
			{
				if (!AddressBookProvider.KEY_ADDRESS.equals(cursor.getColumnName(columnIndex)))
					return false;

				((TextView) view).setText(WalletUtils.splitIntoLines(cursor.getString(columnIndex), 3));

				return true;
			}
		});
		setListAdapter(adapter);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		registerForContextMenu(getListView());
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final Cursor cursor = (Cursor) adapter.getItem(position);
		final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
		handleSend(address);
	}

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo)
	{
		activity.getMenuInflater().inflate(R.menu.sending_addresses_context, menu);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();

		switch (item.getItemId())
		{
			case R.id.sending_addresses_context_send:
			{
				final Cursor cursor = (Cursor) adapter.getItem(menuInfo.position);
				final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
				handleSend(address);
				return true;
			}

			case R.id.sending_addresses_context_edit:
			{
				final Cursor cursor = (Cursor) adapter.getItem(menuInfo.position);
				final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
				EditAddressBookEntryFragment.edit(getFragmentManager(), address);
				return true;
			}

			case R.id.sending_addresses_context_remove:
			{
				final Cursor cursor = (Cursor) adapter.getItem(menuInfo.position);
				final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
				handleRemove(address);
				return true;
			}

			case R.id.sending_addresses_context_copy_to_clipboard:
			{
				final Cursor cursor = (Cursor) adapter.getItem(menuInfo.position);
				final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
				handleCopyToClipboard(address);
				return true;
			}

			default:
				return false;
		}
	}

	private void handleSend(final String address)
	{
		final Intent intent = new Intent(activity, SendCoinsActivity.class);
		intent.putExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS, address);
		startActivity(intent);
	}

	private void handleRemove(final String address)
	{
		final Uri uri = AddressBookProvider.CONTENT_URI.buildUpon().appendPath(address).build();
		activity.getContentResolver().delete(uri, null, null);
	}

	private void handleCopyToClipboard(final String address)
	{
		ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboardManager.setText(address);
		((AbstractWalletActivity) activity).toast(R.string.wallet_address_fragment_clipboard_msg);
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		final Uri uri = AddressBookProvider.CONTENT_URI;
		return new CursorLoader(activity, uri, null, "notin", new String[] { walletAddressesSelection }, AddressBookProvider.KEY_LABEL
				+ " COLLATE LOCALIZED ASC");
	}

	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		adapter.swapCursor(data);
	}

	public void onLoaderReset(final Loader<Cursor> loader)
	{
		adapter.swapCursor(null);
	}

	public void setWalletAddresses(final ArrayList<Address> addresses)
	{
		final StringBuilder builder = new StringBuilder();
		for (final Address address : addresses)
			builder.append(address.toString()).append(",");
		if (addresses.size() > 0)
			builder.setLength(builder.length() - 1);

		walletAddressesSelection = builder.toString();
	}
}
