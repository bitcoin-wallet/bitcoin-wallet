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
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.util.Installer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private PackageManager packageManager;
    private Configuration config;

    private AlertDialogsViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(AlertDialogsFragment.class);

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // do nothing
            });

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.packageManager = activity.getPackageManager();
        this.config = activity.getWalletApplication().getConfiguration();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AlertDialogsViewModel.class);
        viewModel.showTimeskewAlertDialog.observe(this, new Event.Observer<Long>() {
            @Override
            protected void onEvent(final Long diffMinutes) {
                log.info("showing timeskew alert dialog");
                createTimeskewAlertDialog(diffMinutes).show();
            }
        });
        viewModel.showVersionAlertDialog.observe(this, new Event.Observer<Installer>() {
            @Override
            protected void onEvent(final Installer market) {
                log.info("showing version alert dialog");
                createVersionAlertDialog(market).show();
            }
        });
        viewModel.showInsecureDeviceAlertDialog.observe(this, new Event.Observer<String>() {
            @Override
            protected void onEvent(final String minSecurityPatchLevel) {
                log.info("showing insecure device alert dialog");
                createInsecureDeviceAlertDialog(minSecurityPatchLevel).show();
            }
        });
        viewModel.showLowStorageAlertDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                log.info("showing low storage alert dialog");
                createLowStorageAlertDialog().show();
            }
        });
        viewModel.showSettingsFailedDialog.observe(this, new Event.Observer<String>() {
            @Override
            protected void onEvent(final String message) {
                log.info("showing settings failed dialog");
                createSettingsFailedDialog(message).show();
            }
        });
        viewModel.showTooMuchBalanceAlertDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                log.info("showing too much balance dialog");
                createTooMuchBalanceAlertDialog().show();
            }
        });
        viewModel.showBatteryOptimizationDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                log.info("showing battery optimization dialog");
                createBatteryOptimizationDialog().show();
            }
        });
        viewModel.startBatteryOptimizationActivity.observe(this, v ->
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + activity.getPackageName())))
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.requestNotificationPermissionDialog.observe(this, v ->
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            );
        }
    }

    private Dialog createTimeskewAlertDialog(final long diffMinutes) {
        final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);
        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.wallet_timeskew_dialog_title,
                R.string.wallet_timeskew_dialog_msg, diffMinutes);
        if (packageManager.resolveActivity(settingsIntent, 0) != null) {
            dialog.setPositiveButton(R.string.button_settings, (d, id) -> {
                try {
                    startActivity(settingsIntent);
                    activity.finish();
                } catch (final Exception x) {
                    viewModel.showSettingsFailedDialog.setValue(new Event<>(x.getMessage()));
                }
            });
        }
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createVersionAlertDialog(final Installer market) {
        final Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(market.appStorePageFor(activity).toString()));
        final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

        final StringBuilder message = new StringBuilder(
                getString(R.string.wallet_version_dialog_msg, market.displayName));
        if (Build.VERSION.SDK_INT < Constants.SDK_DEPRECATED_BELOW)
            message.append("\n\n").append(getString(R.string.wallet_version_dialog_msg_deprecated));
        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.wallet_version_dialog_title, message);

        if (packageManager.resolveActivity(marketIntent, 0) != null) {
            dialog.setPositiveButton(market.displayName, (d, id) -> {
                startActivity(marketIntent);
                activity.finish();
            });
        }

        if (packageManager.resolveActivity(binaryIntent, 0) != null) {
            dialog.setNeutralButton(R.string.wallet_version_dialog_button_binary,
                    (d, id) -> {
                        startActivity(binaryIntent);
                        activity.finish();
                    });
        }

        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createInsecureDeviceAlertDialog(final String minSecurityPatch) {
        final DialogBuilder dialog = DialogBuilder.warn(activity,
                R.string.alert_dialogs_fragment_insecure_device_title,
                R.string.wallet_balance_fragment_insecure_device);
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createLowStorageAlertDialog() {
        final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.wallet_low_storage_dialog_title,
                R.string.wallet_low_storage_dialog_msg);
        if (packageManager.resolveActivity(settingsIntent, 0) != null) {
            dialog.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps,
                    (d, id) -> {
                        try {
                            startActivity(settingsIntent);
                            activity.finish();
                        } catch (final Exception x) {
                            viewModel.showSettingsFailedDialog.setValue(new Event<>(x.getMessage()));
                        }
                    });
        }
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createSettingsFailedDialog(final String exceptionMessage) {
        final DialogBuilder dialog = DialogBuilder.dialog(activity,
                R.string.alert_dialogs_fragment_settings_failed_title, exceptionMessage);
        dialog.singleDismissButton(null);
        return dialog.create();
    }

    private Dialog createTooMuchBalanceAlertDialog() {
        final DialogBuilder dialog = DialogBuilder.dialog(activity,
                R.string.alert_dialogs_fragment_too_much_balance_dialog_title,
                R.string.alert_dialogs_fragment_too_much_balance_dialog_message);
        dialog.singleDismissButton(null);
        return dialog.create();
    }

    private Dialog createBatteryOptimizationDialog() {
        final DialogBuilder dialog = DialogBuilder.dialog(activity,
                R.string.alert_dialogs_fragment_battery_optimization_dialog_title,
                R.string.alert_dialogs_fragment_battery_optimization_dialog_message);
        dialog.setPositiveButton(R.string.alert_dialogs_fragment_battery_optimization_dialog_button_allow,
                (d, which) -> viewModel.handleBatteryOptimizationDialogPositiveButton());
        dialog.setNegativeButton(R.string.button_dismiss,
                (d, which) -> viewModel.handleBatteryOptimizationDialogNegativeButton());
        return dialog.create();
    }
}
