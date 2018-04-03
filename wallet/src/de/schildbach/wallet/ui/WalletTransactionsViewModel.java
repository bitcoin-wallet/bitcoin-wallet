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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookLiveData;
import de.schildbach.wallet.data.ConfigFormatLiveData;
import de.schildbach.wallet.data.ThrottelingLiveData;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;

/**
 * @author Andreas Schildbach
 */
public class WalletTransactionsViewModel extends AndroidViewModel {
    public enum Direction {
        RECEIVED, SENT
    }

    private final WalletApplication application;
    private final TransactionsLiveData transactions;
    private final TransactionsConfidenceLiveData transactionsConfidence;
    private final AddressBookLiveData addressBook;
    private final ConfigFormatLiveData configFormat;
    private final MutableLiveData<Direction> direction = new MutableLiveData<>();
    private final MutableLiveData<Sha256Hash> selectedTransaction = new MutableLiveData<>();
    private final MutableLiveData<TransactionsAdapter.WarningType> warning = new MutableLiveData<>();
    private final MediatorLiveData<List<TransactionsAdapter.ListItem>> list = new MediatorLiveData<>();

    public WalletTransactionsViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.transactions = new TransactionsLiveData(this.application);
        this.transactionsConfidence = new TransactionsConfidenceLiveData(this.application);
        this.addressBook = new AddressBookLiveData(this.application);
        this.configFormat = new ConfigFormatLiveData(this.application);
        this.list.addSource(transactions, new Observer<Set<Transaction>>() {
            @Override
            public void onChanged(final Set<Transaction> transactions) {
                maybePostList();
            }
        });
        this.list.addSource(transactionsConfidence, new Observer<Void>() {
            @Override
            public void onChanged(final Void v) {
                maybePostList();
            }
        });
        this.list.addSource(addressBook, new Observer<Map<String, String>>() {
            @Override
            public void onChanged(final Map<String, String> addressBook) {
                maybePostList();
            }
        });
        this.list.addSource(direction, new Observer<Direction>() {
            @Override
            public void onChanged(final Direction direction) {
                maybePostList();
            }
        });
        this.list.addSource(selectedTransaction, new Observer<Sha256Hash>() {
            @Override
            public void onChanged(final Sha256Hash selectedTransaction) {
                maybePostList();
            }
        });
        this.list.addSource(configFormat, new Observer<MonetaryFormat>() {
            @Override
            public void onChanged(final MonetaryFormat format) {
                maybePostList();
            }
        });
    }

    public Direction getDirection() {
        return direction.getValue();
    }

    public void setDirection(final Direction direction) {
        this.direction.setValue(direction);
    }

    public Sha256Hash getSelectedTransaction() {
        return selectedTransaction.getValue();
    }

    public void setSelectedTransaction(final Sha256Hash selectedTransaction) {
        this.selectedTransaction.setValue(selectedTransaction);
    }

    public void setWarning(final TransactionsAdapter.WarningType warning) {
        this.warning.setValue(warning);
    }

    public LiveData<List<TransactionsAdapter.ListItem>> getList() {
        return list;
    }

    private void maybePostList() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                final Set<Transaction> transactions = WalletTransactionsViewModel.this.transactions.getValue();
                final MonetaryFormat format = configFormat.getValue();
                final Map<String, String> addressBook = WalletTransactionsViewModel.this.addressBook.getValue();
                if (transactions != null && format != null && addressBook != null) {
                    final List<Transaction> filteredTransactions = new ArrayList<Transaction>(transactions.size());
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
                            warning.getValue(), wallet, addressBook, format, application.maxConnectedPeers(),
                            selectedTransaction.getValue()));
                }
            }
        });
    }

    private static final Comparator<Transaction> TRANSACTION_COMPARATOR = new Comparator<Transaction>() {
        @Override
        public int compare(final Transaction tx1, final Transaction tx2) {
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

            return tx1.getHash().compareTo(tx2.getHash());
        }
    };

    private static class TransactionsLiveData extends ThrottelingLiveData<Set<Transaction>> {
        private final WalletApplication application;
        private final LocalBroadcastManager broadcastManager;
        private Wallet wallet;
        private static final long THROTTLE_MS = 1000;

        public TransactionsLiveData(final WalletApplication application) {
            super(THROTTLE_MS);
            this.application = application;
            this.broadcastManager = LocalBroadcastManager.getInstance(application);
        }

        @Override
        protected void onActive() {
            broadcastManager.registerReceiver(walletChangeReceiver,
                    new IntentFilter(WalletApplication.ACTION_WALLET_REFERENCE_CHANGED));
            this.wallet = application.getWallet();
            addWalletListener();
            load();
        }

        @Override
        protected void onInactive() {
            removeWalletListener();
            broadcastManager.unregisterReceiver(walletChangeReceiver);
        }

        private void addWalletListener() {
            wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addReorganizeEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addChangeEventListener(Threading.SAME_THREAD, walletListener);
        }

        private void removeWalletListener() {
            wallet.removeChangeEventListener(walletListener);
            wallet.removeReorganizeEventListener(walletListener);
            wallet.removeCoinsSentEventListener(walletListener);
            wallet.removeCoinsReceivedEventListener(walletListener);
        }

        @Override
        protected void load() {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                    postValue(wallet.getTransactions(true));
                }
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

        private final BroadcastReceiver walletChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                removeWalletListener();
                wallet = application.getWallet();
                addWalletListener();
                load();
            }
        };
    }

    private static class TransactionsConfidenceLiveData extends ThrottelingLiveData<Void>
            implements TransactionConfidenceEventListener {
        private final Wallet wallet;

        public TransactionsConfidenceLiveData(final WalletApplication application) {
            this.wallet = application.getWallet();
        }

        @Override
        protected void onActive() {
            wallet.addTransactionConfidenceEventListener(Threading.SAME_THREAD, this);
        }

        @Override
        protected void onInactive() {
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