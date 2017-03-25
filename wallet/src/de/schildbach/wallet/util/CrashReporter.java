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

package de.schildbach.wallet.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.support.v4.app.ActivityManagerCompat;

/**
 * @author Andreas Schildbach
 */
public class CrashReporter {
    private static final String BACKGROUND_TRACES_FILENAME = "background.trace";
    private static final String CRASH_TRACE_FILENAME = "crash.trace";

    private static File backgroundTracesFile;
    private static File crashTraceFile;

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final Logger log = LoggerFactory.getLogger(CrashReporter.class);

    public static void init(final File cacheDir) {
        backgroundTracesFile = new File(cacheDir, BACKGROUND_TRACES_FILENAME);
        crashTraceFile = new File(cacheDir, CRASH_TRACE_FILENAME);

        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()));
    }

    public static boolean hasSavedBackgroundTraces() {
        return backgroundTracesFile.exists();
    }

    public static void appendSavedBackgroundTraces(final Appendable report) throws IOException {
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(backgroundTracesFile), Charsets.UTF_8));
            copy(reader, report);
        } finally {
            if (reader != null)
                reader.close();

            backgroundTracesFile.delete();
        }
    }

    public static boolean hasSavedCrashTrace() {
        return crashTraceFile.exists();
    }

    public static void appendSavedCrashTrace(final Appendable report) throws IOException {
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(crashTraceFile), Charsets.UTF_8));
            copy(reader, report);
        } finally {
            if (reader != null)
                reader.close();

            crashTraceFile.delete();
        }
    }

    private static void copy(final BufferedReader in, final Appendable out) throws IOException {
        while (true) {
            final String line = in.readLine();
            if (line == null)
                break;

            out.append(line).append('\n');
        }
    }

    public static void appendDeviceInfo(final Appendable report, final Context context) throws IOException {
        final Resources res = context.getResources();
        final android.content.res.Configuration config = res.getConfiguration();
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context
                .getSystemService(Context.DEVICE_POLICY_SERVICE);

        report.append("Device Model: " + Build.MODEL + "\n");
        report.append("Android Version: " + Build.VERSION.RELEASE + "\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            report.append("Android security patch level: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        report.append("ABIs: ").append(Joiner.on(", ").skipNulls().join(Strings.emptyToNull(Build.CPU_ABI),
                Strings.emptyToNull(Build.CPU_ABI2))).append("\n");
        report.append("Board: " + Build.BOARD + "\n");
        report.append("Brand: " + Build.BRAND + "\n");
        report.append("Device: " + Build.DEVICE + "\n");
        report.append("Display: " + Build.DISPLAY + "\n");
        report.append("Finger Print: " + Build.FINGERPRINT + "\n");
        report.append("Host: " + Build.HOST + "\n");
        report.append("ID: " + Build.ID + "\n");
        report.append("Product: " + Build.PRODUCT + "\n");
        report.append("Tags: " + Build.TAGS + "\n");
        report.append("Time: " + Build.TIME + "\n");
        report.append("Type: " + Build.TYPE + "\n");
        report.append("User: " + Build.USER + "\n");
        report.append("Configuration: " + config + "\n");
        report.append("Screen Layout: size "
                + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) + " long "
                + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_LONG_MASK) + "\n");
        report.append("Display Metrics: " + res.getDisplayMetrics() + "\n");
        report.append("Memory Class: " + activityManager.getMemoryClass() + "/" + activityManager.getLargeMemoryClass()
                + (ActivityManagerCompat.isLowRamDevice(activityManager) ? " (low RAM device)" : "") + "\n");
        report.append("Storage Encryption Status: " + devicePolicyManager.getStorageEncryptionStatus() + "\n");
        report.append("Bluetooth MAC: " + bluetoothMac() + "\n");
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

    public static void appendInstalledPackages(final Appendable report, final Context context) throws IOException {
        final PackageManager pm = context.getPackageManager();
        final List<PackageInfo> installedPackages = pm.getInstalledPackages(0);

        // sort by package name
        Collections.sort(installedPackages, new Comparator<PackageInfo>() {
            @Override
            public int compare(final PackageInfo lhs, final PackageInfo rhs) {
                return lhs.packageName.compareTo(rhs.packageName);
            }
        });

        for (final PackageInfo p : installedPackages)
            report.append(String.format(Locale.US, "%s %s (%d) - %tF %tF\n", p.packageName, p.versionName,
                    p.versionCode, p.firstInstallTime, p.lastUpdateTime));
    }

    public static void appendApplicationInfo(final Appendable report, final WalletApplication application)
            throws IOException {
        final PackageInfo pi = application.packageInfo();
        final Configuration configuration = application.getConfiguration();
        final Calendar calendar = new GregorianCalendar(UTC);

        report.append("Version: " + pi.versionName + " (" + pi.versionCode + ")\n");
        report.append("Package: " + pi.packageName + "\n");
        report.append("Installer: " + application.getPackageManager().getInstallerPackageName(pi.packageName) + "\n");
        report.append("Test/Prod: " + (Constants.TEST ? "test" : "prod") + "\n");
        report.append("Timezone: " + TimeZone.getDefault().getID() + "\n");
        calendar.setTimeInMillis(System.currentTimeMillis());
        report.append("Time: " + String.format(Locale.US, "%tF %tT %tZ", calendar, calendar, calendar) + "\n");
        calendar.setTimeInMillis(WalletApplication.TIME_CREATE_APPLICATION);
        report.append(
                "Time of launch: " + String.format(Locale.US, "%tF %tT %tZ", calendar, calendar, calendar) + "\n");
        calendar.setTimeInMillis(pi.lastUpdateTime);
        report.append(
                "Time of last update: " + String.format(Locale.US, "%tF %tT %tZ", calendar, calendar, calendar) + "\n");
        calendar.setTimeInMillis(pi.firstInstallTime);
        report.append("Time of first install: " + String.format(Locale.US, "%tF %tT %tZ", calendar, calendar, calendar)
                + "\n");
        final long lastBackupTime = configuration.getLastBackupTime();
        calendar.setTimeInMillis(lastBackupTime);
        report.append("Time of backup: "
                + (lastBackupTime > 0 ? String.format(Locale.US, "%tF %tT %tZ", calendar, calendar, calendar) : "none")
                + "\n");
        report.append("Network: " + Constants.NETWORK_PARAMETERS.getId() + "\n");
        final Wallet wallet = application.getWallet();
        report.append("Encrypted: " + wallet.isEncrypted() + "\n");
        report.append("Keychain size: " + wallet.getKeyChainGroupSize() + "\n");

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
        report.append("Transactions: " + transactions.size() + "\n");
        report.append("Inputs: " + numInputs + "\n");
        report.append("Outputs: " + numOutputs + " (spent: " + numSpentOutputs + ")\n");
        report.append(
                "Last block seen: " + wallet.getLastBlockSeenHeight() + " (" + wallet.getLastBlockSeenHash() + ")\n");

        report.append("Databases:");
        for (final String db : application.databaseList())
            report.append(" " + db);
        report.append("\n");

        final File filesDir = application.getFilesDir();
        report.append("\nContents of FilesDir " + filesDir + ":\n");
        appendDir(report, filesDir, 0);
        final File logDir = application.getDir("log", Context.MODE_PRIVATE);
        report.append("\nContents of LogDir " + logDir + ":\n");
        appendDir(report, logDir, 0);
    }

    private static void appendDir(final Appendable report, final File file, final int indent) throws IOException {
        for (int i = 0; i < indent; i++)
            report.append("  - ");

        final Formatter formatter = new Formatter(report);
        final Calendar calendar = new GregorianCalendar(UTC);
        calendar.setTimeInMillis(file.lastModified());
        formatter.format(Locale.US, "%tF %tT %8d  %s\n", calendar, calendar, file.length(), file.getName());
        formatter.close();

        final File[] files = file.listFiles();
        if (files != null)
            for (final File f : files)
                appendDir(report, f, indent + 1);
    }

    public static void saveBackgroundTrace(final Throwable throwable, final PackageInfo packageInfo) {
        synchronized (backgroundTracesFile) {
            PrintWriter writer = null;

            try {
                writer = new PrintWriter(
                        new OutputStreamWriter(new FileOutputStream(backgroundTracesFile, true), Charsets.UTF_8));

                final Calendar now = new GregorianCalendar(UTC);
                writer.println(String.format(Locale.US, "\n--- collected at %tF %tT %tZ on version %s (%d)", now, now,
                        now, packageInfo.versionName, packageInfo.versionCode));
                appendTrace(writer, throwable);
            } catch (final IOException x) {
                log.error("problem writing background trace", x);
            } finally {
                if (writer != null)
                    writer.close();
            }
        }
    }

    private static void appendTrace(final PrintWriter writer, final Throwable throwable) {
        throwable.printStackTrace(writer);
        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        Throwable cause = throwable.getCause();
        while (cause != null) {
            writer.println("\nCause:\n");
            cause.printStackTrace(writer);
            cause = cause.getCause();
        }
    }

    private static class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler previousHandler;

        public ExceptionHandler(final Thread.UncaughtExceptionHandler previousHandler) {
            this.previousHandler = previousHandler;
        }

        @Override
        public synchronized void uncaughtException(final Thread t, final Throwable exception) {
            log.warn("crashing because of uncaught exception", exception);

            try {
                saveCrashTrace(exception);
            } catch (final IOException x) {
                log.info("problem writing crash trace", x);
            }

            previousHandler.uncaughtException(t, exception);
        }

        private void saveCrashTrace(final Throwable throwable) throws IOException {
            final PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(crashTraceFile), Charsets.UTF_8));
            appendTrace(writer, throwable);
            writer.close();
        }
    }
}
