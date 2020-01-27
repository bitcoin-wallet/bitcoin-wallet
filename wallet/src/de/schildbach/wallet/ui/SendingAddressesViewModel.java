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
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.AbstractWalletLiveData;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.wallet.Wallet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public class SendingAddressesViewModel extends AndroidViewModel {
    private final WalletApplication application;
    public LiveData<List<AddressBookEntry>> addressBook;
    public final AddressesToExcludeLiveData addressesToExclude;
    public final MutableLiveData<Event<Bitmap>> showBitmapDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Address>> showEditAddressBookEntryDialog = new MutableLiveData<>();

    public SendingAddressesViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.addressesToExclude = new AddressesToExcludeLiveData(this.application);
    }

    public static class AddressesToExcludeLiveData extends AbstractWalletLiveData<Set<String>> {
        public AddressesToExcludeLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            loadAddressesToExclude();
        }

        private void loadAddressesToExclude() {
            final Wallet wallet = getWallet();
            AsyncTask.execute(() -> {
                final List<Address> derivedAddresses = wallet.getIssuedReceiveAddresses();
                final List<ECKey> randomKeys = wallet.getImportedKeys();

                final Set<String> addresses = new HashSet<>(derivedAddresses.size() + randomKeys.size());
                for (final Address address : derivedAddresses)
                    addresses.add(address.toString());
                for (final ECKey key : randomKeys)
                    addresses.add(LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, key).toString());
                postValue(addresses);
            });
        }
    }
}
