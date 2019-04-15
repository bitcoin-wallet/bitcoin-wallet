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

import java.io.BufferedReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Installer;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient.Builder;
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
    private @Nullable Installer installer;

    private AlertDialogsViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(AlertDialogsFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.packageManager = activity.getPackageManager();
        this.installer = Installer.from(application);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(this).get(AlertDialogsViewModel.class);
        viewModel.showTimeskewAlertDialog.observe(this, new Event.Observer<Long>() {
            @Override
            public void onEvent(final Long diffMinutes) {
                log.info("showing timeskew alert dialog");
                createTimeskewAlertDialog(diffMinutes).show();
            }
        });
        viewModel.showVersionAlertDialog.observe(this, new Event.Observer<Void>() {
            @Override
            public void onEvent(final Void v) {
                log.info("showing version alert dialog");
                createVersionAlertDialog().show();
            }
        });
        viewModel.showInsecureBluetoothAlertDialog.observe(this, new Event.Observer<String>() {
            @Override
            public void onEvent(final String minSecurityPatchLevel) {
                log.info("showing insecure bluetooth alert dialog");
                createInsecureBluetoothAlertDialog(minSecurityPatchLevel).show();
            }
        });
        viewModel.showLowStorageAlertDialog.observe(this, new Event.Observer<Void>() {
            @Override
            public void onEvent(final Void v) {
                log.info("showing low storage alert dialog");
                createLowStorageAlertDialog().show();
            }
        });
        viewModel.showSettingsFailedDialog.observe(this, new Event.Observer<String>() {
            @Override
            public void onEvent(final String message) {
                log.info("showing settings failed dialog");
                createSettingsFailedDialog(message).show();
            }
        });

        if (savedInstanceState == null)
            process();
    }

    private void process() {
        final PackageInfo packageInfo = application.packageInfo();
        final HttpUrl.Builder url = Constants.VERSION_URL.newBuilder();
        url.addEncodedQueryParameter("package", packageInfo.packageName);
        final String installerPackageName = Installer.installerPackageName(application);
        if (installerPackageName != null)
            url.addEncodedQueryParameter("installer", installerPackageName);
        url.addQueryParameter("sdk", Integer.toString(Build.VERSION.SDK_INT));
        url.addQueryParameter("current", Integer.toString(packageInfo.versionCode));
        final HttpUrl versionUrl = url.build();

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    log.debug("querying \"{}\"...", versionUrl);
                    final Request.Builder request = new Request.Builder();
                    request.url(versionUrl);
                    request.header("Accept-Charset", "utf-8");
                    final String userAgent = application.httpUserAgent();
                    if (userAgent != null)
                        request.header("User-Agent", userAgent);

                    final Builder httpClientBuilder = Constants.HTTP_CLIENT.newBuilder();
                    httpClientBuilder.connectionSpecs(Arrays.asList(ConnectionSpec.RESTRICTED_TLS));
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
                                viewModel.showTimeskewAlertDialog.postValue(new Event<>(diffMinutes));
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
                        String version = null;
                        if (installer != null)
                            version = properties.get("version." + installer.name().toLowerCase(Locale.US));
                        if (version == null)
                            version = properties.get("version");
                        if (version == null)
                            version = properties.get(null);
                        if (version != null) {
                            log.info("according to \"{}\", strongly recommended minimum app version is \"{}\"",
                                    versionUrl, version);
                            final Integer recommendedVersionCode = Ints.tryParse(version);
                            if (recommendedVersionCode != null) {
                                if (recommendedVersionCode > application.packageInfo().versionCode) {
                                    viewModel.showVersionAlertDialog.postValue(Event.simple());
                                    return;
                                }
                            }
                        }

                        // Maybe show insecure bluetooth alert.
                        final String minSecurityPatchLevel = properties.get("min.security_patch.bluetooth");
                        if (minSecurityPatchLevel != null) {
                            log.info("according to \"{}\", minimum security patch level for bluetooth is {}",
                                    versionUrl, minSecurityPatchLevel);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                    && Build.VERSION.SECURITY_PATCH.compareTo(minSecurityPatchLevel) < 0) {
                                final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                                if (bluetoothAdapter != null && BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                                    viewModel.showInsecureBluetoothAlertDialog
                                            .postValue(new Event<>(minSecurityPatchLevel));
                                    return;
                                }
                            }
                        }

                        // Maybe show low storage alert.
                        final Intent stickyIntent = activity.registerReceiver(null,
                                new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
                        if (stickyIntent != null) {
                            viewModel.showLowStorageAlertDialog.postValue(Event.simple());
                            return;
                        }

                        log.info("all good, no alert dialog shown");
                    }
                } catch (final Exception x) {
                    if (x instanceof UnknownHostException || x instanceof SocketException
                            || x instanceof SocketTimeoutException) {
                        // swallow
                        log.debug("problem reading", x);
                    } else {
                        CrashReporter.saveBackgroundTrace(new RuntimeException(versionUrl.toString(), x),
                                application.packageInfo());
                        log.warn("problem parsing", x);
                    }
                }
            }
        });
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
                        viewModel.showSettingsFailedDialog.setValue(new Event<>(x.getMessage()));
                    }
                }
            });
        }
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createVersionAlertDialog() {
        final Installer installer = this.installer != null ? this.installer : Installer.F_DROID;
        final Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(installer.appStorePageFor(application).toString()));
        final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.wallet_version_dialog_title);
        final StringBuilder message = new StringBuilder(
                getString(R.string.wallet_version_dialog_msg, installer.displayName));
        if (Build.VERSION.SDK_INT < Constants.SDK_DEPRECATED_BELOW)
            message.append("\n\n").append(getString(R.string.wallet_version_dialog_msg_deprecated));
        dialog.setMessage(message);

        if (packageManager.resolveActivity(marketIntent, 0) != null) {
            dialog.setPositiveButton(installer.displayName, new DialogInterface.OnClickListener() {
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
                        viewModel.showSettingsFailedDialog.setValue(new Event<>(x.getMessage()));
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
                                viewModel.showSettingsFailedDialog.setValue(new Event<>(x.getMessage()));
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
