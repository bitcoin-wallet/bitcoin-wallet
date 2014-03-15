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

package de.langerhans.wallet.service;

import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import de.langerhans.wallet.WalletApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.preference.PreferenceManager;
import de.langerhans.wallet.Constants;
import de.langerhans.wallet.Configuration;

/**
 * @author Andreas Schildbach
 */
public class AutosyncReceiver extends BroadcastReceiver
{
	private static final Logger log = LoggerFactory.getLogger(AutosyncReceiver.class);
    private long prefsLastUsed = 0;
    private Context mCtx = null;

	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		log.info("got broadcast intent: " + intent);

        // Workaround because Android sometime sucks hard...
        if (intent.getAction() == null)
            intent.setAction("de.langerhans.wallet.AUTOSYNC_ACTION");

		// other app got replaced
		if (intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED))
            if (!intent.getDataString().equals("package:" + context.getPackageName()))
			    return;

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefsLastUsed = prefs.getLong(Configuration.PREFS_KEY_LAST_USED, 0);
        final boolean prefsAutosyncSwitch = prefs.getBoolean(Configuration.PREFS_KEY_AUTOSYNC_SWITCH, false);
        final boolean prefsAutosyncCharge = prefs.getBoolean(Configuration.PREFS_KEY_AUTOSYNC_CHARGE, false);
        final boolean prefsAutosyncWiFi = prefs.getBoolean(Configuration.PREFS_KEY_AUTOSYNC_WIFI, false);
        mCtx = context;

        // determine WiFi state.
        boolean wifiDontSync = false;
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (!mWifi.isConnected() && prefsAutosyncWiFi)
            wifiDontSync = true;

        // Don't blindly start sync because we got WiFi...
        if (intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION))
        {
            boolean justConnected = intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false);
            if (justConnected)
                wifiDontSync = true;
        }

        // determine power connected state
        boolean powerDontSync = false;
        int batteryStatus;
        final Intent batteryChanged = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryChanged != null)
            batteryStatus = batteryChanged.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        else
            //If we didn't receive an Intent from this, we assume the device is not charging. Will prevent false positives.
            batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING;

        boolean isPowerConnected = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
        if ((!isPowerConnected && prefsAutosyncCharge) || !prefsAutosyncCharge)
            powerDontSync = true;

        // We just booted
        boolean bootDontSync = intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED);

        if (!prefsAutosyncSwitch) //Case 1: Never AutoSync. We will check anyway later. Maybe the user changed their mind.
        {
            maybeStopService(context);
            WalletApplication.scheduleStartBlockchainService(context);
            return;
        }
        if (bootDontSync) //Case 2: We just booted, no need to sync yet. Check back later.
        {
            WalletApplication.scheduleStartBlockchainService(context);
            return;
        }
        if (wifiDontSync) //Case 3: We have no WiFi and the user doesn't want to sync. Check back later.
        {
            maybeStopService(context);
            WalletApplication.scheduleStartBlockchainService(context);
            return;
        }
        if (powerDontSync) //Case 4: No power and user wants only to sync on power. Check pack later.
        {
            maybeStopService(context);
            WalletApplication.scheduleStartBlockchainService(context);
            return;
        }

        // All other cases: We can sync now.
        final Intent serviceIntent = new Intent(context, BlockchainServiceImpl.class);
        context.startService(serviceIntent);
        WalletApplication.scheduleStartBlockchainService(context);
	}

    private void maybeStopService(Context context)
    {
        final Intent serviceIntent = new Intent(context, BlockchainServiceImpl.class);
        try
        {
            context.stopService(serviceIntent);
        } catch (Exception e)
        {
            log.debug("Tried to stop service which didn't run. Whatever :D");
        }
    }
}
