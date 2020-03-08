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

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import org.bitcoinj.core.Address;

/**
 * @author Andreas Schildbach
 */
public class AddressBookViewModel extends ViewModel {
    public final MutableLiveData<Address> selectedAddress = new MutableLiveData<>();
    public final MutableLiveData<Event<Integer>> pageTo = new MutableLiveData<>();
    public final MutableLiveData<Event<Address>> showEditAddressBookEntryDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showScanOwnAddressDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showScanInvalidDialog = new MutableLiveData<>();
}
