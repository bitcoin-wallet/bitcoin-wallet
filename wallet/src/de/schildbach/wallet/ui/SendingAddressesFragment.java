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
import android.view.ActionMode;
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
public final class SendingAddressesFragment extends Fragment implements AddressBookAdapter.OnClickListener {
    private AbstractWalletActivity activity;
    private AddressBookDao addressBookDao;
    private ClipboardManager clipboardManager;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private AddressBookAdapter adapter;

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

        adapter = new AddressBookAdapter(activity, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.sending_addresses_fragment, container, false);
        viewGroup = view.findViewById(R.id.sending_addresses_list_group);
        recyclerView = view.findViewById(R.id.sending_addresses_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));
        return view;
    }

    @Override
    public void onAddressClick(final View view, final Address address, final String label) {
        activity.startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.sending_addresses_context, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                mode.setTitle(label);
                return true;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.sending_addresses_context_send) {
                    handleSend(address, label);
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_edit) {
                    viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_remove) {
                    handleRemove(address);
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_show_qr) {
                    final String uri = BitcoinURI.convertToBitcoinURI(Constants.NETWORK_PARAMETERS,
                            address.toString(), null, label, null);
                    viewModel.showBitmapDialog.setValue(new Event<>(Qr.bitmap(uri)));
                    mode.finish();
                    return true;
                } else if (itemId == R.id.sending_addresses_context_copy_to_clipboard) {
                    handleCopyToClipboard(address);
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(final ActionMode mode) {
            }
        });
    }

    private void handleSend(final Address address, final String label) {
        SendCoinsActivity.start(activity, PaymentIntent.fromAddress(address, label));
    }

    private void handleRemove(final Address address) {
        addressBookDao.delete(address.toString());
    }

    private void handleCopyToClipboard(final Address address) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Bitcoin address", address.toString()));
        log.info("sending address copied to clipboard: {}", address);
        new Toast(activity).toast(R.string.wallet_address_fragment_clipboard_msg);
    }
}
