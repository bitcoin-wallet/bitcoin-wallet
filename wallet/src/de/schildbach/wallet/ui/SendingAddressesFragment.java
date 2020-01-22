/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.AddressBookDao;
import de.schildbach.wallet.data.AddressBookEntry;
import de.schildbach.wallet.data.AppDatabase;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.util.WholeStringBuilder;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;

/**
 * @author Andreas Schildbach
 */
public final class SendingAddressesFragment extends FancyListFragment {
    private AbstractWalletActivity activity;
    private AddressBookDao addressBookDao;
    private final Handler handler = new Handler();

    private ArrayAdapter<AddressBookEntry> adapter;

    private SendingAddressesViewModel viewModel;

    private static final int REQUEST_CODE_SCAN = 0;

    private static final Logger log = LoggerFactory.getLogger(SendingAddressesFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.addressBookDao = AppDatabase.getDatabase(context).addressBookDao();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        viewModel = new ViewModelProvider(this).get(SendingAddressesViewModel.class);
        viewModel.wallet.observe(this, wallet -> activity.invalidateOptionsMenu());
        viewModel.addressesToExclude.observe(this, addressesToExclude -> {
            viewModel.addressBook = addressBookDao.getAllExcept(addressesToExclude);
            viewModel.addressBook.observe(SendingAddressesFragment.this, addressBook -> {
                adapter.setNotifyOnChange(false);
                adapter.clear();
                adapter.addAll(addressBook);
                adapter.notifyDataSetChanged();
                setEmptyText(WholeStringBuilder.bold(getString(R.string.address_book_empty_text)));
            });
        });
        viewModel.clip.observe(this, clipData -> activity.invalidateOptionsMenu());
        viewModel.showBitmapDialog.observe(this, new Event.Observer<Bitmap>() {
            @Override
            public void onEvent(final Bitmap bitmap) {
                BitmapFragment.show(getParentFragmentManager(), bitmap);
            }
        });
        viewModel.showEditAddressBookEntryDialog.observe(this, new Event.Observer<Address>() {
            @Override
            public void onEvent(final Address address) {
                EditAddressBookEntryFragment.edit(getParentFragmentManager(), address);
            }
        });

        adapter = new ArrayAdapter<AddressBookEntry>(activity, 0) {
            @Override
            public View getView(final int position, View view, final ViewGroup parent) {
                if (view == null)
                    view = LayoutInflater.from(activity).inflate(R.layout.address_book_row, parent, false);
                final AddressBookEntry entry = getItem(position);
                ((TextView) view.findViewById(R.id.address_book_row_label)).setText(entry.getLabel());
                ((TextView) view.findViewById(R.id.address_book_row_address)).setText(WalletUtils.formatHash(
                        entry.getAddress(), Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                return view;
            }
        };
        setListAdapter(adapter);
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
                    handler.postDelayed(() -> {
                        if (paymentIntent.hasAddress()) {
                            final Wallet wallet = viewModel.wallet.getValue();
                            final Address address = paymentIntent.getAddress();
                            if (!wallet.isAddressMine(address))
                                viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
                            else
                                dialog(activity, null, R.string.address_book_options_scan_title,
                                        R.string.address_book_options_scan_own_address);
                        } else {
                            dialog(activity, null, R.string.address_book_options_scan_title,
                                    R.string.address_book_options_scan_invalid);
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
        menu.findItem(R.id.sending_addresses_options_paste)
                .setEnabled(viewModel.wallet.getValue() != null && getAddressFromPrimaryClip() != null);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.sending_addresses_options_paste) {
            handlePasteClipboard();
            return true;
        } else if (itemId == R.id.sending_addresses_options_scan) {
            ScanActivity.startForResult(this, activity, REQUEST_CODE_SCAN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handlePasteClipboard() {
        final Wallet wallet = viewModel.wallet.getValue();
        final Address address = getAddressFromPrimaryClip();
        if (address == null) {
            final DialogBuilder dialog = new DialogBuilder(activity);
            dialog.setTitle(R.string.address_book_options_paste_from_clipboard_title);
            dialog.setMessage(R.string.address_book_options_paste_from_clipboard_invalid);
            dialog.singleDismissButton(null);
            dialog.show();
        } else if (!wallet.isAddressMine(address)) {
            viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
        } else {
            final DialogBuilder dialog = new DialogBuilder(activity);
            dialog.setTitle(R.string.address_book_options_paste_from_clipboard_title);
            dialog.setMessage(R.string.address_book_options_paste_from_clipboard_own_address);
            dialog.singleDismissButton(null);
            dialog.show();
        }
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
                int itemId = item.getItemId();
                if (itemId == R.id.sending_addresses_context_send) {
                    handleSend(getAddress(position), getLabel(position));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_edit) {
                    final Address address = Address.fromString(Constants.NETWORK_PARAMETERS, getAddress(position));
                    viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_remove) {
                    handleRemove(getAddress(position));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_show_qr) {
                    final String uri = BitcoinURI.convertToBitcoinURI(Constants.NETWORK_PARAMETERS,
                            getAddress(position), null, getLabel(position), null);
                    viewModel.showBitmapDialog.setValue(new Event<>(Qr.bitmap(uri)));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_copy_to_clipboard) {
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
                return adapter.getItem(position).getAddress();
            }

            private String getLabel(final int position) {
                return adapter.getItem(position).getLabel();
            }
        });
    }

    private void handleSend(final String address, final String label) {
        SendCoinsActivity.start(activity, PaymentIntent.fromAddress(address, label));
    }

    private void handleRemove(final String address) {
        addressBookDao.delete(address);
    }

    private void handleCopyToClipboard(final String address) {
        viewModel.clip.setClipData(ClipData.newPlainText("Bitcoin address", address));
        log.info("sending address copied to clipboard: {}", address);
        new Toast(activity).toast(R.string.wallet_address_fragment_clipboard_msg);
    }

    private Address getAddressFromPrimaryClip() {
        final ClipData clip = viewModel.clip.getValue();
        if (clip == null)
            return null;
        final ClipDescription clipDescription = clip.getDescription();

        if (clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            final CharSequence clipText = clip.getItemAt(0).getText();
            if (clipText == null)
                return null;

            try {
                return Address.fromString(Constants.NETWORK_PARAMETERS, clipText.toString().trim());
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
