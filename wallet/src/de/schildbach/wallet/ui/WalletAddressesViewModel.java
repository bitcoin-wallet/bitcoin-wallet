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

import android.app.Application;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.AbstractWalletLiveData;
import de.schildbach.wallet.data.ConfigOwnNameLiveData;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressesViewModel extends AndroidViewModel {
    private final WalletApplication application;
    public final IssuedReceiveAddressesLiveData issuedReceiveAddresses;
    public final ImportedAddressesLiveData importedAddresses;
    public final LiveData<List<AddressBookEntry>> addressBook;
    public final ConfigOwnNameLiveData ownName;
    public final MutableLiveData<Event<Bitmap>> showBitmapDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Address>> showEditAddressBookEntryDialog = new MutableLiveData<>();

    public WalletAddressesViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.issuedReceiveAddresses = new IssuedReceiveAddressesLiveData(this.application);
        this.importedAddresses = new ImportedAddressesLiveData(this.application);
        this.addressBook = AddressBookDatabase.getDatabase(this.application).addressBookDao().getAll();
        this.ownName = new ConfigOwnNameLiveData(this.application);
    }

    public static class IssuedReceiveAddressesLiveData extends AbstractWalletLiveData<List<Address>>
            implements KeyChainEventListener {
        public IssuedReceiveAddressesLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            wallet.addKeyChainEventListener(Threading.SAME_THREAD, this);
            loadAddresses();
        }

        @Override
        protected void onWalletInactive(final Wallet wallet) {
            wallet.removeKeyChainEventListener(this);
        }

        @Override
        public void onKeysAdded(final List<ECKey> keys) {
            loadAddresses();
        }

        private void loadAddresses() {
            final Wallet wallet = getWallet();
            AsyncTask.execute(() -> {
                final List<Address> addresses = wallet.getIssuedReceiveAddresses();
                Collections.reverse(addresses);
                postValue(addresses);
            });
        }
    }

    public static class ImportedAddressesLiveData extends AbstractWalletLiveData<List<Address>>
            implements KeyChainEventListener {
        public ImportedAddressesLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            wallet.addKeyChainEventListener(Threading.SAME_THREAD, this);
            loadAddresses();
        }

        @Override
        protected void onWalletInactive(final Wallet wallet) {
            wallet.removeKeyChainEventListener(this);
        }

        @Override
        public void onKeysAdded(final List<ECKey> keys) {
            loadAddresses();
        }

        private void loadAddresses() {
            final Wallet wallet = getWallet();
            AsyncTask.execute(() -> {
                final List<ECKey> importedKeys = wallet.getImportedKeys();
                Collections.reverse(importedKeys);
                final List<Address> importedAddresses = new ArrayList<>(importedKeys.size());
                for (final ECKey key : importedKeys)
                    importedAddresses.add(LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, key));
                postValue(importedAddresses);
            });
        }
    }
}
