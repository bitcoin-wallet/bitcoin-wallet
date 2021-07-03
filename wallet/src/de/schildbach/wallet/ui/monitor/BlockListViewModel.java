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

package de.schildbach.wallet.ui.monitor;

import android.app.Application;
import android.os.AsyncTask;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.AbstractWalletLiveData;
import de.schildbach.wallet.data.BlockchainServiceLiveData;
import de.schildbach.wallet.data.TimeLiveData;
import de.schildbach.wallet.service.BlockchainService;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public class BlockListViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private final BlockchainServiceLiveData blockchainService;
    public final MediatorLiveData<List<StoredBlock>> blocks;
    private TransactionsLiveData transactions;
    public final LiveData<List<AddressBookEntry>> addressBook;
    private TimeLiveData time;

    private static final int MAX_BLOCKS = 100;

    public BlockListViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.blockchainService = new BlockchainServiceLiveData(application);
        this.blocks = new MediatorLiveData<>();
        this.blocks.addSource(blockchainService, blockchainService -> maybeRefreshBlocks());
        this.blocks.addSource(this.application.blockchainState, blockchainState -> maybeRefreshBlocks());
        this.addressBook = AddressBookDatabase.getDatabase(this.application).addressBookDao().getAll();
    }

    private void maybeRefreshBlocks() {
        final BlockchainService blockchainService = this.blockchainService.getValue();
        if (blockchainService != null)
            this.blocks.setValue(blockchainService.getRecentBlocks(MAX_BLOCKS));
    }

    public TransactionsLiveData getTransactions() {
        if (transactions == null)
            transactions = new TransactionsLiveData(application);
        return transactions;
    }

    public TimeLiveData getTime() {
        if (time == null)
            time = new TimeLiveData(application);
        return time;
    }

    public static class TransactionsLiveData extends AbstractWalletLiveData<Set<Transaction>> {
        private TransactionsLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            loadTransactions();
        }

        public void loadTransactions() {
            final Wallet wallet = getWallet();
            if (wallet == null)
                return;
            AsyncTask.execute(() -> {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                final Set<Transaction> transactions = wallet.getTransactions(false);
                final Set<Transaction> filteredTransactions = new HashSet<>(transactions.size());
                for (final Transaction tx : transactions) {
                    final Map<Sha256Hash, Integer> appearsIn = tx.getAppearsInHashes();
                    if (appearsIn != null && !appearsIn.isEmpty()) // TODO filter by updateTime
                        filteredTransactions.add(tx);
                }
                postValue(filteredTransactions);
            });
        }
    }
}
