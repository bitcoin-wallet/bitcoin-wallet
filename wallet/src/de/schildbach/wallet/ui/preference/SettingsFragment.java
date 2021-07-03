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

package de.schildbach.wallet.ui.preference;

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
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Bluetooth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;

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

    private EditTextPreference trustedPeerPreference;
    private Preference trustedPeerOnlyPreference;
    private Preference ownNamePreference;
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

        final ListPreference syncModePreference = (ListPreference) findPreference(Configuration.PREFS_KEY_SYNC_MODE);
        syncModePreference.setEntryValues(new CharSequence[] {
                Configuration.SyncMode.CONNECTION_FILTER.name(),
                Configuration.SyncMode.FULL.name() });
        syncModePreference.setEntries(new CharSequence[] {
                Html.fromHtml(getString(R.string.preferences_sync_mode_labels_connection_filter)),
                Html.fromHtml(getString(R.string.preferences_sync_mode_labels_full)) });
        if (!application.fullSyncCapable())
            removeOrDisablePreference(syncModePreference);

        trustedPeerPreference = (EditTextPreference) findPreference(Configuration.PREFS_KEY_TRUSTED_PEERS);
        trustedPeerPreference.setOnPreferenceChangeListener(this);
        trustedPeerPreference.setDialogMessage(getString(R.string.preferences_trusted_peer_dialog_message) + "\n\n" +
                getString(R.string.preferences_trusted_peer_dialog_message_multiple));

        trustedPeerOnlyPreference = findPreference(Configuration.PREFS_KEY_TRUSTED_PEERS_ONLY);
        trustedPeerOnlyPreference.setOnPreferenceChangeListener(this);

        final Preference enableExchangeRatesPreference = findPreference(Configuration.PREFS_KEY_ENABLE_EXCHANGE_RATES);
        if (!Constants.ENABLE_EXCHANGE_RATES)
            removeOrDisablePreference(enableExchangeRatesPreference);

        final Preference dataUsagePreference = findPreference(Configuration.PREFS_KEY_DATA_USAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            dataUsagePreference.setIntent(new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:" + application.getPackageName())));
        if (dataUsagePreference.getIntent() == null || pm.resolveActivity(dataUsagePreference.getIntent(), 0) == null)
            removeOrDisablePreference(dataUsagePreference);

        final Preference notificationsPreference = findPreference(Configuration.PREFS_KEY_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notificationsPreference.setIntent(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, application.getPackageName()));
        if (notificationsPreference.getIntent() == null || pm.resolveActivity(notificationsPreference.getIntent(), 0) == null)
            removeOrDisablePreference(notificationsPreference);

        ownNamePreference = findPreference(Configuration.PREFS_KEY_OWN_NAME);
        ownNamePreference.setOnPreferenceChangeListener(this);

        bluetoothAddressPreference = (EditTextPreference) findPreference(Configuration.PREFS_KEY_BLUETOOTH_ADDRESS);
        bluetoothAddressPreference.setOnPreferenceChangeListener(this);
        final InputFilter.AllCaps allCaps = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ?
                new InputFilter.AllCaps(Locale.US) : new InputFilter.AllCaps();
        final InputFilter.LengthFilter maxLength = new InputFilter.LengthFilter(BLUETOOTH_ADDRESS_LENGTH);
        final RestrictToHex hex = new RestrictToHex();
        bluetoothAddressPreference.getEditText().setFilters(new InputFilter[] { maxLength, allCaps, hex });
        bluetoothAddressPreference.getEditText().addTextChangedListener(colonFormat);

        updateTrustedPeer();
        updateOwnName();
        updateBluetoothAddress();
    }

    @Override
    public void onDestroy() {
        bluetoothAddressPreference.getEditText().removeTextChangedListener(colonFormat);
        bluetoothAddressPreference.setOnPreferenceChangeListener(null);
        ownNamePreference.setOnPreferenceChangeListener(null);
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
            else if (preference.equals(ownNamePreference))
                updateOwnName();
            else if (preference.equals(bluetoothAddressPreference))
                updateBluetoothAddress();
        });
        return true;
    }

    private void updateTrustedPeer() {
        final Set<HostAndPort> trustedPeers = config.getTrustedPeers();
        if (trustedPeers.isEmpty()) {
            trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_summary);
            trustedPeerOnlyPreference.setEnabled(false);
        } else {
            trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_resolve_progress);
            trustedPeerOnlyPreference.setEnabled(true);

            for (final HostAndPort trustedPeer : trustedPeers) {
                new ResolveDnsTask(backgroundHandler) {
                    @Override
                    protected void onSuccess(final HostAndPort hostAndPort, final InetSocketAddress socketAddress) {
                        appendToTrustedPeerSummary(Constants.CHAR_CHECKMARK + " " + hostAndPort);
                        log.info("trusted peer '{}' resolved to {}", hostAndPort,
                                socketAddress.getAddress().getHostAddress());
                    }

                    @Override
                    protected void onUnknownHost(final HostAndPort hostAndPort) {
                        appendToTrustedPeerSummary(Constants.CHAR_CROSSMARK + " " + hostAndPort + " – " +
                                getString(R.string.preferences_trusted_peer_resolve_unknown_host));
                        log.info("trusted peer '{}' unknown host", hostAndPort);
                    }
                }.resolve(trustedPeer);
            }
        }
    }

    private void appendToTrustedPeerSummary(final String line) {
        // This is a hack, because we're too lazy to implement a sophisticated UI here.
        synchronized (trustedPeerPreference) {
            CharSequence summary = trustedPeerPreference.getSummary();
            if (summary.equals(getString(R.string.preferences_trusted_peer_resolve_progress)))
                summary = "";
            else
                summary = summary + "\n";
            trustedPeerPreference.setSummary(summary + line);
        }
    }

    private void updateOwnName() {
        final String ownName = config.getOwnName();
        ownNamePreference.setSummary(ownName != null ? ownName : getText(R.string.preferences_own_name_summary));
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
                            Bluetooth.decompressMac(Bluetooth.compressMac(bluetoothAddress));
                    bluetoothAddressPreference.setSummary(normalizedBluetoothAddress);
                }
            }
        } else {
            removeOrDisablePreference(bluetoothAddressPreference);
        }
    }

    private void removeOrDisablePreference(final Preference preference) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            preference.getParent().removePreference(preference);
        else
            preference.setEnabled(false);
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

    private final TextWatcher colonFormat = new TextWatcher() {
        private boolean inFlight = false;

        @Override
        public void afterTextChanged(final Editable s) {
            if (inFlight)
                return;

            inFlight = true;
            for (int i = 0; i < s.length(); i++) {
                final boolean atColon = i % 3 == 2;
                final char c = s.charAt(i);
                if (atColon) {
                    if (c != ':')
                        s.insert(i, ":");
                } else {
                    if (c == ':')
                        s.delete(i, i + 1);
                }
            }
            inFlight = false;
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }
    };
}
