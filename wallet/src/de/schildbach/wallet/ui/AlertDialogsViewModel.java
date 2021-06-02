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

/**
 * @author Andreas Schildbach
 */
public class AlertDialogsViewModel extends ViewModel {
    public final MutableLiveData<Event<Long>> showTimeskewAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showVersionAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<String>> showInsecureDeviceAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<String>> showInsecureBluetoothAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showLowStorageAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<String>> showSettingsFailedDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showTooMuchBalanceAlertDialog = new MutableLiveData<>();
}
