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
import java.util.Locale;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookDao;
import de.schildbach.wallet.data.AddressBookEntry;
import de.schildbach.wallet.data.AppDatabase;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.util.WholeStringBuilder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressesFragment extends FancyListFragment {
    private WalletApplication application;
    private AbstractWalletActivity activity;
    private AddressBookDao addressBookDao;
    private ClipboardManager clipboardManager;

    private WalletAddressesAdapter adapter;

    private WalletAddressesViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(WalletAddressesFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.addressBookDao = AppDatabase.getDatabase(context).addressBookDao();
        this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        viewModel = ViewModelProviders.of(this).get(WalletAddressesViewModel.class);
        viewModel.issuedReceiveAddresses.observe(this, issuedReceiveAddresses -> adapter.replaceDerivedAddresses(issuedReceiveAddresses));
        viewModel.importedAddresses.observe(this, importedAddresses -> adapter.replaceRandomAddresses(importedAddresses));
        viewModel.wallet.observe(this, wallet -> adapter.setWallet(wallet));
        viewModel.addressBook.observe(this, addressBook -> adapter.setAddressBook(AddressBookEntry.asMap(addressBook)));
        viewModel.ownName.observe(this, ownName -> adapter.notifyDataSetChanged());
        viewModel.showBitmapDialog.observe(this, new Event.Observer<Bitmap>() {
            @Override
            public void onEvent(final Bitmap bitmap) {
                BitmapFragment.show(getFragmentManager(), bitmap);
            }
        });
        viewModel.showEditAddressBookEntryDialog.observe(this, new Event.Observer<Address>() {
            @Override
            public void onEvent(final Address address) {
                EditAddressBookEntryFragment.edit(getFragmentManager(), address);
            }
        });

        adapter = new WalletAddressesAdapter(activity);
        setListAdapter(adapter);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setEmptyText(WholeStringBuilder.bold(getString(R.string.address_book_empty_text)));
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.wallet_addresses_fragment_options, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onListItemClick(final ListView l, final View v, final int position, final long id) {
        activity.startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.wallet_addresses_context, menu);
                menu.findItem(R.id.wallet_addresses_context_browse).setVisible(Constants.ENABLE_BROWSE);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                final String address = getAddress(position).toString();
                final String label = addressBookDao.resolveLabel(address);
                mode.setTitle(label != null ? label
                        : WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, 0));
                return true;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                final Address address = getAddress(position);
                int itemId = item.getItemId();
                if (itemId == R.id.wallet_addresses_context_edit) {
                    viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.wallet_addresses_context_show_qr) {
                    final String label = viewModel.ownName.getValue();
                    final String uri;
                    if (address instanceof LegacyAddress || label != null)
                        uri = BitcoinURI.convertToBitcoinURI(address, null, label, null);
                    else
                        uri = address.toString().toUpperCase(Locale.US);
                    viewModel.showBitmapDialog.setValue(new Event<>(Qr.bitmap(uri)));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.wallet_addresses_context_copy_to_clipboard) {
                    handleCopyToClipboard(address);
                    mode.finish();
                    return true;
                } else if (itemId == R.id.wallet_addresses_context_browse) {
                    final Uri blockExplorerUri = application.getConfiguration().getBlockExplorer();
                    log.info("Viewing address {} on {}", address, blockExplorerUri);
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.withAppendedPath(blockExplorerUri, "address/" + address)));
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(final ActionMode mode) {
            }

            private Address getAddress(final int position) {
                return (Address) getListAdapter().getItem(position);
            }

            private void handleCopyToClipboard(final Address address) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Bitcoin address", address.toString()));
                log.info("wallet address copied to clipboard: {}", address);
                new Toast(activity).toast(R.string.wallet_address_fragment_clipboard_msg);
            }
        });
    }
}
