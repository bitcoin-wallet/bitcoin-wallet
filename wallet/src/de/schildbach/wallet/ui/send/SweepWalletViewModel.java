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

package de.schildbach.wallet.ui.send;

import org.bitcoinj.core.PrefixedChecksummedBytes;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.DynamicFeeLiveData;
import de.schildbach.wallet.ui.Event;

import android.app.Application;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

/**
 * @author Andreas Schildbach
 */
public class SweepWalletViewModel extends AndroidViewModel {
    public enum State {
        DECODE_KEY, // ask for password
        CONFIRM_SWEEP, // displays balance and asks for confirmation
        PREPARATION, SENDING, SENT, FAILED // sending states
    }

    private final WalletApplication application;
    private DynamicFeeLiveData dynamicFees;
    public final MutableLiveData<String> progress = new MutableLiveData<>();

    public State state = State.DECODE_KEY;
    public @Nullable PrefixedChecksummedBytes privateKeyToSweep = null;
    public @Nullable Wallet walletToSweep = null;
    public @Nullable Transaction sentTransaction = null;

    public final MutableLiveData<Event<CharSequence>> showParsePrivateKeyProblemDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<CharSequence>> showRequestWalletBalanceFailedDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<String>> showProblemSendingDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showInsufficientMoneyDialog = new MutableLiveData<>();

    public SweepWalletViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
    }

    public DynamicFeeLiveData getDynamicFees() {
        if (dynamicFees == null)
            dynamicFees = new DynamicFeeLiveData(application);
        return dynamicFees;
    }
}
