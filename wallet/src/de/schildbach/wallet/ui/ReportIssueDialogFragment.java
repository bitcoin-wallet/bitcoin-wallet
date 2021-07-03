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

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.common.base.Joiner;
import de.schildbach.wallet.BuildConfig;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Installer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * @author Andreas Schildbach
 */
public class ReportIssueDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = ReportIssueDialogFragment.class.getName();
    private static final String KEY_TITLE = "title";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_SUBJECT = "subject";
    private static final String KEY_CONTEXTUAL_TRANSACTION_HASH = "contextual_transaction_hash";

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    public static void show(final FragmentManager fm, final int titleResId, final int messageResId,
            final String subject, final Sha256Hash contextualTransactionHash) {
        final DialogFragment newFragment = new ReportIssueDialogFragment();
        final Bundle args = new Bundle();
        args.putInt(KEY_TITLE, titleResId);
        args.putInt(KEY_MESSAGE, messageResId);
        args.putString(KEY_SUBJECT, subject);
        if (contextualTransactionHash != null)
            args.putByteArray(KEY_CONTEXTUAL_TRANSACTION_HASH, contextualTransactionHash.getBytes());
        newFragment.setArguments(args);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;

    private Button positiveButton;

    private AbstractWalletActivityViewModel walletActivityViewModel;

    private static final Logger log = LoggerFactory.getLogger(ReportIssueDialogFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("opening dialog {}", getClass().getName());

        walletActivityViewModel = new ViewModelProvider(activity).get(AbstractWalletActivityViewModel.class);
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final int titleResId = args.getInt(KEY_TITLE);
        final int messageResId = args.getInt(KEY_MESSAGE);
        final String subject = args.getString(KEY_SUBJECT);
        final Sha256Hash contextualTransactionHash = args.containsKey(KEY_CONTEXTUAL_TRANSACTION_HASH) ?
                Sha256Hash.wrap(args.getByteArray(KEY_CONTEXTUAL_TRANSACTION_HASH)) : null;

        final ReportIssueDialogBuilder builder = new ReportIssueDialogBuilder(activity, titleResId, messageResId) {
            @Override
            protected String subject() {
                final StringBuilder builder = new StringBuilder(subject).append(": ");
                final PackageInfo pi = application.packageInfo();
                builder.append(WalletApplication.versionLine(pi));
                final String installer = Installer.installerPackageName(application);
                if (installer != null)
                    builder.append(", installer ").append(installer);
                builder.append(", android ").append(Build.VERSION.RELEASE);
                builder.append(" (").append(Build.VERSION.SECURITY_PATCH).append(")");
                builder.append(", ").append(Build.MANUFACTURER);
                if (!Build.BRAND.equalsIgnoreCase(Build.MANUFACTURER))
                    builder.append(' ').append(Build.BRAND);
                builder.append(' ').append(Build.MODEL);
                return builder.toString();
            }

            @Override
            protected CharSequence collectApplicationInfo() throws IOException {
                final StringBuilder applicationInfo = new StringBuilder();
                appendApplicationInfo(applicationInfo, application);
                return applicationInfo;
            }

            @Override
            protected CharSequence collectStackTrace() throws IOException {
                final StringBuilder stackTrace = new StringBuilder();
                CrashReporter.appendSavedCrashTrace(stackTrace);
                return stackTrace.length() > 0 ? stackTrace : null;
            }

            @Override
            protected CharSequence collectDeviceInfo() throws IOException {
                final StringBuilder deviceInfo = new StringBuilder();
                appendDeviceInfo(deviceInfo, activity);
                return deviceInfo;
            }

            @Override
            protected CharSequence collectContextualData() {
                if (contextualTransactionHash == null)
                    return null;

                final Wallet wallet = walletActivityViewModel.wallet.getValue();
                final Transaction tx = wallet.getTransaction(contextualTransactionHash);
                final StringBuilder contextualData = new StringBuilder();
                try {
                    contextualData.append(tx.getValue(wallet).toFriendlyString()).append(" total value");
                } catch (final ScriptException x) {
                    contextualData.append(x.getMessage());
                }
                contextualData.append('\n');
                if (tx.hasConfidence())
                    contextualData.append("  confidence: ").append(tx.getConfidence()).append('\n');
                final String[] blockExplorers = activity.getResources()
                        .getStringArray(R.array.preferences_block_explorer_values);
                for (final String blockExplorer : blockExplorers)
                    contextualData
                            .append(Uri.withAppendedPath(Uri.parse(blockExplorer), "tx/" + tx.getTxId().toString()))
                            .append('\n');
                contextualData.append(tx.toString()).append('\n');
                contextualData.append(Constants.HEX.encode(tx.unsafeBitcoinSerialize())).append('\n');
                return contextualData;
            }

            @Override
            protected CharSequence collectWalletDump() {
                return walletActivityViewModel.wallet.getValue().toString(false, false, null, true, true, null);
            }
        };
        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setEnabled(false);

            walletActivityViewModel.wallet.observe(ReportIssueDialogFragment.this, wallet -> positiveButton.setEnabled(true));
        });

        return dialog;
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        CrashReporter.deleteSaveCrashTrace();
        super.onDismiss(dialog);
    }

    private void appendApplicationInfo(final Appendable report, final WalletApplication application)
            throws IOException {
        final PackageInfo pi = application.packageInfo();
        final Configuration config = application.getConfiguration();
        final Calendar calendar = new GregorianCalendar(UTC);

        report.append("Version: ").append(pi.versionName).append(" (").append(String.valueOf(pi.versionCode)).append(
                ")\n");
        report.append("APK Hash: ").append(application.apkHash().toString()).append("\n");
        report.append("Package: ").append(pi.packageName).append("\n");
        report.append("Flavor: " + BuildConfig.FLAVOR + "\n");
        report.append("Build Type: " + BuildConfig.BUILD_TYPE + "\n");
        final String installerPackageName = Installer.installerPackageName(application);
        final Installer installer = Installer.from(installerPackageName);
        if (installer != null)
            report.append("Installer: ").append(installer.displayName).append(" (").append(installerPackageName).append(")\n");
        else
            report.append("Installer: unknown\n");
        report.append("Timezone: ").append(TimeZone.getDefault().getID()).append("\n");
        calendar.setTimeInMillis(System.currentTimeMillis());
        report.append("Current time: ").append(String.format(Locale.US, "%tF %tT %tZ", calendar, calendar, calendar)).append("\n");
        calendar.setTimeInMillis(WalletApplication.TIME_CREATE_APPLICATION);
        report.append("Time of app launch: ").append(String.format(Locale.US, "%tF %tT %tZ", calendar, calendar,
                calendar)).append("\n");
        calendar.setTimeInMillis(pi.firstInstallTime);
        report.append("Time of first app install: ").append(String.format(Locale.US, "%tF %tT %tZ", calendar,
                calendar, calendar)).append("\n");
        calendar.setTimeInMillis(pi.lastUpdateTime);
        report.append("Time of last app update: ").append(String.format(Locale.US, "%tF %tT %tZ", calendar, calendar,
                calendar)).append("\n");
        final long lastBackupTime = config.getLastBackupTime();
        calendar.setTimeInMillis(lastBackupTime);
        report.append("Time of last backup: ").append(lastBackupTime > 0 ? String.format(Locale.US, "%tF %tT %tZ",
                calendar, calendar, calendar) : "none").append("\n");
        final long lastRestoreTime = config.getLastRestoreTime();
        calendar.setTimeInMillis(lastRestoreTime);
        report.append("Time of last restore: ").append(lastRestoreTime > 0 ? String.format(Locale.US, "%tF %tT %tZ",
                calendar, calendar, calendar) : "none").append("\n");
        final long lastEncryptKeysTime = config.getLastEncryptKeysTime();
        calendar.setTimeInMillis(lastEncryptKeysTime);
        report.append("Time of last encrypt keys: ").append(lastEncryptKeysTime > 0 ? String.format(Locale.US, "%tF " +
                "%tT %tZ", calendar, calendar, calendar) :
                "none").append("\n");
        final long lastBlockchainResetTime = config.getLastBlockchainResetTime();
        calendar.setTimeInMillis(lastBlockchainResetTime);
        report.append("Time of last blockchain reset: ").append(lastBlockchainResetTime > 0
                ? String.format(Locale.US, "%tF %tT %tZ", calendar, calendar, calendar) : "none").append("\n");
        report.append("Network: ").append(Constants.NETWORK_PARAMETERS.getId()).append("\n");
        report.append("Sync mode: ").append(config.getSyncMode().name()).append("\n");
        final Wallet wallet = walletActivityViewModel.wallet.getValue();
        report.append("Encrypted: ").append(String.valueOf(wallet.isEncrypted())).append("\n");
        report.append("Keychain size: ").append(String.valueOf(wallet.getKeyChainGroupSize())).append("\n");

        final Set<Transaction> transactions = wallet.getTransactions(true);
        int numInputs = 0;
        int numOutputs = 0;
        int numSpentOutputs = 0;
        for (final Transaction tx : transactions) {
            numInputs += tx.getInputs().size();
            final List<TransactionOutput> outputs = tx.getOutputs();
            numOutputs += outputs.size();
            for (final TransactionOutput txout : outputs) {
                if (!txout.isAvailableForSpending())
                    numSpentOutputs++;
            }
        }
        report.append("Transactions: ").append(String.valueOf(transactions.size())).append("\n");
        report.append("Inputs: ").append(String.valueOf(numInputs)).append("\n");
        report.append("Outputs: ").append(String.valueOf(numOutputs)).append(" (spent: ").append(String.valueOf(numSpentOutputs)).append(")\n");
        final int lastBlockSeenHeight = wallet.getLastBlockSeenHeight();
        final Date lastBlockSeenTime = wallet.getLastBlockSeenTime();
        report.append("Last block seen: ").append(String.valueOf(lastBlockSeenHeight)).append(" (")
                .append(lastBlockSeenTime == null ? "time unknown" : Utils.dateTimeFormat(lastBlockSeenTime))
                .append(")\n");
        report.append("Best chain height ever: ").append(Integer.toString(config.getBestChainHeightEver()))
                .append("\n");

        report.append("Databases:");
        for (final String db : application.databaseList())
            report.append(" ").append(db);
        report.append("\n");

        final File filesDir = application.getFilesDir();
        report.append("\nContents of FilesDir ").append(String.valueOf(filesDir)).append(":\n");
        appendDir(report, filesDir, 0);
        report.append("free/usable space: ").append(Long.toString(filesDir.getFreeSpace() / 1024))
                .append("/").append(Long.toString(filesDir.getUsableSpace() / 1024)).append(" kB\n");
    }

    private static void appendDir(final Appendable report, final File file, final int indent) throws IOException {
        for (int i = 0; i < indent; i++)
            report.append("  - ");

        final Formatter formatter = new Formatter(report);
        final Calendar calendar = new GregorianCalendar(UTC);
        calendar.setTimeInMillis(file.lastModified());
        formatter.format(Locale.US, "%tF %tT %8d kB  %s\n",
                calendar, calendar, file.length() / 1024, file.getName());
        formatter.close();

        final File[] files = file.listFiles();
        if (files != null)
            for (final File f : files)
                appendDir(report, f, indent + 1);
    }

    private static void appendDeviceInfo(final Appendable report, final Context context) throws IOException {
        final Resources res = context.getResources();
        final android.content.res.Configuration config = res.getConfiguration();
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context
                .getSystemService(Context.DEVICE_POLICY_SERVICE);

        report.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        report.append("Device Model: ").append(Build.MODEL).append("\n");
        report.append("Android Version: ").append(Build.VERSION.RELEASE)
                .append(" (").append(Integer.toString(Build.VERSION.SDK_INT)).append(")\n");
        report.append("Android security patch level: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        report.append("ABIs: ").append(Joiner.on(", ").skipNulls().join(Build.SUPPORTED_ABIS)).append("\n");
        report.append("Board: ").append(Build.BOARD).append("\n");
        report.append("Brand: ").append(Build.BRAND).append("\n");
        report.append("Device: ").append(Build.DEVICE).append("\n");
        report.append("Product: ").append(Build.PRODUCT).append("\n");
        report.append("Configuration: ").append(String.valueOf(config)).append("\n");
        report.append("Screen Layout: size ").append(String.valueOf(config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK))
                .append(" long ").append(String.valueOf(config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_LONG_MASK))
                .append(" layoutdir ").append(String.valueOf(config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_LAYOUTDIR_MASK))
                .append(" round ").append(String.valueOf(config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_ROUND_MASK))
                .append("\n");
        report.append("Display Metrics: ").append(String.valueOf(res.getDisplayMetrics())).append("\n");
        report.append("Memory Class: ").append(String.valueOf(activityManager.getMemoryClass())).append("/")
                .append(String.valueOf(activityManager.getLargeMemoryClass()))
                .append(activityManager.isLowRamDevice() ? " (low RAM device)" : "").append("\n");
        report.append("Storage Encryption Status: ").append(String.valueOf(devicePolicyManager.getStorageEncryptionStatus())).append("\n");
        report.append("Bluetooth MAC: ").append(bluetoothMac()).append("\n");
        report.append("Runtime: ").append(System.getProperty("java.vm.name")).append(" ")
                .append(System.getProperty("java.vm.version")).append("\n");
    }

    private static String bluetoothMac() {
        try {
            return Bluetooth.getAddress(BluetoothAdapter.getDefaultAdapter());
        } catch (final Exception x) {
            return x.getMessage();
        }
    }
}
