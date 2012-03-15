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

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class EditAddressBookEntryFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = EditAddressBookEntryFragment.class.getName();

	private static final String KEY_ADDRESS = "address";

	private String address;

	public static void edit(final FragmentManager fm, final String address)
	{
		final FragmentTransaction ft = fm.beginTransaction();
		final Fragment prev = fm.findFragmentByTag(EditAddressBookEntryFragment.FRAGMENT_TAG);
		if (prev != null)
			ft.remove(prev);
		ft.addToBackStack(null);
		final DialogFragment newFragment = EditAddressBookEntryFragment.instance(address.toString());
		newFragment.show(ft, EditAddressBookEntryFragment.FRAGMENT_TAG);
	}

	private static EditAddressBookEntryFragment instance(final String address)
	{
		final EditAddressBookEntryFragment fragment = new EditAddressBookEntryFragment();

		final Bundle args = new Bundle();
		args.putString(KEY_ADDRESS, address);
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		this.address = getArguments().getString(KEY_ADDRESS);

		final FragmentActivity activity = getActivity();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final ContentResolver contentResolver = activity.getContentResolver();
		final Uri uri = AddressBookProvider.CONTENT_URI.buildUpon().appendPath(address).build();

		final String label = AddressBookProvider.resolveLabel(contentResolver, address);

		final boolean isAdd = label == null;

		final Builder dialog = new AlertDialog.Builder(activity).setTitle(isAdd ? R.string.edit_address_book_entry_dialog_title_add
				: R.string.edit_address_book_entry_dialog_title_edit);

		final View view = inflater.inflate(R.layout.edit_address_book_entry_dialog, null);

		final TextView viewAddress = (TextView) view.findViewById(R.id.edit_address_book_entry_address);
		viewAddress.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));

		final TextView viewLabel = (TextView) view.findViewById(R.id.edit_address_book_entry_label);
		viewLabel.setText(label);

		dialog.setView(view);

		dialog.setPositiveButton(isAdd ? R.string.edit_address_book_entry_dialog_button_add : R.string.edit_address_book_entry_dialog_button_edit,
				new DialogInterface.OnClickListener()
				{
					public void onClick(final DialogInterface dialog, final int whichButton)
					{
						final String newLabel = viewLabel.getText().toString().trim();

						if (newLabel.length() > 0)
						{
							final ContentValues values = new ContentValues();
							values.put(AddressBookProvider.KEY_LABEL, newLabel);

							if (isAdd)
								contentResolver.insert(uri, values);
							else
								contentResolver.update(uri, values, null, null);
						}
						else if (!isAdd)
						{
							contentResolver.delete(uri, null, null);
						}

						dismiss();
					}
				});
		if (!isAdd)
		{
			dialog.setNeutralButton(R.string.edit_address_book_entry_dialog_button_delete, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int whichButton)
				{
					contentResolver.delete(uri, null, null);
					dismiss();
				}
			});
		}
		dialog.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int whichButton)
			{
				dismiss();
			}
		});

		return dialog.create();
	}
}
