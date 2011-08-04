/*
 * Copyright 2010 the original author or authors.
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

package de.schildbach.wallet;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class AddressBookFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	private SimpleCursorAdapter adapter;

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		setEmptyText(getString(R.string.address_book_empty_text));

		adapter = new SimpleCursorAdapter(getActivity(), R.layout.address_book_row, null, new String[] { AddressBookProvider.KEY_LABEL,
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
		getActivity().getMenuInflater().inflate(R.menu.address_book_context, menu);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		final Cursor cursor = (Cursor) adapter.getItem(menuInfo.position);
		final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));

		switch (item.getItemId())
		{
			case R.id.address_book_context_send:
				handleSend(address);
				return true;

			case R.id.address_book_context_edit:
				handleEdit(address);
				return true;

			case R.id.address_book_context_remove:
				handleRemove(address);
				return true;

			default:
				return false;
		}
	}

	private void handleSend(final String address)
	{
		final Intent intent = new Intent(getActivity(), SendCoinsActivity.class);
		intent.putExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS, address);
		startActivity(intent);
	}

	private void handleEdit(final String address)
	{
		final FragmentTransaction ft = getFragmentManager().beginTransaction();
		final Fragment prev = getFragmentManager().findFragmentByTag(EditAddressBookEntryFragment.FRAGMENT_TAG);
		if (prev != null)
			ft.remove(prev);
		ft.addToBackStack(null);
		final DialogFragment newFragment = new EditAddressBookEntryFragment(getLayoutInflater(null), address);
		newFragment.show(ft, EditAddressBookEntryFragment.FRAGMENT_TAG);
	}

	private void handleRemove(final String address)
	{
		final Uri uri = AddressBookProvider.CONTENT_URI.buildUpon().appendPath(address).build();
		getActivity().getContentResolver().delete(uri, null, null);
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		return new CursorLoader(getActivity(), AddressBookProvider.CONTENT_URI, null, null, null, AddressBookProvider.KEY_LABEL
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
}
