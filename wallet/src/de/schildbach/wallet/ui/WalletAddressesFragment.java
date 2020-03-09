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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewAnimator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDao;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.Toast;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressesFragment extends Fragment implements AddressBookAdapter.OnClickListener,
        AddressBookAdapter.ContextMenuCallback {
    private WalletApplication application;
    private AbstractWalletActivity activity;
    private FragmentManager fragmentManager;
    private AddressBookDao addressBookDao;
    private ClipboardManager clipboardManager;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private AddressBookAdapter adapter;

    private AbstractWalletActivityViewModel walletActivityViewModel;
    private AddressBookViewModel activityViewModel;
    private WalletAddressesViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(WalletAddressesFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.addressBookDao = AddressBookDatabase.getDatabase(context).addressBookDao();
        this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.fragmentManager = getChildFragmentManager();

        walletActivityViewModel = new ViewModelProvider(activity).get(AbstractWalletActivityViewModel.class);
        walletActivityViewModel.wallet.observe(this, wallet -> maybeSubmitList());
        activityViewModel = new ViewModelProvider(activity).get(AddressBookViewModel.class);
        activityViewModel.selectedAddress.observe(this, address -> {
            adapter.setSelectedAddress(address);
            final int position = adapter.positionOf(address);
            if (position != RecyclerView.NO_POSITION) {
                activityViewModel.pageTo.setValue(new Event(AddressBookActivity.POSITION_WALLET_ADDRESSES));
                recyclerView.smoothScrollToPosition(position);
            }
        });
        viewModel = new ViewModelProvider(this).get(WalletAddressesViewModel.class);
        viewModel.issuedReceiveAddresses.observe(this, issuedReceiveAddresses -> maybeSubmitList());
        viewModel.importedAddresses.observe(this, importedAddresses -> maybeSubmitList());
        viewModel.addressBook.observe(this, addressBook -> maybeSubmitList());
        viewModel.ownName.observe(this, ownName -> {});
        viewModel.showBitmapDialog.observe(this, new Event.Observer<Bitmap>() {
            @Override
            protected void onEvent(final Bitmap bitmap) {
                BitmapFragment.show(fragmentManager, bitmap);
            }
        });
        viewModel.showEditAddressBookEntryDialog.observe(this, new Event.Observer<Address>() {
            @Override
            protected void onEvent(final Address address) {
                EditAddressBookEntryFragment.edit(fragmentManager, address);
            }
        });

        adapter = new AddressBookAdapter(activity, this, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.wallet_addresses_fragment, container, false);
        viewGroup = view.findViewById(R.id.wallet_addresses_list_group);
        recyclerView = view.findViewById(R.id.wallet_addresses_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        return view;
    }

    private void maybeSubmitList() {
        final List<Address> derivedAddresses = viewModel.issuedReceiveAddresses.getValue();
        final List<Address> randomAddresses = viewModel.importedAddresses.getValue();
        final Wallet wallet = walletActivityViewModel.wallet.getValue();
        if (derivedAddresses != null && randomAddresses != null) {
            viewGroup.setDisplayedChild(1);
            adapter.submitList(AddressBookAdapter.buildListItems(activity, derivedAddresses, randomAddresses, wallet,
                    AddressBookEntry.asMap(viewModel.addressBook.getValue())));
        }
    }

    @Override
    public void onAddressClick(final View view, final Address address, final String label) {
        activityViewModel.selectedAddress.setValue(address);
    }

    @Override
    public void onInflateAddressContextMenu(final MenuInflater inflater, final Menu menu) {
        inflater.inflate(R.menu.wallet_addresses_context, menu);
        menu.findItem(R.id.wallet_addresses_context_browse).setVisible(Constants.ENABLE_BROWSE);
    }

    @Override
    public boolean onClickAddressContextMenuItem(final MenuItem item, final Address address, final String label) {
        int itemId = item.getItemId();
        if (itemId == R.id.wallet_addresses_context_edit) {
            viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
            return true;
        } else if (itemId == R.id.wallet_addresses_context_show_qr) {
            final String ownName = viewModel.ownName.getValue();
            final String uri;
            if (address instanceof LegacyAddress || ownName != null)
                uri = BitcoinURI.convertToBitcoinURI(address, null, ownName, null);
            else
                uri = address.toString().toUpperCase(Locale.US);
            viewModel.showBitmapDialog.setValue(new Event<>(Qr.bitmap(uri)));
            return true;
        } else if (itemId == R.id.wallet_addresses_context_copy_to_clipboard) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Bitcoin address", address.toString()));
            log.info("wallet address copied to clipboard: {}", address);
            new Toast(activity).toast(R.string.wallet_address_fragment_clipboard_msg);
            return true;
        } else if (itemId == R.id.wallet_addresses_context_browse) {
            final Uri blockExplorerUri = application.getConfiguration().getBlockExplorer();
            log.info("Viewing address {} on {}", address, blockExplorerUri);
            activity.startExternalDocument(Uri.withAppendedPath(blockExplorerUri, "address/" + address));
            return true;
        } else {
            return false;
        }
    }
}
