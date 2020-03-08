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
import androidx.lifecycle.Observer;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.BlockchainServiceLiveData;
import de.schildbach.wallet.data.WalletLiveData;
import de.schildbach.wallet.service.BlockchainService;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class AbstractWalletActivityViewModel extends AndroidViewModel {
    private final WalletApplication application;
    public final BlockchainServiceLiveData blockchainService;
    public final WalletLiveData wallet;

    private static final Logger log = LoggerFactory.getLogger(AbstractWalletActivityViewModel.class);

    public AbstractWalletActivityViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.blockchainService = new BlockchainServiceLiveData(this.application);
        this.wallet = new WalletLiveData(this.application);
    }

    public void broadcastTransaction(final Transaction tx) throws VerificationException {
        wallet.observeForever(new Observer<Wallet>() {
            @Override
            public void onChanged(final Wallet wallet) {
                blockchainService.observeForever(new Observer<BlockchainService>() {
                    @Override
                    public void onChanged(final BlockchainService blockchainService) {
                        if (wallet.isTransactionRelevant(tx)) {
                            wallet.receivePending(tx, null);
                            blockchainService.broadcastTransaction(tx);
                        } else {
                            log.info("tx {} irrelevant", tx.getTxId());
                        }
                        AbstractWalletActivityViewModel.this.blockchainService.removeObserver(this);
                    }
                });
                AbstractWalletActivityViewModel.this.wallet.removeObserver(this);
            }
        });
    }
}
