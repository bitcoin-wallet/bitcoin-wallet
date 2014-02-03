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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class EditTransactionNoteFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = EditTransactionNoteFragment.class.getName();
	private static final String KEY_TRANSACTION = "transaction";
    private SharedPreferences prefs;
    private TransactionsListAdapter adapter;

	public static void edit(final FragmentManager fm, @Nonnull final String address, TransactionsListAdapter adapter)
	{
		final EditTransactionNoteFragment newFragment = EditTransactionNoteFragment.instance(address);
        newFragment.setAdapter(adapter);
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private static EditTransactionNoteFragment instance(@Nonnull final String txid)
	{
		final EditTransactionNoteFragment fragment = new EditTransactionNoteFragment();

		final Bundle args = new Bundle();
		args.putString(KEY_TRANSACTION, txid);
		fragment.setArguments(args);

		return fragment;
	}

	private Activity activity;
	private ContentResolver contentResolver;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
		this.contentResolver = activity.getContentResolver();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplication());
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Bundle args = getArguments();
		final String txid = args.getString(KEY_TRANSACTION);

		final LayoutInflater inflater = LayoutInflater.from(activity);

		final String note = prefs.getString("tx:"+txid, "");
        final boolean isAdd = note.isEmpty();

		final AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
		dialog.setInverseBackgroundForced(true);
		dialog.setTitle(R.string.edit_transaction_note_dialog_title_edit);

		final View view = inflater.inflate(R.layout.edit_transaction_note_dialog, null);

		final TextView viewTxid = (TextView) view.findViewById(R.id.edit_transaction_note_txid);
		viewTxid.setText(txid);

		final TextView viewNote = (TextView) view.findViewById(R.id.edit_transaction_note_note);
		viewNote.setText(note);

		dialog.setView(view);

		final DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				if (which == DialogInterface.BUTTON_POSITIVE)
				{
					final String newNote = viewNote.getText().toString().trim();

					if (!newNote.isEmpty())
					{
						prefs.edit().putString("tx:"+txid, newNote).commit();
					}
				}
                if (which == DialogInterface.BUTTON_NEUTRAL)
                {
                    prefs.edit().remove("tx:"+txid).commit();
                }
                // Notify list of update
                if(adapter != null)
                    adapter.notifyDataSetChanged();

				dismiss();
			}
		};

		dialog.setPositiveButton(isAdd ? R.string.button_add : R.string.edit_address_book_entry_dialog_button_edit, onClickListener);
		if (!isAdd)
			dialog.setNeutralButton(R.string.button_delete, onClickListener);
		dialog.setNegativeButton(R.string.button_cancel, onClickListener);

		return dialog.create();
	}

    public void setAdapter(TransactionsListAdapter adapter) {
        this.adapter = adapter;
    }
}
