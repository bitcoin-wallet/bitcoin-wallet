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
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.WalletLiveData;
import org.bitcoinj.core.Address;

/**
 * @author Andreas Schildbach
 */
public class AddressBookViewModel extends AndroidViewModel {
    private final WalletApplication application;
    public final WalletLiveData wallet;
    public final MutableLiveData<Address> selectedAddress = new MutableLiveData<>();
    public final MutableLiveData<Event<Integer>> pageTo = new MutableLiveData<>();
    public final MutableLiveData<Event<Address>> showEditAddressBookEntryDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showScanOwnAddressDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showScanInvalidDialog = new MutableLiveData<>();

    public AddressBookViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.wallet = new WalletLiveData(this.application);
    }
}
