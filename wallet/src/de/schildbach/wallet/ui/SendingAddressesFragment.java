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
import de.schildbach.wallet.addressbook.AddressBookDao;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.Toast;
import org.bitcoinj.core.Address;
import org.bitcoinj.uri.BitcoinURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public final class SendingAddressesFragment extends Fragment implements AddressBookAdapter.OnClickListener,
        AddressBookAdapter.ContextMenuCallback {
    private AbstractWalletActivity activity;
    private FragmentManager fragmentManager;
    private AddressBookDao addressBookDao;
    private ClipboardManager clipboardManager;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private AddressBookAdapter adapter;

    private AddressBookViewModel activityViewModel;
    private SendingAddressesViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(SendingAddressesFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.addressBookDao = AddressBookDatabase.getDatabase(context).addressBookDao();
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.fragmentManager = getChildFragmentManager();

        activityViewModel = new ViewModelProvider(activity).get(AddressBookViewModel.class);
        activityViewModel.selectedAddress.observe(this, address -> {
            adapter.setSelectedAddress(address);
            final int position = adapter.positionOf(address);
            if (position != RecyclerView.NO_POSITION) {
                activityViewModel.pageTo.setValue(new Event(AddressBookActivity.POSITION_SENDING_ADDRESSES));
                recyclerView.smoothScrollToPosition(position);
            }
        });
        viewModel = new ViewModelProvider(this).get(SendingAddressesViewModel.class);
        viewModel.addressesToExclude.observe(this, addressesToExclude -> {
            viewModel.addressBook = addressBookDao.getAllExcept(addressesToExclude);
            viewModel.addressBook.observe(SendingAddressesFragment.this, addressBook -> {
                viewGroup.setDisplayedChild(addressBook.isEmpty() ? 1 : 2);
                adapter.submitList(AddressBookAdapter.buildListItems(activity, addressBook));
            });
        });
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
        final View view = inflater.inflate(R.layout.sending_addresses_fragment, container, false);
        viewGroup = view.findViewById(R.id.sending_addresses_list_group);
        recyclerView = view.findViewById(R.id.sending_addresses_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onAddressClick(final View view, final Address address, final String label) {
        activityViewModel.selectedAddress.setValue(address);
    }

    @Override
    public void onInflateAddressContextMenu(final MenuInflater inflater, final Menu menu) {
        inflater.inflate(R.menu.sending_addresses_context, menu);
    }

    @Override
    public boolean onClickAddressContextMenuItem(final MenuItem item, final Address address, final String label) {
        int itemId = item.getItemId();
        if (itemId == R.id.sending_addresses_context_send) {
            SendCoinsActivity.start(activity, PaymentIntent.fromAddress(address, label));
            return true;
        } else if (itemId == R.id.sending_addresses_context_edit) {
            viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
            return true;
        } else if (itemId == R.id.sending_addresses_context_remove) {
            addressBookDao.delete(address.toString());
            return true;
        } else if (itemId == R.id.sending_addresses_context_show_qr) {
            final String uri = BitcoinURI.convertToBitcoinURI(Constants.NETWORK_PARAMETERS,
                    address.toString(), null, label, null);
            viewModel.showBitmapDialog.setValue(new Event<>(Qr.bitmap(uri)));
            return true;
        } else if (itemId == R.id.sending_addresses_context_copy_to_clipboard) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Bitcoin address", address.toString()));
            log.info("sending address copied to clipboard: {}", address);
            new Toast(activity).toast(R.string.wallet_address_fragment_clipboard_msg);
            return true;
        } else {
            return false;
        }
    }
}
