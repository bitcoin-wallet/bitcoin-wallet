/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.ui.preference;

import java.net.InetAddress;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.Spanned;

/**
 * @author Andreas Schildbach
 */
public final class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener {
    private Activity activity;
    private WalletApplication application;
    private Configuration config;
    private PackageManager pm;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Preference trustedPeerPreference;
    private Preference trustedPeerOnlyPreference;
    private EditTextPreference bluetoothAddressPreference;

    private static final int BLUETOOTH_ADDRESS_LENGTH = 6 * 2 + 5; // including the colons
    private static final Logger log = LoggerFactory.getLogger(SettingsFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
        this.application = (WalletApplication) activity.getApplication();
        this.config = application.getConfiguration();
        this.pm = activity.getPackageManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_settings);

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        trustedPeerPreference = findPreference(Configuration.PREFS_KEY_TRUSTED_PEER);
        trustedPeerPreference.setOnPreferenceChangeListener(this);

        trustedPeerOnlyPreference = findPreference(Configuration.PREFS_KEY_TRUSTED_PEER_ONLY);
        trustedPeerOnlyPreference.setOnPreferenceChangeListener(this);

        final Preference dataUsagePreference = findPreference(Configuration.PREFS_KEY_DATA_USAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            dataUsagePreference.setIntent(new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:" + application.getPackageName())));
        dataUsagePreference.setEnabled(pm.resolveActivity(dataUsagePreference.getIntent(), 0) != null);

        final Preference notificationsPreference = findPreference(Configuration.PREFS_KEY_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationsPreference.setIntent(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, application.getPackageName()));
            notificationsPreference.setEnabled(pm.resolveActivity(notificationsPreference.getIntent(), 0) != null);
        }

        bluetoothAddressPreference = (EditTextPreference) findPreference(Configuration.PREFS_KEY_BLUETOOTH_ADDRESS);
        bluetoothAddressPreference.setOnPreferenceChangeListener(this);
        bluetoothAddressPreference.getEditText().setFilters(
                new InputFilter[] { new InputFilter.LengthFilter(BLUETOOTH_ADDRESS_LENGTH),
                        new InputFilter.AllCaps(Locale.US), new RestrictToHex() });

        updateTrustedPeer();
        updateBluetoothAddress();
    }

    @Override
    public void onDestroy() {
        bluetoothAddressPreference.setOnPreferenceChangeListener(null);
        trustedPeerOnlyPreference.setOnPreferenceChangeListener(null);
        trustedPeerPreference.setOnPreferenceChangeListener(null);

        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        // delay action because preference isn't persisted until after this method returns
        handler.post(() -> {
            if (preference.equals(trustedPeerPreference))
                updateTrustedPeer();
            else if (preference.equals(bluetoothAddressPreference))
                updateBluetoothAddress();
        });
        return true;
    }

    private void updateTrustedPeer() {
        final String trustedPeer = config.getTrustedPeerHost();

        if (trustedPeer == null) {
            trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_summary);
            trustedPeerOnlyPreference.setEnabled(false);
        } else {
            trustedPeerPreference.setSummary(
                    trustedPeer + "\n[" + getString(R.string.preferences_trusted_peer_resolve_progress) + "]");
            trustedPeerOnlyPreference.setEnabled(true);

            new ResolveDnsTask(backgroundHandler) {
                @Override
                protected void onSuccess(final InetAddress address) {
                    trustedPeerPreference.setSummary(trustedPeer);
                    log.info("trusted peer '{}' resolved to {}", trustedPeer, address);
                }

                @Override
                protected void onUnknownHost() {
                    trustedPeerPreference.setSummary(trustedPeer + "\n["
                            + getString(R.string.preferences_trusted_peer_resolve_unknown_host) + "]");
                }
            }.resolve(trustedPeer);
        }
    }

    private void updateBluetoothAddress() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            String bluetoothAddress = Bluetooth.getAddress(bluetoothAdapter);
            if (bluetoothAddress == null)
                bluetoothAddress = config.getLastBluetoothAddress();
            if (bluetoothAddress != null) {
                bluetoothAddressPreference.setSummary(bluetoothAddress);
                bluetoothAddressPreference.setEnabled(false);
            } else {
                bluetoothAddress = config.getBluetoothAddress();
                if (bluetoothAddress != null) {
                    final String normalizedBluetoothAddress =
                            Bluetooth.decompressMac(Bluetooth.compressMac(bluetoothAddress)).toUpperCase(Locale.US);
                    bluetoothAddressPreference.setSummary(normalizedBluetoothAddress);
                }
            }
        } else {
            bluetoothAddressPreference.getParent().removePreference(bluetoothAddressPreference);
        }
    }

    private static class RestrictToHex implements InputFilter {
        @Override
        public CharSequence filter(final CharSequence source, final int start, final int end, final Spanned dest,
                                   final int dstart, final int dend) {
            final StringBuilder result = new StringBuilder();
            for (int i = start; i < end; i++) {
                final char c = source.charAt(i);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':')
                    result.append(c);
            }
            return result;
        }
    }
}
