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

import android.os.Handler;
import android.os.Looper;
import de.schildbach.wallet.Constants;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.CompletionException;
import org.bitcoinj.wallet.Wallet.CouldNotAdjustDownwards;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public abstract class SendCoinsOfflineTask {
    private final Wallet wallet;
    private final Handler backgroundHandler;
    private final Handler callbackHandler;

    private static final Logger log = LoggerFactory.getLogger(SendCoinsOfflineTask.class);

    public SendCoinsOfflineTask(final Wallet wallet, final Handler backgroundHandler) {
        this.wallet = wallet;
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
    }

    public final void sendCoinsOffline(final SendRequest sendRequest) {
        backgroundHandler.post(() -> {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

            try {
                log.info("sending: {}", sendRequest);
                final Transaction transaction = wallet.sendCoinsOffline(sendRequest); // can take long
                log.info("send successful, transaction committed: {}", transaction.getTxId());

                callbackHandler.post(() -> onSuccess(transaction));
            } catch (final InsufficientMoneyException x) {
                final Coin missing = x.missing;
                if (missing != null)
                    log.info("send failed, {} missing", missing.toFriendlyString());
                else
                    log.info("send failed, insufficient coins");

                callbackHandler.post(() -> onInsufficientMoney(x.missing));
            } catch (final ECKey.KeyIsEncryptedException x) {
                log.info("send failed, key is encrypted: {}", x.getMessage());

                callbackHandler.post(() -> onFailure(x));
            } catch (final KeyCrypterException x) {
                log.info("send failed, key crypter exception: {}", x.getMessage());

                final boolean isEncrypted = wallet.isEncrypted();
                callbackHandler.post(() -> {
                    if (isEncrypted)
                        onInvalidEncryptionKey();
                    else
                        onFailure(x);
                });
            } catch (final CouldNotAdjustDownwards x) {
                log.info("send failed, could not adjust downwards: {}", x.getMessage());

                callbackHandler.post(() -> onEmptyWalletFailed());
            } catch (final CompletionException x) {
                log.info("send failed, cannot complete: {}", x.getMessage());

                callbackHandler.post(() -> onFailure(x));
            }
        });
    }

    protected abstract void onSuccess(Transaction transaction);

    protected abstract void onInsufficientMoney(Coin missing);

    protected abstract void onInvalidEncryptionKey();

    protected void onEmptyWalletFailed() {
        onFailure(new CouldNotAdjustDownwards());
    }

    protected abstract void onFailure(Exception exception);
}
