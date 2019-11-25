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

package de.schildbach.wallet.ui.backup;

import android.app.Application;
import android.net.Uri;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.WalletBalanceLiveData;
import de.schildbach.wallet.ui.Event;

/**
 * @author Andreas Schildbach
 */
public class RestoreWalletViewModel extends AndroidViewModel {
    private final WalletApplication application;
    public final WalletBalanceLiveData balance;
    public final MutableLiveData<Uri> backupUri = new MutableLiveData<>();
    public final MutableLiveData<String> displayName = new MutableLiveData<>();
    public final MutableLiveData<Event<Boolean>> showSuccessDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<String>> showFailureDialog = new MutableLiveData<>();

    public RestoreWalletViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.balance = new WalletBalanceLiveData(this.application);
    }
}
