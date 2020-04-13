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

package de.schildbach.wallet;

import android.app.ActivityManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.SettableFuture;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.ui.Event;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.crypto.LinuxSecureRandom;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletFiles;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Andreas Schildbach
 */
public class WalletApplication extends Application {
    private ActivityManager activityManager;

    private File walletFile;
    private WalletFiles walletFiles;
    private Configuration config;

    public final MutableLiveData<BlockchainState> blockchainState = new MutableLiveData<>();
    public final MutableLiveData<Integer> peerState = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> walletChanged = new MutableLiveData<>();

    public static final long TIME_CREATE_APPLICATION = System.currentTimeMillis();
    private static final String BIP39_WORDLIST_FILENAME = "bip39-wordlist.txt";

    private static final Logger log = LoggerFactory.getLogger(WalletApplication.class);

    @Override
    public void onCreate() {
        new LinuxSecureRandom(); // init proper random number generator

        Logging.init(getFilesDir());

        initStrictMode();

        Threading.throwOnLockCycles();
        org.bitcoinj.core.Context.enableStrictMode();
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

        log.info("=== starting app using flavor: {}, build type: {}, network: {}", BuildConfig.FLAVOR,
                BuildConfig.BUILD_TYPE, Constants.NETWORK_PARAMETERS.getId());

        super.onCreate();

        CrashReporter.init(getCacheDir());

        final PackageInfo packageInfo = packageInfo();

        Threading.uncaughtExceptionHandler = (thread, throwable) -> {
            log.info("bitcoinj uncaught exception", throwable);
            CrashReporter.saveBackgroundTrace(throwable, packageInfo);
        };

        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        walletFile = getFileStreamPath(Constants.Files.WALLET_FILENAME_PROTOBUF);

        final Configuration config = getConfiguration();
        config.updateLastVersionCode(packageInfo.versionCode);
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null)
            config.updateLastBluetoothAddress(Bluetooth.getAddress(bluetoothAdapter));

        cleanupFiles();

