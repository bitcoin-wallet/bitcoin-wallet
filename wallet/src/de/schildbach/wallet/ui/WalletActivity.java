/*
 * Copyright 2011-2015 the original author or authors.
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
import org.bitcoinj.wallet.Wallet;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.backup.BackupWalletDialogFragment;
import de.schildbach.wallet.ui.backup.RestoreWalletDialogFragment;
import de.schildbach.wallet.ui.preference.PreferenceActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.ui.send.SweepWalletActivity;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractWalletActivity {
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;

    private Handler handler = new Handler();

    private static final int REQUEST_CODE_SCAN = 0;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        application = getWalletApplication();
        config = application.getConfiguration();
        wallet = application.getWallet();

        setContentView(R.layout.wallet_content);

        final View exchangeRatesFragment = findViewById(R.id.wallet_main_twopanes_exchange_rates);
        if (exchangeRatesFragment != null)
            exchangeRatesFragment.setVisibility(Constants.ENABLE_EXCHANGE_RATES ? View.VISIBLE : View.GONE);

        if (savedInstanceState == null) {
            final View contentView = findViewById(android.R.id.content);
            final View slideInLeftView = contentView.findViewWithTag("slide_in_left");
            if (slideInLeftView != null)
                slideInLeftView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_left));
            final View slideInRightView = contentView.findViewWithTag("slide_in_right");
            if (slideInRightView != null)
                slideInRightView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
            final View slideInTopView = contentView.findViewWithTag("slide_in_top");
            if (slideInTopView != null)
                slideInTopView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_top));
            final View slideInBottomView = contentView.findViewWithTag("slide_in_bottom");
            if (slideInBottomView != null)
                slideInBottomView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom));

            checkSavedCrashTrace();
        }

        config.touchLastUsed();

        handleIntent(getIntent());

        final FragmentManager fragmentManager = getSupportFragmentManager();
        MaybeMaintenanceFragment.add(fragmentManager);
        AlertDialogsFragment.add(fragmentManager);
    }

    @Override
    protected void onResume() {
        super.onResume();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // delayed start so that UI has enough time to initialize
                BlockchainService.start(WalletActivity.this, true);
            }
        }, 1000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);

        super.onPause();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        final String action = intent.getAction();

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            final String inputType = intent.getType();
            final NdefMessage ndefMessage = (NdefMessage) intent
                    .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
            final byte[] input = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

            new BinaryInputParser(inputType, input) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                    cannotClassify(inputType);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(WalletActivity.this, null, 0, messageResId, messageArgs);
                }
            }.parse();
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                new StringInputParser(input) {
                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                        SendCoinsActivity.start(WalletActivity.this, paymentIntent);
                    }

                    @Override
                    protected void handlePrivateKey(final VersionedChecksummedBytes key) {
                        if (Constants.ENABLE_SWEEP_WALLET)
                            SweepWalletActivity.start(WalletActivity.this, key);
                        else
                            super.handlePrivateKey(key);
                    }

                    @Override
                    protected void handleDirectTransaction(final Transaction tx) throws VerificationException {
                        application.processDirectTransaction(tx);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        dialog(WalletActivity.this, null, R.string.button_scan, messageResId, messageArgs);
                    }
                }.parse();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.wallet_options, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final Resources res = getResources();
        final String externalStorageState = Environment.getExternalStorageState();

        menu.findItem(R.id.wallet_options_exchange_rates)
                .setVisible(Constants.ENABLE_EXCHANGE_RATES && res.getBoolean(R.bool.show_exchange_rates_option));
        menu.findItem(R.id.wallet_options_sweep_wallet).setVisible(Constants.ENABLE_SWEEP_WALLET);
        menu.findItem(R.id.wallet_options_restore_wallet)
                .setEnabled(Environment.MEDIA_MOUNTED.equals(externalStorageState)
                        || Environment.MEDIA_MOUNTED_READ_ONLY.equals(externalStorageState));
        menu.findItem(R.id.wallet_options_backup_wallet)
                .setEnabled(Environment.MEDIA_MOUNTED.equals(externalStorageState));
        final MenuItem encryptKeysOption = menu.findItem(R.id.wallet_options_encrypt_keys);
        encryptKeysOption.setTitle(wallet.isEncrypted() ? R.string.wallet_options_encrypt_keys_change
                : R.string.wallet_options_encrypt_keys_set);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.wallet_options_request:
            handleRequestCoins();
            return true;

        case R.id.wallet_options_send:
            handleSendCoins();
            return true;

        case R.id.wallet_options_scan:
            handleScan();
            return true;

        case R.id.wallet_options_address_book:
            AddressBookActivity.start(this);
            return true;

        case R.id.wallet_options_exchange_rates:
            startActivity(new Intent(this, ExchangeRatesActivity.class));
            return true;

        case R.id.wallet_options_sweep_wallet:
            SweepWalletActivity.start(this);
            return true;

        case R.id.wallet_options_network_monitor:
            startActivity(new Intent(this, NetworkMonitorActivity.class));
            return true;

        case R.id.wallet_options_restore_wallet:
            handleRestoreWallet();
            return true;

        case R.id.wallet_options_backup_wallet:
            handleBackupWallet();
            return true;

        case R.id.wallet_options_encrypt_keys:
            handleEncryptKeys();
            return true;

        case R.id.wallet_options_preferences:
            startActivity(new Intent(this, PreferenceActivity.class));
            return true;

        case R.id.wallet_options_safety:
            HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_safety);
            return true;

        case R.id.wallet_options_technical_notes:
            HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_technical_notes);
            return true;

        case R.id.wallet_options_report_issue:
            handleReportIssue();
            return true;

        case R.id.wallet_options_help:
            HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_wallet);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void handleRequestCoins() {
        startActivity(new Intent(this, RequestCoinsActivity.class));
    }

    public void handleSendCoins() {
        startActivity(new Intent(this, SendCoinsActivity.class));
    }

    public void handleScan() {
        startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
    }

    public void handleBackupWallet() {
        BackupWalletDialogFragment.show(getSupportFragmentManager());
    }

    public void handleRestoreWallet() {
        RestoreWalletDialogFragment.show(getSupportFragmentManager());
    }

    public void handleEncryptKeys() {
        EncryptKeysDialogFragment.show(getSupportFragmentManager());
    }

    private void handleReportIssue() {
        ReportIssueDialogFragment.show(getSupportFragmentManager(), R.string.report_issue_dialog_title_issue,
                R.string.report_issue_dialog_message_issue, Constants.REPORT_SUBJECT_ISSUE, null);
    }

    private void checkSavedCrashTrace() {
        if (CrashReporter.hasSavedCrashTrace())
            ReportIssueDialogFragment.show(getSupportFragmentManager(), R.string.report_issue_dialog_title_crash,
                    R.string.report_issue_dialog_message_crash, Constants.REPORT_SUBJECT_CRASH, null);
    }
}
