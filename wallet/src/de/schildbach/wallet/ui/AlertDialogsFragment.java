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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.io.BufferedReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet_test.R;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.format.DateUtils;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Andreas Schildbach
 */
public class AlertDialogsFragment extends Fragment {
    private static final String FRAGMENT_TAG = AlertDialogsFragment.class.getName();

    public static void add(final FragmentManager fm) {
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new AlertDialogsFragment();
            fm.beginTransaction().add(fragment, FRAGMENT_TAG).commit();
        }
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private PackageManager packageManager;

    private HttpUrl versionUrl;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private static final Logger log = LoggerFactory.getLogger(AlertDialogsFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.packageManager = activity.getPackageManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        final PackageInfo packageInfo = application.packageInfo();
        final int versionNameSplit = packageInfo.versionName.indexOf('-');
        final HttpUrl.Builder url = HttpUrl
                .parse(Constants.VERSION_URL
                        + (versionNameSplit >= 0 ? packageInfo.versionName.substring(versionNameSplit) : ""))
                .newBuilder();
        url.addEncodedQueryParameter("package", packageInfo.packageName);
        url.addEncodedQueryParameter("installer",
                Strings.nullToEmpty(packageManager.getInstallerPackageName(packageInfo.packageName)));
        url.addQueryParameter("current", Integer.toString(packageInfo.versionCode));
        versionUrl = url.build();
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        log.debug("querying \"{}\"...", versionUrl);
        final Request.Builder request = new Request.Builder();
        request.url(versionUrl);
        request.header("Accept-Charset", "utf-8");
        final String userAgent = application.httpUserAgent();
        if (userAgent != null)
            request.header("User-Agent", userAgent);

        final Call call = Constants.HTTP_CLIENT.newCall(request.build());

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean abort = false;
                try {
                    final Response response = call.execute();
                    if (response.isSuccessful()) {
                        final long serverTime = response.headers().getDate("Date").getTime();
                        try (final BufferedReader reader = new BufferedReader(response.body().charStream())) {
                            abort = handleServerTime(serverTime);

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
                                    abort = handleLine(key);
                                    if (abort)
                                        break;
                                    continue;
                                }
                                final String value = split.next();
                                if (!split.hasNext()) {
                                    abort = handleProperty(key, value);
                                    if (abort)
                                        break;
                                    continue;
                                }
                                log.info("Ignoring line: {}", line);
                            }
                        }
                    }
                } catch (final Exception x) {
                    handleException(x);
                }
                if (!abort)
                    handleCatchAll();
            }
        });
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    private boolean handleLine(final String line) {
        final int serverVersionCode = Integer.parseInt(line.split("\\s+")[0]);
        log.info("according to \"" + versionUrl + "\", strongly recommended minimum app version is "
                + serverVersionCode);

        if (serverVersionCode > application.packageInfo().versionCode) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (isAdded())
                        createVersionAlertDialog().show();
                }
            });
            return true;
        }
        return false;
    }

    private boolean handleProperty(final String key, final String value) {
        if (key.equalsIgnoreCase("min.security_patch.bluetooth")) {
            final String minSecurityPatchLevel = value;
            log.info("according to \"{}\", minimum security patch level for bluetooth is {}", versionUrl,
                    minSecurityPatchLevel);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Build.VERSION.SECURITY_PATCH.compareTo(minSecurityPatchLevel) < 0) {
                final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null && BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isAdded())
                                createInsecureBluetoothAlertDialog(minSecurityPatchLevel).show();
                        }
                    });
                    return true;
                }
            }
        } else {
            log.info("Ignoring key: {}", key);
        }
        return false;
    }

    private boolean handleServerTime(final long serverTime) {
        if (serverTime > 0) {
            final long diffMinutes = Math.abs((System.currentTimeMillis() - serverTime) / DateUtils.MINUTE_IN_MILLIS);

            if (diffMinutes >= 60) {
                log.info("according to \"" + versionUrl + "\", system clock is off by " + diffMinutes + " minutes");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isAdded())
                            createTimeskewAlertDialog(diffMinutes).show();
                    }
                });
                return true;
            }
        }
        return false;
    }

    private boolean handleCatchAll() {
        final Intent stickyIntent = activity.registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
        if (stickyIntent != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (isAdded())
                        createLowStorageAlertDialog().show();
                }
            });
            return true;
        }
        return false;
    }

    private void handleException(final Exception x) {
        if (x instanceof UnknownHostException || x instanceof SocketException || x instanceof SocketTimeoutException) {
            // swallow
            log.debug("problem reading", x);
        } else {
            CrashReporter.saveBackgroundTrace(new RuntimeException(versionUrl.toString(), x),
                    application.packageInfo());
        }
    }

    private Dialog createTimeskewAlertDialog(final long diffMinutes) {
        final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);
        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.wallet_timeskew_dialog_title);
        dialog.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));
        if (packageManager.resolveActivity(settingsIntent, 0) != null) {
            dialog.setPositiveButton(R.string.button_settings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    try {
                        startActivity(settingsIntent);
                        activity.finish();
                    } catch (final Exception x) {
                        createSettingsFailedDialog(x.getMessage()).show();
                    }
                }
            });
        }
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createVersionAlertDialog() {
        final Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Constants.MARKET_APP_URL, application.packageInfo().packageName)));
        final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.wallet_version_dialog_title);
        final StringBuilder message = new StringBuilder(getString(R.string.wallet_version_dialog_msg));
        if (Build.VERSION.SDK_INT < Constants.SDK_DEPRECATED_BELOW)
            message.append("\n\n").append(getString(R.string.wallet_version_dialog_msg_deprecated));
        dialog.setMessage(message);

        if (packageManager.resolveActivity(marketIntent, 0) != null) {
            dialog.setPositiveButton(R.string.wallet_version_dialog_button_market,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int id) {
                            startActivity(marketIntent);
                            activity.finish();
                        }
                    });
        }

        if (packageManager.resolveActivity(binaryIntent, 0) != null) {
            dialog.setNeutralButton(R.string.wallet_version_dialog_button_binary,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int id) {
                            startActivity(binaryIntent);
                            activity.finish();
                        }
                    });
        }

        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createInsecureBluetoothAlertDialog(final String minSecurityPatch) {
        final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        final DialogBuilder dialog = DialogBuilder.warn(activity,
                R.string.alert_dialogs_fragment_insecure_bluetooth_title);
        dialog.setMessage(getString(R.string.alert_dialogs_fragment_insecure_bluetooth_message, minSecurityPatch));
        if (packageManager.resolveActivity(settingsIntent, 0) != null) {
            dialog.setPositiveButton(R.string.button_settings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    try {
                        startActivity(settingsIntent);
                        activity.finish();
                    } catch (final Exception x) {
                        createSettingsFailedDialog(x.getMessage()).show();
                    }
                }
            });
        }
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createLowStorageAlertDialog() {
        final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.wallet_low_storage_dialog_title);
        dialog.setMessage(R.string.wallet_low_storage_dialog_msg);
        if (packageManager.resolveActivity(settingsIntent, 0) != null) {
            dialog.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int id) {
                            try {
                                startActivity(settingsIntent);
                                activity.finish();
                            } catch (final Exception x) {
                                createSettingsFailedDialog(x.getMessage()).show();
                            }
                        }
                    });
        }
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createSettingsFailedDialog(final String exceptionMessage) {
        final DialogBuilder dialog = new DialogBuilder(activity);
        dialog.setTitle(R.string.alert_dialogs_fragment_settings_failed_title);
        dialog.setMessage(exceptionMessage);
        dialog.singleDismissButton(null);
        return dialog.create();
    }
}
