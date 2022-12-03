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

import android.app.Application;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.DynamicFeeLiveData;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.data.SelectedExchangeRateLiveData;
import de.schildbach.wallet.data.TransactionLiveData;
import de.schildbach.wallet.data.WalletBalanceLiveData;
import de.schildbach.wallet.ui.AddressAndLabel;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Andreas Schildbach
 */
public class SendCoinsViewModel extends AndroidViewModel {
    public enum State {
        REQUEST_PAYMENT_REQUEST, //
        INPUT, // asks for confirmation
        DECRYPTING, SIGNING, SENDING, SENT, FAILED // sending states
    }

    private final WalletApplication application;
    private final Wallet wallet;
    public final LiveData<List<AddressBookEntry>> addressBook;
    public final SelectedExchangeRateLiveData exchangeRate;
    public final DynamicFeeLiveData dynamicFees;
    public final MutableLiveData<FeeCategory> feeCategory = new MutableLiveData<>(FeeCategory.NORMAL);
    public final WalletBalanceLiveData balance;
    public final MutableLiveData<String> progress = new MutableLiveData<>();
    public final TransactionLiveData sentTransaction;

    @Nullable
    public State state = null;
    @Nullable
    public PaymentIntent paymentIntent = null;
    @Nullable
    public AddressAndLabel validatedAddress = null;
    @Nullable
    public final MutableLiveData<Coin> amount = new MutableLiveData<>(); // MAX_MONEY means available balance
    public final MediatorLiveData<Coin> visibleAmount = new MediatorLiveData<>();
    @Nullable
    public Boolean directPaymentAck = null;
    public MutableLiveData<Transaction> dryrunTransaction = new MutableLiveData<>();
    public MutableLiveData<Exception> dryrunException = new MutableLiveData<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            new ContextPropagatingThreadFactory("send"));

    public SendCoinsViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.wallet = this.application.getWallet();
        this.addressBook = AddressBookDatabase.getDatabase(this.application).addressBookDao().getAll();
        this.exchangeRate = new SelectedExchangeRateLiveData(this.application);
        this.dynamicFees = new DynamicFeeLiveData(this.application);
        this.balance = new WalletBalanceLiveData(this.application, BalanceType.AVAILABLE);
        this.sentTransaction = new TransactionLiveData(this.application);
        this.visibleAmount.addSource(amount, amount -> amountOrBalanceChanged());
        this.visibleAmount.addSource(balance, balance -> amountOrBalanceChanged());

        dynamicFees.observeForever(dynamicFees -> maybeDryrun());
        feeCategory.observeForever(feeCategory -> maybeDryrun());
    }

    private void amountOrBalanceChanged() {
        final Coin amount = this.amount.getValue();
        if (amount != null && amount.equals(Constants.NETWORK_PARAMETERS.getMaxMoney()))
            visibleAmount.setValue(this.balance.getValue());
        else
            visibleAmount.setValue(amount);
    }

    public void maybeDryrun() {
        final Map<FeeCategory, Coin> fees = this.dynamicFees.getValue();
        final Coin amount = this.amount.getValue();
        dryrunTransaction.setValue(null);
        dryrunException.setValue(null);
        if (state == State.INPUT && amount != null && fees != null) {
            final Address dummy = wallet.currentReceiveAddress(); // won't be used, tx is never committed
            final SendRequest sendRequest = paymentIntent.mergeWithEditedValues(amount, dummy).toSendRequest();
            sendRequest.signInputs = false;
            sendRequest.emptyWallet =
                    paymentIntent.mayEditAmount() && amount.equals(Constants.NETWORK_PARAMETERS.getMaxMoney());
            sendRequest.feePerKb = fees.get(feeCategory.getValue());
            executor.execute(() -> {
                try {
                    wallet.completeTx(sendRequest);
                    dryrunTransaction.postValue(sendRequest.tx);
                } catch (final Exception x) {
                    dryrunException.postValue(x);
                }
            });
        }
    }
}
