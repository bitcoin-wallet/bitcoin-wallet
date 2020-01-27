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

import android.app.Activity;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.ui.send.SweepWalletActivity;
import org.bitcoinj.core.PrefixedChecksummedBytes;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsQrActivity extends AbstractWalletActivity {
    private AbstractWalletActivityViewModel walletActivityViewModel;

    private static final int REQUEST_CODE_SCAN = 0;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        walletActivityViewModel = new ViewModelProvider(this).get(AbstractWalletActivityViewModel.class);

        if (savedInstanceState == null)
            ScanActivity.startForResult(this, REQUEST_CODE_SCAN);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK) {
            final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

            new StringInputParser(input) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                    SendCoinsActivity.start(SendCoinsQrActivity.this, paymentIntent);

                    SendCoinsQrActivity.this.finish();
                }

                @Override
                protected void handlePrivateKey(final PrefixedChecksummedBytes key) {
                    if (Constants.ENABLE_SWEEP_WALLET) {
                        SweepWalletActivity.start(SendCoinsQrActivity.this, key);
                        SendCoinsQrActivity.this.finish();
                    } else {
                        super.handlePrivateKey(key);
                    }
                }

                @Override
                protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                    walletActivityViewModel.broadcastTransaction(transaction);
                    SendCoinsQrActivity.this.finish();
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    final DialogBuilder dialog = DialogBuilder.dialog(SendCoinsQrActivity.this, 0, messageResId, messageArgs);
                    dialog.singleDismissButton(dismissListener);
                    dialog.show();
                }

                private final OnClickListener dismissListener = (dialog, which) -> SendCoinsQrActivity.this.finish();
            }.parse();
        } else {
            finish();
        }
    }
}
