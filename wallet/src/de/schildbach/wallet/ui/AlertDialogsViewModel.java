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

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.text.format.DateUtils;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Installer;
import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Andreas Schildbach
 */
public class AlertDialogsViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private final Configuration config;
    private final PowerManager powerManager;
    public final @Nullable Installer installer;
    public final MutableLiveData<Event<Long>> showTimeskewAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Installer>> showVersionAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<String>> showInsecureDeviceAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showLowStorageAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<String>> showSettingsFailedDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showTooMuchBalanceAlertDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showBatteryOptimizationDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> startBatteryOptimizationActivity = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> requestNotificationPermissionDialog = new MutableLiveData<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            new ContextPropagatingThreadFactory("query-versions"));

    private static final Logger log = LoggerFactory.getLogger(AlertDialogsViewModel.class);

    public AlertDialogsViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.config = this.application.getConfiguration();
        this.powerManager = application.getSystemService(PowerManager.class);
        this.installer = Installer.from(application);

        process();
    }

    @MainThread
    private void process() {
        final PackageInfo packageInfo = application.packageInfo();
        final HttpUrl.Builder url = Constants.VERSION_URL.newBuilder();
        url.addEncodedQueryParameter("package", packageInfo.packageName);
        final String installerPackageName = Installer.installerPackageName(application);
        if (installerPackageName != null)
            url.addEncodedQueryParameter("installer", installerPackageName);
        url.addQueryParameter("sdk", Integer.toString(Build.VERSION.SDK_INT));
        url.addQueryParameter("current", Integer.toString(packageInfo.versionCode));

        executor.execute(() -> processAsync(url.build()));
    }

    @WorkerThread
    private void processAsync(final HttpUrl versionUrl) {
        try {
            log.debug("querying \"{}\"...", versionUrl);
            final Request.Builder request = new Request.Builder();
            request.url(versionUrl);
            final Headers.Builder headers = new Headers.Builder();
            headers.add("Accept-Charset", "utf-8");
            final String userAgent = application.httpUserAgent();
            if (userAgent != null)
                headers.add("User-Agent", userAgent);
            request.headers(headers.build());

            final OkHttpClient.Builder httpClientBuilder = Constants.HTTP_CLIENT.newBuilder();
            httpClientBuilder.connectionSpecs(Collections.singletonList(ConnectionSpec.RESTRICTED_TLS));
            final Call call = httpClientBuilder.build().newCall(request.build());

            final Response response = call.execute();
            if (response.isSuccessful()) {
                // Maybe show timeskew alert.
                final Date serverDate = response.headers().getDate("Date");
                if (serverDate != null) {
                    final long diffMinutes = Math.abs(
                            (System.currentTimeMillis() - serverDate.getTime()) / DateUtils.MINUTE_IN_MILLIS);
                    if (diffMinutes >= 60) {
                        log.info("according to \"" + versionUrl + "\", system clock is off by " + diffMinutes
                                + " minutes");
                        showTimeskewAlertDialog.postValue(new Event<>(diffMinutes));
                        return;
                    }
                }

                // Read properties from server.
                final Map<String, String> properties = new HashMap<>();
                try (final BufferedReader reader = new BufferedReader(response.body().charStream())) {
                    while (true) {
                        final String line = reader.readLine();
                        if (line == null)
                            break;
                        if (line.charAt(0) == '#')
                            continue;

                        final Splitter splitter = Splitter.on('=').trimResults();
                        final Iterator<String> split = splitter.split(line).iterator();
                        if (!split.hasNext())
                            continue;
                        final String key = split.next();
                        if (!split.hasNext()) {
                            properties.put(null, key);
                            continue;
                        }
                        final String value = split.next();
                        if (!split.hasNext()) {
                            properties.put(key.toLowerCase(Locale.US), value);
                            continue;
                        }
                        log.info("Ignoring line: {}", line);
                    }
                }

                // Maybe show version alert.
                String recommendedVersionKey = "version";
                Integer recommendedVersion = properties.containsKey(recommendedVersionKey) ?
                        Ints.tryParse(properties.get(recommendedVersionKey)) : null;
                Installer recommendedMarket = Installer.F_DROID;
                if (installer != null) {
                    final String versionKey = "version." + installer.name().toLowerCase(Locale.US);
                    final Integer version = properties.containsKey(versionKey) ?
                            Ints.tryParse(properties.get(versionKey)) : null;
                    if (recommendedVersion == null || (version != null && version > recommendedVersion)) {
                        recommendedVersionKey = versionKey;
                        recommendedVersion = version;
                        recommendedMarket = installer;
                    }
                }
                if (recommendedVersion != null) {
                    log.info("according to \"{}\" strongly recommended minimum app {} is \"{}\", recommended " +
                            "market is {}", versionUrl, recommendedVersionKey, recommendedVersion, recommendedMarket);
                    if (recommendedVersion > application.packageInfo().versionCode) {
                        showVersionAlertDialog.postValue(new Event<>(recommendedMarket));
                        return;
                    }
                }

                // Maybe show insecure device alert.
                if (Build.VERSION.SECURITY_PATCH.compareToIgnoreCase(Constants.SECURITY_PATCH_INSECURE_BELOW) < 0) {
                    showInsecureDeviceAlertDialog.postValue(new Event<>(Constants.SECURITY_PATCH_INSECURE_BELOW));
                    return;
                }

                // Maybe show low storage alert.
                final Intent stickyIntent = application.registerReceiver(null,
                        new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
                if (stickyIntent != null) {
                    showLowStorageAlertDialog.postValue(Event.simple());
                    return;
                }

                // Maybe show too much balance alert.
                if (Constants.NETWORK_PARAMETERS.getId().equals(MainNetParams.ID_MAINNET)) {
                    final Coin balance = application.getWallet().getBalance();
                    if (balance.isGreaterThan(Constants.TOO_MUCH_BALANCE_THRESHOLD)) {
                        showTooMuchBalanceAlertDialog.postValue(Event.simple());
                        return;
                    }
                }

                final boolean walletIsEmpty = application.getWallet().getTransactions(true).isEmpty();

                // Maybe show battery optimization dialog.
                if (config.isTimeForBatteryOptimizationDialog() &&
                        ContextCompat.checkSelfPermission(application,
                                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_GRANTED &&
                        !powerManager.isIgnoringBatteryOptimizations(application.getPackageName()) &&
                        !walletIsEmpty) {
                    showBatteryOptimizationDialog.postValue(Event.simple());
                    return;
                }

                // Maybe request notification permission.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(application,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                        !walletIsEmpty) {
                    requestNotificationPermissionDialog.postValue(Event.simple());
                    return;
                }

                log.info("all good, no alert dialog shown");
            }
        } catch (final Exception x) {
            if (x instanceof UnknownHostException || x instanceof SocketException || x instanceof SocketTimeoutException) {
                // swallow
                log.debug("problem reading", x);
            } else {
                CrashReporter.saveBackgroundTrace(new RuntimeException(versionUrl.toString(), x),
                        application.packageInfo());
                log.warn("problem parsing", x);
            }
        }
    }

    @MainThread
    public void handleBatteryOptimizationDialogPositiveButton() {
        startBatteryOptimizationActivity.setValue(Event.simple());
        config.removeBatteryOptimizationDialogTime();
    }

    @MainThread
    public void handleBatteryOptimizationDialogNegativeButton() {
        config.setBatteryOptimizationDialogTimeIn(DateUtils.WEEK_IN_MILLIS * 12);
    }
}
