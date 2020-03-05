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

package de.schildbach.wallet.data;

import android.app.Application;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import androidx.lifecycle.LiveData;
import de.schildbach.wallet.WalletApplication;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

/**
 * @author Andreas Schildbach
 */
public class TransactionLiveData extends LiveData<Transaction> implements TransactionConfidence.Listener {
    private final Application application;
    private final Handler handler = new Handler();
    private boolean listening = false;

    public TransactionLiveData(final WalletApplication application) {
        this.application = application;
    }

    @Override
    protected void onActive() {
        maybeAddEventListener(getValue());
    }

    @Override
    protected void onInactive() {
        maybeRemoveEventListener(getValue());
    }

    @Override
    public void setValue(final Transaction newTransaction) {
        final Transaction oldTransaction = getValue();
        maybeRemoveEventListener(oldTransaction);
        super.setValue(newTransaction);
        maybeAddEventListener(newTransaction);
    }

    private void maybeAddEventListener(final Transaction transaction) {
        if (!listening && transaction != null && hasActiveObservers()) {
            transaction.getConfidence().addEventListener(this);
            listening = true;
        }
    }

    private void maybeRemoveEventListener(final Transaction transaction) {
        if (listening && transaction != null) {
            transaction.getConfidence().removeEventListener(this);
            listening = false;
        }
    }

    @Override
    public void onConfidenceChanged(final TransactionConfidence confidence, final ChangeReason reason) {
        handler.post(() -> {
            // trigger change
            super.setValue(super.getValue());

            // play sound effect
            final TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            if (reason == ChangeReason.SEEN_PEERS && confidenceType == TransactionConfidence.ConfidenceType.PENDING) {
                final int numBroadcastPeers = confidence.numBroadcastPeers();
                final int soundResId =
                        application.getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers, "raw",
                                application.getPackageName());
                if (soundResId > 0)
                    RingtoneManager.getRingtone(application,
                            Uri.parse("android.resource://" + application.getPackageName() + "/" + soundResId)).play();
            }
        });
    }
}
