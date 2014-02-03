/*
 * Copyright 2011-2014 the original author or authors.
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.VersionMessage;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class AboutActivity extends SherlockPreferenceActivity
{
	private static final String KEY_ABOUT_VERSION = "about_version";
	private static final String KEY_ABOUT_LICENSE = "about_license";
	private static final String KEY_ABOUT_SOURCE = "about_source";
	private static final String KEY_ABOUT_MARKET_APP = "about_market_app";
	private static final String KEY_ABOUT_COMMUNITY_GOOGLEPLUS = "about_community_googleplus";
	private static final String KEY_ABOUT_AUTHOR_TWITTER = "about_author_twitter";
	private static final String KEY_ABOUT_AUTHOR_GOOGLEPLUS = "about_author_googleplus";
	private static final String KEY_ABOUT_CREDITS_BITCOINJ = "about_credits_bitcoinj";
	private static final String KEY_ABOUT_CREDITS_ZXING = "about_credits_zxing";

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.about);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		findPreference(KEY_ABOUT_VERSION).setSummary(((WalletApplication) getApplication()).packageInfo().versionName);
		findPreference(KEY_ABOUT_LICENSE).setSummary(Constants.LICENSE_URL);
		findPreference(KEY_ABOUT_SOURCE).setSummary(Constants.SOURCE_URL);
		findPreference(KEY_ABOUT_CREDITS_BITCOINJ).setTitle(getString(R.string.about_credits_litecoinj_title, VersionMessage.BITCOINJ_VERSION));
		findPreference(KEY_ABOUT_CREDITS_BITCOINJ).setSummary(Constants.CREDITS_BITCOINJ_URL);
		findPreference(KEY_ABOUT_CREDITS_ZXING).setSummary(Constants.CREDITS_ZXING_URL);

        if(GenericUtils.isBlackberry()) {
            Preference p = findPreference(KEY_ABOUT_MARKET_APP);
            p.setTitle(R.string.about_market_app_title_bb);
            p.setSummary(Constants.BB_WEBMARKET_APP_URL);
        }
        findPreference("about_processor").setSummary(System.getProperty("os.name"));
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference)
	{
		final String key = preference.getKey();
		if (KEY_ABOUT_LICENSE.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.LICENSE_URL)));
			finish();
		}
		else if (KEY_ABOUT_SOURCE.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.SOURCE_URL)));
			finish();
		}
		else if (KEY_ABOUT_MARKET_APP.equals(key))
		{
            final Intent marketIntent;
            if(GenericUtils.isBlackberry())
                marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.BB_MARKET_APP_URL, getPackageName())));
            else
                marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.ANDROID_MARKET_APP_URL, getPackageName())));

            if (getPackageManager().resolveActivity(marketIntent, 0) != null)
				startActivity(marketIntent);
			else {
                if(GenericUtils.isBlackberry())
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.BB_WEBMARKET_APP_URL, getPackageName()))));
                else
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.ANDROID_WEBMARKET_APP_URL, getPackageName()))));
            }
            finish();
		}
		else if (KEY_ABOUT_COMMUNITY_GOOGLEPLUS.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.COMMUNITY_GOOGLEPLUS_URL)));
			finish();
		}
		else if (KEY_ABOUT_AUTHOR_TWITTER.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.AUTHOR_TWITTER_URL)));
			finish();
		}
		else if (KEY_ABOUT_AUTHOR_GOOGLEPLUS.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.AUTHOR_GOOGLEPLUS_URL)));
			finish();
		}
		else if (KEY_ABOUT_CREDITS_BITCOINJ.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_BITCOINJ_URL)));
			finish();
		}
		else if (KEY_ABOUT_CREDITS_ZXING.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_ZXING_URL)));
			finish();
		}

		return false;
	}
}