        initNotificationManager();
    }

    public synchronized Configuration getConfiguration() {
        if (config == null)
            config = new Configuration(PreferenceManager.getDefaultSharedPreferences(this), getResources());
        return config;
    }

    @WorkerThread
    public Wallet getWallet() {
        final Stopwatch watch = Stopwatch.createStarted();
        final SettableFuture<Wallet> future = SettableFuture.create();
        getWalletAsync(wallet -> future.set(wallet));
        try {
            return future.get();
        } catch (final InterruptedException | ExecutionException x) {
            throw new RuntimeException(x);
        } finally {
            watch.stop();
            if (Looper.myLooper() == Looper.getMainLooper())
                log.warn("main thread blocked for " + watch + " when using getWallet()", new RuntimeException());
        }
    }

    private final Executor getWalletExecutor = Executors.newSingleThreadExecutor();
    private final Object getWalletLock = new Object();

    @AnyThread
    public void getWalletAsync(final OnWalletLoadedListener listener) {
        getWalletExecutor.execute(new Runnable() {
            @Override
            public void run() {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                synchronized (getWalletLock) {
                    initMnemonicCode();
                    if (walletFiles == null)
                        loadWalletFromProtobuf();
                }
                listener.onWalletLoaded(walletFiles.getWallet());
            }

            @WorkerThread
            private void loadWalletFromProtobuf() {
                Wallet wallet;
                if (walletFile.exists()) {
                    try (final FileInputStream walletStream = new FileInputStream(walletFile)) {
                        final Stopwatch watch = Stopwatch.createStarted();
                        wallet = new WalletProtobufSerializer().readWallet(walletStream);
                        watch.stop();

                        if (!wallet.getParams().equals(Constants.NETWORK_PARAMETERS))
                            throw new UnreadableWalletException(
                                    "bad wallet network parameters: " + wallet.getParams().getId());

                        log.info("wallet loaded from: '{}', took {}", walletFile, watch);
                    } catch (final IOException | UnreadableWalletException x) {
                        log.warn("problem loading wallet, auto-restoring: " + walletFile, x);
                        wallet = WalletUtils.restoreWalletFromAutoBackup(WalletApplication.this);
                        if (wallet != null)
                            new Toast(WalletApplication.this).postLongToast(R.string.toast_wallet_reset);
                    }
                    if (!wallet.isConsistent()) {
                        log.warn("inconsistent wallet, auto-restoring: " + walletFile);
                        wallet = WalletUtils.restoreWalletFromAutoBackup(WalletApplication.this);
                        if (wallet != null)
                            new Toast(WalletApplication.this).postLongToast(R.string.toast_wallet_reset);
                    }

                    if (!wallet.getParams().equals(Constants.NETWORK_PARAMETERS))
                        throw new Error("bad wallet network parameters: " + wallet.getParams().getId());

                    wallet.cleanup();
                    walletFiles = wallet.autosaveToFile(walletFile, Constants.Files.WALLET_AUTOSAVE_DELAY_MS,
                            TimeUnit.MILLISECONDS, null);
                } else {
                    final Stopwatch watch = Stopwatch.createStarted();
                    wallet = Wallet.createDeterministic(Constants.NETWORK_PARAMETERS,
                            Constants.DEFAULT_OUTPUT_SCRIPT_TYPE);
                    walletFiles = wallet.autosaveToFile(walletFile, Constants.Files.WALLET_AUTOSAVE_DELAY_MS,
                            TimeUnit.MILLISECONDS, null);
                    autosaveWalletNow(); // persist...
                    WalletUtils.autoBackupWallet(WalletApplication.this, wallet); // ...and backup asap
                    watch.stop();
                    log.info("fresh {} wallet created, took {}", Constants.DEFAULT_OUTPUT_SCRIPT_TYPE, watch);

                    config.armBackupReminder();
                }
            }

            private void initMnemonicCode() {
                if (MnemonicCode.INSTANCE == null) {
                    try {
                        final Stopwatch watch = Stopwatch.createStarted();
                        MnemonicCode.INSTANCE = new MnemonicCode(getAssets().open(BIP39_WORDLIST_FILENAME), null);
                        watch.stop();
                        log.info("BIP39 wordlist loaded from: '{}', took {}", BIP39_WORDLIST_FILENAME, watch);
                    } catch (final IOException x) {
                        throw new Error(x);
                    }
                }
            }
        });
    }

    public interface OnWalletLoadedListener {
        void onWalletLoaded(Wallet wallet);
    }

    public void autosaveWalletNow() {
        final Stopwatch watch = Stopwatch.createStarted();
        synchronized (getWalletLock) {
            if (walletFiles != null) {
                try {
                    walletFiles.saveNow();
                    watch.stop();
                    log.info("wallet saved to: '{}', took {}", walletFile, watch);
                } catch (final IOException x) {
                    log.warn("problem with forced autosaving of wallet", x);
                    CrashReporter.saveBackgroundTrace(x, packageInfo);
                }
            }
        }
    }

    public void replaceWallet(final Wallet newWallet) {
        newWallet.cleanup();
        if (newWallet.isDeterministicUpgradeRequired(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE) && !newWallet.isEncrypted())
            newWallet.upgradeToDeterministic(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE, null);
        BlockchainService.resetBlockchain(this);

        final Wallet oldWallet = getWallet();
        synchronized (getWalletLock) {
            oldWallet.shutdownAutosaveAndWait(); // this will also prevent BlockchainService to save
            walletFiles = newWallet.autosaveToFile(walletFile, Constants.Files.WALLET_AUTOSAVE_DELAY_MS,
                    TimeUnit.MILLISECONDS, null);
        }
        config.maybeIncrementBestChainHeightEver(newWallet.getLastBlockSeenHeight());
        WalletUtils.autoBackupWallet(this, newWallet);

        walletChanged.setValue(Event.simple());
    }

    private void cleanupFiles() {
        for (final String filename : fileList()) {
            if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_BASE58)
                    || filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + '.')
                    || filename.endsWith(".tmp")) {
                final File file = new File(getFilesDir(), filename);
                log.info("removing obsolete file: '{}'", file);
                file.delete();
            }
        }
    }

    public static void initStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads()
                .permitDiskWrites().penaltyLog().build());
    }

    private void initNotificationManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Stopwatch watch = Stopwatch.createStarted();
            final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            final NotificationChannel received = new NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID_RECEIVED,
                    getString(R.string.notification_channel_received_name), NotificationManager.IMPORTANCE_DEFAULT);
            received.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received),
                    new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build());
            nm.createNotificationChannel(received);

            final NotificationChannel ongoing = new NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID_ONGOING,
                    getString(R.string.notification_channel_ongoing_name), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ongoing);

            final NotificationChannel important = new NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID_IMPORTANT,
                    getString(R.string.notification_channel_important_name), NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(important);

            log.info("created notification channels, took {}", watch);
        }
    }

    private PackageInfo packageInfo;

    public synchronized PackageInfo packageInfo() {
        // replace by BuildConfig.VERSION_* as soon as it's possible
        if (packageInfo == null) {
            try {
                packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            } catch (final NameNotFoundException x) {
                throw new RuntimeException(x);
            }
        }
        return packageInfo;
    }

    public final String applicationPackageFlavor() {
        final String packageName = getPackageName();
        final int index = packageName.lastIndexOf('_');

        if (index != -1)
            return packageName.substring(index + 1);
        else
            return null;
    }

    public static String httpUserAgent(final String versionName) {
        final VersionMessage versionMessage = new VersionMessage(Constants.NETWORK_PARAMETERS, 0);
        versionMessage.appendToSubVer(Constants.USER_AGENT, versionName, null);
        return versionMessage.subVer;
    }

    public String httpUserAgent() {
        return httpUserAgent(packageInfo().versionName);
    }

    public int maxConnectedPeers() {
        return activityManager.getMemoryClass() <= 128 ? 4 : 6;
    }

    public int scryptIterationsTarget() {
        return activityManager.getMemoryClass() <= 128 || Build.SUPPORTED_64_BIT_ABIS.length == 0
                ? Constants.SCRYPT_ITERATIONS_TARGET_LOWRAM : Constants.SCRYPT_ITERATIONS_TARGET;
    }

    public boolean fullSyncCapable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activityManager.getMemoryClass() >= 128;
    }

    public static String versionLine(final PackageInfo packageInfo) {
        return ImmutableList.copyOf(Splitter.on('.').splitToList(packageInfo.packageName)).reverse().get(0) + ' '
                + packageInfo.versionName + (BuildConfig.DEBUG ? " (debuggable)" : "");
    }

    public final HashCode apkHash() throws IOException {
        final Hasher hasher = Hashing.sha256().newHasher();
        final FileInputStream is = new FileInputStream(getPackageCodePath());
        final byte[] buf = new byte[4096];
        int read;
        while (-1 != (read = is.read(buf)))
            hasher.putBytes(buf, 0, read);
        is.close();
        return hasher.hash();
    }
}
