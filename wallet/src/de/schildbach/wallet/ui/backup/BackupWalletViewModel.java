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

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import org.bitcoinj.wallet.Wallet;

/**
 * @author Andreas Schildbach
 */
public class BackupWalletViewModel extends ViewModel {

    public enum State {
        INPUT, CRYPTING, BADPIN, EXPORTING
    }


    public final MutableLiveData<State> state = new MutableLiveData<>(State.INPUT);

    public final MutableLiveData<String> password = new MutableLiveData<>();
    public final MutableLiveData<String> spendingPIN = new MutableLiveData<>();
    public final MutableLiveData<Wallet> walletToBackup = new MutableLiveData<>();
}
