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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.preference;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener
{
	private Activity activity;
	private WalletApplication application;
	private PackageManager pm;

	private final Handler handler = new Handler();

	private Preference btcPrecisionPreference;
	private Preference trustedPeerPreference;
	private Preference trustedPeerOnlyPreference;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
		this.application = (WalletApplication) activity.getApplication();
		this.pm = activity.getPackageManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preference_settings);

		btcPrecisionPreference = findPreference(Configuration.PREFS_KEY_BTC_PRECISION);
		btcPrecisionPreference.setOnPreferenceChangeListener(this);

		trustedPeerPreference = findPreference(Configuration.PREFS_KEY_TRUSTED_PEER);
		trustedPeerPreference.setOnPreferenceChangeListener(this);

		trustedPeerOnlyPreference = findPreference(Configuration.PREFS_KEY_TRUSTED_PEER_ONLY);
		trustedPeerOnlyPreference.setOnPreferenceChangeListener(this);

		final Preference dataUsagePreference = findPreference(Configuration.PREFS_KEY_DATA_USAGE);
		dataUsagePreference.setEnabled(pm.resolveActivity(dataUsagePreference.getIntent(), 0) != null);

		final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		final String trustedPeer = prefs.getString(Configuration.PREFS_KEY_TRUSTED_PEER, "").trim();
		updateTrustedPeer(trustedPeer);
	}

	@Override
	public void onDestroy()
	{
		trustedPeerOnlyPreference.setOnPreferenceChangeListener(null);
		trustedPeerPreference.setOnPreferenceChangeListener(null);
		btcPrecisionPreference.setOnPreferenceChangeListener(null);

		super.onDestroy();
	}

	@Override
	public boolean onPreferenceChange(final Preference preference, final Object newValue)
	{
		// delay action because preference isn't persisted until after this method returns
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				if (preference.equals(btcPrecisionPreference))
				{
					WalletBalanceWidgetProvider.updateWidgets(activity, application.getWallet());
				}
				else if (preference.equals(trustedPeerPreference))
				{
					application.stopBlockchainService();
					updateTrustedPeer((String) newValue);
				}
				else if (preference.equals(trustedPeerOnlyPreference))
				{
					application.stopBlockchainService();
				}
			}
		});

		return true;
	}

	private void updateTrustedPeer(final String trustedPeer)
	{
		if (trustedPeer.isEmpty())
		{
			trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_summary);
			trustedPeerOnlyPreference.setEnabled(false);
		}
		else
		{
			trustedPeerPreference.setSummary(trustedPeer);
			trustedPeerOnlyPreference.setEnabled(true);
		}
	}
}
