/*
 * Copyright 2011-2015 the original author or authors.
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
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.util.WholeStringBuilder;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ClipboardManager.OnPrimaryClipChangedListener;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.CursorLoader;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public final class SendingAddressesFragment extends FancyListFragment {
    private AbstractWalletActivity activity;
    private Wallet wallet;
    private final Handler handler = new Handler();

    private SimpleCursorAdapter adapter;

    private ViewModel viewModel;

    private static final int REQUEST_CODE_SCAN = 0;

    private static final Logger log = LoggerFactory.getLogger(SendingAddressesFragment.class);

    public static class ViewModel extends AndroidViewModel {
        private final WalletApplication application;
        private AddressBookLiveData addressBook;
        private ClipLiveData clip;

        public ViewModel(final Application application) {
            super(application);
            this.application = (WalletApplication) application;
        }

        public AddressBookLiveData getAddressBook() {
            if (addressBook == null)
                addressBook = new AddressBookLiveData(application);
            return addressBook;
        }

        public ClipLiveData getClip() {
            if (clip == null)
                clip = new ClipLiveData(application);
            return clip;
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        final WalletApplication application = activity.getWalletApplication();
        this.wallet = application.getWallet();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        viewModel.getAddressBook().observe(this, new Observer<Cursor>() {
            @Override
            public void onChanged(final Cursor addressBook) {
                adapter.swapCursor(addressBook);
                setEmptyText(WholeStringBuilder.bold(getString(R.string.address_book_empty_text)));
            }
        });
        viewModel.getClip().observe(this, new Observer<ClipData>() {
            @Override
            public void onChanged(final ClipData clipData) {
                activity.invalidateOptionsMenu();
            }
        });

        adapter = new SimpleCursorAdapter(activity, R.layout.address_book_row, null,
                new String[] { AddressBookProvider.KEY_LABEL, AddressBookProvider.KEY_ADDRESS },
                new int[] { R.id.address_book_row_label, R.id.address_book_row_address }, 0);
        adapter.setViewBinder(new ViewBinder() {
            @Override
            public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
                if (!AddressBookProvider.KEY_ADDRESS.equals(cursor.getColumnName(columnIndex)))
                    return false;

                ((TextView) view).setText(WalletUtils.formatHash(cursor.getString(columnIndex),
                        Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));

                return true;
            }
        });
        setListAdapter(adapter);

        final List<ECKey> derivedKeys = wallet.getIssuedReceiveKeys();
        Collections.sort(derivedKeys, DeterministicKey.CHILDNUM_ORDER);
        final List<ECKey> randomKeys = wallet.getImportedKeys();

        final StringBuilder builder = new StringBuilder();
        for (final ECKey key : Iterables.concat(derivedKeys, randomKeys)) {
            final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
            builder.append(address.toBase58()).append(",");
        }
        if (builder.length() > 0)
            builder.setLength(builder.length() - 1);

        viewModel.getAddressBook().setWalletAddressesSelection(builder.toString());
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK) {
            final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

            new StringInputParser(input) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                    // workaround for "IllegalStateException: Can not perform this action after
                    // onSaveInstanceState"
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (paymentIntent.hasAddress()) {
                                final Address address = paymentIntent.getAddress();
                                if (!wallet.isPubKeyHashMine(address.getHash160()))
                                    EditAddressBookEntryFragment.edit(getFragmentManager(), address);
                                else
                                    dialog(activity, null, R.string.address_book_options_scan_title,
                                            R.string.address_book_options_scan_own_address);
                            } else {
                                dialog(activity, null, R.string.address_book_options_scan_title,
                                        R.string.address_book_options_scan_invalid);
                            }
                        }
                    }, 500);
                }

                @Override
                protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                    cannotClassify(input);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(activity, null, R.string.address_book_options_scan_title, messageResId, messageArgs);
                }
            }.parse();
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.sending_addresses_fragment_options, menu);

        final PackageManager pm = activity.getPackageManager();
        menu.findItem(R.id.sending_addresses_options_scan).setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        menu.findItem(R.id.sending_addresses_options_paste).setEnabled(getAddressFromPrimaryClip() != null);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.sending_addresses_options_paste:
            handlePasteClipboard();
            return true;

        case R.id.sending_addresses_options_scan:
            handleScan();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void handlePasteClipboard() {
        final Address address = getAddressFromPrimaryClip();
        if (address == null) {
            final DialogBuilder dialog = new DialogBuilder(activity);
            dialog.setTitle(R.string.address_book_options_paste_from_clipboard_title);
            dialog.setMessage(R.string.address_book_options_paste_from_clipboard_invalid);
            dialog.singleDismissButton(null);
            dialog.show();
        } else if (!wallet.isPubKeyHashMine(address.getHash160())) {
            EditAddressBookEntryFragment.edit(getFragmentManager(), address);
        } else {
            final DialogBuilder dialog = new DialogBuilder(activity);
            dialog.setTitle(R.string.address_book_options_paste_from_clipboard_title);
            dialog.setMessage(R.string.address_book_options_paste_from_clipboard_own_address);
            dialog.singleDismissButton(null);
            dialog.show();
        }
    }

    private void handleScan() {
        startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
    }

    @Override
    public void onListItemClick(final ListView l, final View v, final int position, final long id) {
        activity.startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.sending_addresses_context, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                final String label = getLabel(position);
                mode.setTitle(label);

                return true;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                switch (item.getItemId()) {
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
                    handleShowQr(getAddress(position), getLabel(position));

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
            public void onDestroyActionMode(final ActionMode mode) {
            }

            private String getAddress(final int position) {
                final Cursor cursor = (Cursor) adapter.getItem(position);
                return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
            }

            private String getLabel(final int position) {
                final Cursor cursor = (Cursor) adapter.getItem(position);
                return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
            }
        });
    }

    private void handleSend(final String address) {
        SendCoinsActivity.start(activity, PaymentIntent.fromAddress(address, null));
    }

    private void handleRemove(final String address) {
        final Uri uri = AddressBookProvider.contentUri(activity.getPackageName()).buildUpon().appendPath(address)
                .build();
        activity.getContentResolver().delete(uri, null, null);
    }

    private void handleShowQr(final String address, final String label) {
        final String uri = BitcoinURI.convertToBitcoinURI(Constants.NETWORK_PARAMETERS, address, null, label, null);
        BitmapFragment.show(getFragmentManager(), Qr.bitmap(uri));
    }

    private void handleCopyToClipboard(final String address) {
        viewModel.getClip().setClipData(ClipData.newPlainText("Bitcoin address", address));
        log.info("sending address copied to clipboard: {}", address.toString());
        new Toast(activity).toast(R.string.wallet_address_fragment_clipboard_msg);
    }

    private static class AddressBookLiveData extends LiveData<Cursor> {
        private final CursorLoader loader;

        public AddressBookLiveData(final WalletApplication application) {
            final Uri uri = AddressBookProvider.contentUri(application.getPackageName());
            this.loader = new CursorLoader(application, uri, null, AddressBookProvider.SELECTION_NOTIN,
                    new String[] { "" }, AddressBookProvider.KEY_LABEL + " COLLATE LOCALIZED ASC") {
                @Override
                public void deliverResult(final Cursor cursor) {
                    setValue(cursor);
                }
            };
        }

        @Override
        protected void onActive() {
            loader.startLoading();
        }

        @Override
        protected void onInactive() {
            loader.stopLoading();
        }

        public void setWalletAddressesSelection(final String walletAddressesSelection) {
            loader.setSelectionArgs(new String[] { walletAddressesSelection != null ? walletAddressesSelection : "" });
            loader.forceLoad();
        }
    }

    private static class ClipLiveData extends LiveData<ClipData> implements OnPrimaryClipChangedListener {
        private final ClipboardManager clipboardManager;

        public ClipLiveData(final WalletApplication application) {
            clipboardManager = (ClipboardManager) application.getSystemService(Context.CLIPBOARD_SERVICE);
        }

        @Override
        protected void onActive() {
            clipboardManager.addPrimaryClipChangedListener(this);
            onPrimaryClipChanged();
        }

        @Override
        protected void onInactive() {
            clipboardManager.removePrimaryClipChangedListener(this);
        }

        @Override
        public void onPrimaryClipChanged() {
            setValue(clipboardManager.getPrimaryClip());
        }

        public void setClipData(final ClipData clipData) {
            clipboardManager.setPrimaryClip(clipData);
        }
    }

    private Address getAddressFromPrimaryClip() {
        final ClipData clip = viewModel.getClip().getValue();
        if (clip == null)
            return null;
        final ClipDescription clipDescription = clip.getDescription();

        if (clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            final CharSequence clipText = clip.getItemAt(0).getText();
            if (clipText == null)
                return null;

            try {
                return Address.fromBase58(Constants.NETWORK_PARAMETERS, clipText.toString().trim());
            } catch (final AddressFormatException x) {
                return null;
            }
        } else if (clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) {
            final Uri clipUri = clip.getItemAt(0).getUri();
            if (clipUri == null)
                return null;
            try {
                return new BitcoinURI(clipUri.toString()).getAddress();
            } catch (final BitcoinURIParseException x) {
                return null;
            }
        } else {
            return null;
        }
    }
}
