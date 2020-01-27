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
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.AbstractWalletLiveData;
import de.schildbach.wallet.data.ConfigFormatLiveData;
import de.schildbach.wallet.data.WalletLiveData;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.bitcoinj.wallet.listeners.WalletReorganizeEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public class WalletTransactionsViewModel extends AndroidViewModel {
    public enum Direction {
        RECEIVED, SENT
    }

    private final WalletApplication application;
    public final TransactionsLiveData transactions;
    public final WalletLiveData wallet;
    private final TransactionsConfidenceLiveData transactionsConfidence;
    private final LiveData<List<AddressBookEntry>> addressBook;
    private final ConfigFormatLiveData configFormat;
    public final MutableLiveData<Direction> direction = new MutableLiveData<>();
    public final MutableLiveData<Sha256Hash> selectedTransaction = new MutableLiveData<>();
    public final MutableLiveData<TransactionsAdapter.WarningType> warning = new MutableLiveData<>();
    public final MediatorLiveData<List<TransactionsAdapter.ListItem>> list = new MediatorLiveData<>();
    public final MutableLiveData<Event<Bitmap>> showBitmapDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Address>> showEditAddressBookEntryDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Sha256Hash>> showReportIssueDialog = new MutableLiveData<>();

    public WalletTransactionsViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.transactions = new TransactionsLiveData(this.application);
        this.wallet = new WalletLiveData(this.application);
        this.transactionsConfidence = new TransactionsConfidenceLiveData(this.application);
        this.addressBook = AddressBookDatabase.getDatabase(this.application).addressBookDao().getAll();
        this.configFormat = new ConfigFormatLiveData(this.application);
        this.list.addSource(transactions, transactions -> maybePostList());
        this.list.addSource(wallet, wallet -> maybePostList());
        this.list.addSource(transactionsConfidence, v -> maybePostList());
        this.list.addSource(addressBook, addressBook -> maybePostList());
        this.list.addSource(direction, direction -> maybePostList());
        this.list.addSource(configFormat, format -> maybePostList());
    }

    public void setDirection(final Direction direction) {
        this.direction.setValue(direction);
    }

    public void setWarning(final TransactionsAdapter.WarningType warning) {
        this.warning.setValue(warning);
    }

    private void maybePostList() {
        AsyncTask.execute(() -> {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
            final Set<Transaction> transactions = WalletTransactionsViewModel.this.transactions.getValue();
            final MonetaryFormat format = configFormat.getValue();
            final Map<String, AddressBookEntry> addressBook = AddressBookEntry
                    .asMap(WalletTransactionsViewModel.this.addressBook.getValue());
            if (transactions != null && format != null && addressBook != null) {
                final List<Transaction> filteredTransactions = new ArrayList<>(transactions.size());
                final Wallet wallet = application.getWallet();
                final Direction direction = WalletTransactionsViewModel.this.direction.getValue();
                for (final Transaction tx : transactions) {
                    final boolean sent = tx.getValue(wallet).signum() < 0;
                    final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;
                    if ((direction == Direction.RECEIVED && !sent && !isInternal) || direction == null
                            || (direction == Direction.SENT && sent && !isInternal))
                        filteredTransactions.add(tx);
                }

                Collections.sort(filteredTransactions, TRANSACTION_COMPARATOR);

                list.postValue(TransactionsAdapter.buildListItems(application, filteredTransactions,
                        warning.getValue(), wallet, addressBook, format, application.maxConnectedPeers()));
            }
        });
    }

    private static final Comparator<Transaction> TRANSACTION_COMPARATOR = (tx1, tx2) -> {
        final boolean pending1 = tx1.getConfidence().getConfidenceType() == ConfidenceType.PENDING;
        final boolean pending2 = tx2.getConfidence().getConfidenceType() == ConfidenceType.PENDING;
        if (pending1 != pending2)
            return pending1 ? -1 : 1;

        final Date updateTime1 = tx1.getUpdateTime();
        final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
        final Date updateTime2 = tx2.getUpdateTime();
        final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;
        if (time1 != time2)
            return time1 > time2 ? -1 : 1;

        return tx1.getTxId().compareTo(tx2.getTxId());
    };

    public static class TransactionsLiveData extends AbstractWalletLiveData<Set<Transaction>> {
        private static final long THROTTLE_MS = 1000;

        public TransactionsLiveData(final WalletApplication application) {
            super(application, THROTTLE_MS);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            addWalletListener(wallet);
            load();
        }

        @Override
        protected void onWalletInactive(final Wallet wallet) {
            removeWalletListener(wallet);
        }

        private void addWalletListener(final Wallet wallet) {
            wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addReorganizeEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addChangeEventListener(Threading.SAME_THREAD, walletListener);
        }

        private void removeWalletListener(final Wallet wallet) {
            wallet.removeChangeEventListener(walletListener);
            wallet.removeReorganizeEventListener(walletListener);
            wallet.removeCoinsSentEventListener(walletListener);
            wallet.removeCoinsReceivedEventListener(walletListener);
        }

        @Override
        protected void load() {
            final Wallet wallet = getWallet();
            AsyncTask.execute(() -> {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                postValue(wallet.getTransactions(true));
            });
        }

        private final WalletListener walletListener = new WalletListener();

        private class WalletListener implements WalletCoinsReceivedEventListener, WalletCoinsSentEventListener,
                WalletReorganizeEventListener, WalletChangeEventListener {
            @Override
            public void onCoinsReceived(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                triggerLoad();
            }

            @Override
            public void onCoinsSent(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                triggerLoad();
            }

            @Override
            public void onReorganize(final Wallet wallet) {
                triggerLoad();
            }

            @Override
            public void onWalletChanged(final Wallet wallet) {
                triggerLoad();
            }
        }
    }

    private static class TransactionsConfidenceLiveData extends AbstractWalletLiveData<Void>
            implements TransactionConfidenceEventListener {
        public TransactionsConfidenceLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            wallet.addTransactionConfidenceEventListener(Threading.SAME_THREAD, this);
        }

        @Override
        protected void onWalletInactive(final Wallet wallet) {
            wallet.removeTransactionConfidenceEventListener(this);
        }

        @Override
        public void onTransactionConfidenceChanged(final Wallet wallet, final Transaction tx) {
            triggerLoad();
        }

        @Override
        protected void load() {
            postValue(null);
        }
    }
}
