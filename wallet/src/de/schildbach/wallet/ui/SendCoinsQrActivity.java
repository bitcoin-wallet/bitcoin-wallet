/*
 * Copyright 2013-2015 the original author or authors.
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

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.VersionedChecksummedBytes;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.ui.send.SweepWalletActivity;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsQrActivity extends Activity {
    private static final int REQUEST_CODE_SCAN = 0;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null)
            startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
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
                protected void handlePrivateKey(final VersionedChecksummedBytes key) {
                    if (Constants.ENABLE_SWEEP_WALLET) {
                        SweepWalletActivity.start(SendCoinsQrActivity.this, key);
                        SendCoinsQrActivity.this.finish();
                    } else {
                        super.handlePrivateKey(key);
                    }
                }

                @Override
                protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                    final WalletApplication application = (WalletApplication) getApplication();
                    application.processDirectTransaction(transaction);

                    SendCoinsQrActivity.this.finish();
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(SendCoinsQrActivity.this, dismissListener, 0, messageResId, messageArgs);
                }

                private final OnClickListener dismissListener = new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        SendCoinsQrActivity.this.finish();
                    }
                };
            }.parse();
        } else {
            finish();
        }
    }
}
