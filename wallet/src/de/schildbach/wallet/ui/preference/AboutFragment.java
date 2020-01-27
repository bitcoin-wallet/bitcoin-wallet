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
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import androidx.annotation.Nullable;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.util.Installer;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.VersionMessage;

import java.io.IOException;

/**
 * @author Andreas Schildbach
 */
public final class AboutFragment extends PreferenceFragment {
    private WalletApplication application;
    private PackageManager packageManager;

    private static final String KEY_ABOUT_VERSION = "about_version";
    private static final String KEY_ABOUT_MARKET_APP = "about_market_app";
    private static final String KEY_ABOUT_CREDITS_BITCOINJ = "about_credits_bitcoinj";

    public static class ApkHashFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            CharSequence message;
            try {
                final WalletApplication application = (WalletApplication) getActivity().getApplication();
                message = WalletUtils.formatHash(application.apkHash().toString(), 4, 0);
            } catch (final IOException x) {
                message = "n/a";
            }
            final DialogBuilder dialog = DialogBuilder.dialog(getActivity(), R.string.about_version_apk_hash_title, message);
            dialog.singleDismissButton(null);
            return dialog.create();
        }
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.application = (WalletApplication) activity.getApplication();
        this.packageManager = activity.getPackageManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_about);

        final PackageInfo packageInfo = application.packageInfo();
        final Preference versionPref = findPreference(KEY_ABOUT_VERSION);
        versionPref.setSummary(WalletApplication.versionLine(packageInfo));
        versionPref.setOnPreferenceClickListener(preference -> {
            new ApkHashFragment().show(getFragmentManager(), null);
            return true;
        });

        Installer installer = Installer.from(application);
        if (installer == null)
            installer = Installer.F_DROID;
        final Preference marketPref = findPreference(KEY_ABOUT_MARKET_APP);
        marketPref.setTitle(getString(R.string.about_market_app_title, installer.displayName));
        final Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(installer.appStorePageFor(application).toString()));
        if (packageManager.resolveActivity(marketIntent, 0) != null) {
            marketPref.setIntent(marketIntent);
            marketPref.setEnabled(true);
        }
        findPreference(KEY_ABOUT_CREDITS_BITCOINJ)
                .setTitle(getString(R.string.about_credits_bitcoinj_title, VersionMessage.BITCOINJ_VERSION));
    }
}
