/*
 * Copyright 2014 the original author or authors.
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

package de.schildbach.wallet;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.text.format.DateUtils;

/**
 * @author Andreas Schildbach
 */
public class Configuration
{
	public final int lastVersionCode;

	private final SharedPreferences prefs;

	public static final String PREFS_KEY_BTC_PRECISION = "btc_precision";
	public static final String PREFS_KEY_CONNECTIVITY_NOTIFICATION = "connectivity_notification";
	public static final String PREFS_KEY_EXCHANGE_CURRENCY = "exchange_currency";
	public static final String PREFS_KEY_TRUSTED_PEER = "trusted_peer";
	public static final String PREFS_KEY_TRUSTED_PEER_ONLY = "trusted_peer_only";
	public static final String PREFS_KEY_DISCLAIMER = "disclaimer";
	public static final String PREFS_KEY_SELECTED_ADDRESS = "selected_address";
	public static final String PREFS_KEY_LABS_BLUETOOTH_OFFLINE_TRANSACTIONS = "labs_bluetooth_offline_transactions";

	private static final String PREFS_KEY_LAST_VERSION = "last_version";
	private static final String PREFS_KEY_LAST_USED = "last_used";
	private static final String PREFS_KEY_BEST_CHAIN_HEIGHT_EVER = "best_chain_height_ever";
	public static final String PREFS_KEY_REMIND_BACKUP = "remind_backup";

	private static final String PREFS_DEFAULT_BTC_PRECISION = "4";

	private static final Logger log = LoggerFactory.getLogger(Configuration.class);

	public Configuration(@Nonnull final SharedPreferences prefs)
	{
		this.prefs = prefs;

		this.lastVersionCode = prefs.getInt(PREFS_KEY_LAST_VERSION, 0);
	}

	public int getBtcPrecision()
	{
		final String precision = prefs.getString(PREFS_KEY_BTC_PRECISION, PREFS_DEFAULT_BTC_PRECISION);
		return precision.charAt(0) - '0';
	}

	public int getBtcMaxPrecision()
	{
		return getBtcShift() == 0 ? Constants.BTC_MAX_PRECISION : Constants.MBTC_MAX_PRECISION;
	}

	public int getBtcShift()
	{
		final String precision = prefs.getString(PREFS_KEY_BTC_PRECISION, PREFS_DEFAULT_BTC_PRECISION);
		return precision.length() == 3 ? precision.charAt(2) - '0' : 0;
	}

	public String getBtcPrefix()
	{
		return getBtcShift() == 0 ? Constants.CURRENCY_CODE_BTC : Constants.CURRENCY_CODE_MBTC;
	}

	public boolean getConnectivityNotificationEnabled()
	{
		return prefs.getBoolean(PREFS_KEY_CONNECTIVITY_NOTIFICATION, false);
	}

	public String getTrustedPeerHost()
	{
		return prefs.getString(PREFS_KEY_TRUSTED_PEER, "").trim();
	}

	public boolean getTrustedPeerOnly()
	{
		return prefs.getBoolean(PREFS_KEY_TRUSTED_PEER_ONLY, false);
	}

	public boolean remindBackup()
	{
		return prefs.getBoolean(PREFS_KEY_REMIND_BACKUP, true);
	}

	public void armBackupReminder()
	{
		prefs.edit().putBoolean(PREFS_KEY_REMIND_BACKUP, true).commit();
	}

	public void disarmBackupReminder()
	{
		prefs.edit().putBoolean(PREFS_KEY_REMIND_BACKUP, false).commit();
	}

	public boolean getDisclaimerEnabled()
	{
		return prefs.getBoolean(PREFS_KEY_DISCLAIMER, true);
	}

	public String getSelectedAddress()
	{
		return prefs.getString(PREFS_KEY_SELECTED_ADDRESS, null);
	}

	public void setSelectedAddress(final String address)
	{
		prefs.edit().putString(PREFS_KEY_SELECTED_ADDRESS, address).commit();
	}

	public String getExchangeCurrencyCode()
	{
		return prefs.getString(PREFS_KEY_EXCHANGE_CURRENCY, null);
	}

	public void setExchangeCurrencyCode(final String exchangeCurrencyCode)
	{
		prefs.edit().putString(PREFS_KEY_EXCHANGE_CURRENCY, exchangeCurrencyCode).commit();
	}

	public boolean getBluetoothOfflineTransactionsEnabled()
	{
		return prefs.getBoolean(PREFS_KEY_LABS_BLUETOOTH_OFFLINE_TRANSACTIONS, false);
	}

	public boolean versionCodeCrossed(final int currentVersionCode, final int triggeringVersionCode)
	{
		final boolean wasBelow = lastVersionCode < triggeringVersionCode;
		final boolean wasUsedBefore = lastVersionCode > 0;
		final boolean isNowAbove = currentVersionCode >= triggeringVersionCode;

		return wasUsedBefore && wasBelow && isNowAbove;
	}

	public void updateLastVersionCode(final int currentVersionCode)
	{
		prefs.edit().putInt(PREFS_KEY_LAST_VERSION, currentVersionCode).commit();

		if (currentVersionCode > lastVersionCode)
			log.info("detected app upgrade: " + lastVersionCode + " -> " + currentVersionCode);
		else if (currentVersionCode < lastVersionCode)
			log.warn("detected app downgrade: " + lastVersionCode + " -> " + currentVersionCode);
	}

	public long getLastUsedAgo()
	{
		final long now = System.currentTimeMillis();

		return now - prefs.getLong(PREFS_KEY_LAST_USED, 0);
	}

	public void touchLastUsed()
	{
		final long prefsLastUsed = prefs.getLong(PREFS_KEY_LAST_USED, 0);
		final long now = System.currentTimeMillis();
		prefs.edit().putLong(PREFS_KEY_LAST_USED, now).commit();

		log.info("just being used - last used {} minutes ago", (now - prefsLastUsed) / DateUtils.MINUTE_IN_MILLIS);
	}

	public int getBestChainHeightEver()
	{
		return prefs.getInt(PREFS_KEY_BEST_CHAIN_HEIGHT_EVER, 0);
	}

	public void setBestChainHeightEver(final int bestChainHeightEver)
	{
		prefs.edit().putInt(PREFS_KEY_BEST_CHAIN_HEIGHT_EVER, bestChainHeightEver).commit();
	}

	public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener)
	{
		prefs.registerOnSharedPreferenceChangeListener(listener);
	}

	public void unregisterOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener)
	{
		prefs.unregisterOnSharedPreferenceChangeListener(listener);
	}
}
