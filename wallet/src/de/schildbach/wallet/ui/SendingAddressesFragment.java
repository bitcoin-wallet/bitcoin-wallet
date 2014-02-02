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

import java.math.BigInteger;
import java.util.ArrayList;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.util.IntentIntegratorSupportV4;
import de.schildbach.wallet.util.IntentResult;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.util.AbstractClipboardManager;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class SendingAddressesFragment extends SherlockListFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	private AbstractWalletActivity activity;
	private AbstractClipboardManager clipboardManager;
	private LoaderManager loaderManager;

	private SimpleCursorAdapter adapter;
	private String walletAddressesSelection;

	private final Handler handler = new Handler();

	private static final int REQUEST_CODE_SCAN = 0;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);
        this.clipboardManager = new AbstractClipboardManager(activity);
		this.activity = (AbstractWalletActivity) activity;
		this.loaderManager = getLoaderManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		setEmptyText(getString(R.string.address_book_empty_text));

		adapter = new SimpleCursorAdapter(activity, R.layout.address_book_row, null, new String[] { AddressBookProvider.KEY_LABEL,
				AddressBookProvider.KEY_ADDRESS }, new int[] { R.id.address_book_row_label, R.id.address_book_row_address }, 0);
		adapter.setViewBinder(new ViewBinder()
		{
			@Override
			public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex)
			{
				if (!AddressBookProvider.KEY_ADDRESS.equals(cursor.getColumnName(columnIndex)))
					return false;

				((TextView) view).setText(WalletUtils.formatHash(cursor.getString(columnIndex), Constants.ADDRESS_FORMAT_GROUP_SIZE,
						Constants.ADDRESS_FORMAT_LINE_SIZE));

				return true;
			}
		});
		setListAdapter(adapter);

		loaderManager.initLoader(0, null, this);
	}

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
    {
        final String input;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        /* Check if user wants to use internal scanner */
        if(prefs.getString(Constants.PREFS_KEY_QR_SCANNER, "").equals("internal"))
        {
            input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
        }
        else
        {
            IntentResult scanResult = IntentIntegratorSupportV4.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult != null)
                input = scanResult.getContents();
            else
                input = null;
        }

        if(input == null) return;
        Log.d("Litecoin", "SCAN RESULT:" + input);

        new StringInputParser(input)
        {
            @Override
            protected void bitcoinRequest(@Nonnull final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
            {
                // workaround for "IllegalStateException: Can not perform this action after onSaveInstanceState"
                handler.postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
                    }
                }, 500);
            }

            @Override
            protected void directTransaction(@Nonnull final Transaction transaction)
            {
                cannotClassify(input);
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs)
            {
                dialog(activity, null, R.string.address_book_options_scan_title, messageResId, messageArgs);
            }

            @Override
            protected void handlePrivateKey(@Nonnull ECKey key) {
                final Address address = new Address(Constants.NETWORK_PARAMETERS, key.getPubKeyHash());
                bitcoinRequest(address, null, null, null);
            }
        }.parse();
    }

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.sending_addresses_fragment_options, menu);

		final PackageManager pm = activity.getPackageManager();
		menu.findItem(R.id.sending_addresses_options_scan).setVisible(
				pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.sending_addresses_options_paste:
				handlePasteClipboard();
				return true;

			case R.id.sending_addresses_options_scan:
				handleScan();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handlePasteClipboard()
	{
		if (clipboardManager.hasText())
		{
			final String input = clipboardManager.getText().toString().trim();

			new StringInputParser(input)
			{
				@Override
				protected void bitcoinRequest(@Nonnull final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
				{
					EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
				}

				@Override
				protected void directTransaction(@Nonnull final Transaction transaction)
				{
					cannotClassify(input);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(activity, null, R.string.address_book_options_paste_from_clipboard_title, messageResId, messageArgs);
				}

                @Override
                protected void handlePrivateKey(@Nonnull ECKey key) {
                    final Address address = new Address(Constants.NETWORK_PARAMETERS, key.getPubKeyHash());
                    bitcoinRequest(address, null, null, null);
                }
			}.parse();
		}
		else
		{
			activity.toast(R.string.address_book_options_copy_from_clipboard_msg_empty);
		}
	}

	private void handleScan()
	{
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if(prefs.getString(Constants.PREFS_KEY_QR_SCANNER, "").equals("internal")) {
            startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
        } else {
            IntentIntegratorSupportV4 integrator = new IntentIntegratorSupportV4(this);
            integrator.initiateScan();
        }
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
				inflater.inflate(R.menu.sending_addresses_context, menu);

				return true;
			}

			@Override
			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				final String label = getLabel(position);
				mode.setTitle(label);

				return true;
			}

			@Override
			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.sending_addresses_context_send:
						handleSend(getAddress(position));

						mode.finish();
						return true;

					case R.id.sending_addresses_context_edit:
						EditAddressBookEntryFragment.edit(getFragmentManager(), getAddress(position));

						mode.finish();
						return true;

					case R.id.sending_addresses_context_remove:
						handleRemove(getAddress(position));

						mode.finish();
						return true;

					case R.id.sending_addresses_context_show_qr:
						handleShowQr(getAddress(position));

						mode.finish();
						return true;

					case R.id.sending_addresses_context_copy_to_clipboard:
						handleCopyToClipboard(getAddress(position));

						mode.finish();
						return true;
				}

				return false;
			}

			@Override
			public void onDestroyActionMode(final ActionMode mode)
			{
			}

			private String getAddress(final int position)
			{
				final Cursor cursor = (Cursor) adapter.getItem(position);
				return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
			}

			private String getLabel(final int position)
			{
				final Cursor cursor = (Cursor) adapter.getItem(position);
				return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
			}
		});
	}

	private void handleSend(final String address)
	{
		SendCoinsActivity.start(activity, address, null, null, null);
	}

	private void handleRemove(final String address)
	{
		final Uri uri = AddressBookProvider.contentUri(activity.getPackageName()).buildUpon().appendPath(address).build();
		activity.getContentResolver().delete(uri, null, null);
	}

	private void handleShowQr(final String address)
	{
		final String uri = BitcoinURI.convertToBitcoinURI(Constants.NETWORK_PARAMETERS, address, null, null, null);
		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		BitmapFragment.show(getFragmentManager(), Qr.bitmap(uri, size));
	}

	private void handleCopyToClipboard(final String address)
	{
		clipboardManager.setText("address", address);
		activity.toast(R.string.wallet_address_fragment_clipboard_msg);
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		final Uri uri = AddressBookProvider.contentUri(activity.getPackageName());
		return new CursorLoader(activity, uri, null, AddressBookProvider.SELECTION_NOTIN,
				new String[] { walletAddressesSelection != null ? walletAddressesSelection : "" }, AddressBookProvider.KEY_LABEL
						+ " COLLATE LOCALIZED ASC");
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		adapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader)
	{
		adapter.swapCursor(null);
	}

	public void setWalletAddresses(@Nonnull final ArrayList<Address> addresses)
	{
		final StringBuilder builder = new StringBuilder();
		for (final Address address : addresses)
			builder.append(address.toString()).append(",");
		if (addresses.size() > 0)
			builder.setLength(builder.length() - 1);

		walletAddressesSelection = builder.toString();
	}
}
