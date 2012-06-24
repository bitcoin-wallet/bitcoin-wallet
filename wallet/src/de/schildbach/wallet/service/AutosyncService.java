/*
 * Copyright 2011-2012 the original author or authors.
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

package de.schildbach.wallet.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.BatteryManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class AutosyncService extends Service implements OnSharedPreferenceChangeListener
{
	private Context context;
	private SharedPreferences prefs;
	private AlarmManager alarmManager;
	private WifiManager wifiManager;

	private Intent serviceIntent;
	private PendingIntent alarmIntent;
	private WifiLock wifiLock;

	private boolean isPowerConnected;
	private boolean isRunning;

	@Override
	public void onCreate()
	{
		super.onCreate();

		context = getApplicationContext();

		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.registerOnSharedPreferenceChangeListener(this);

		alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

		serviceIntent = new Intent(context, BlockchainServiceImpl.class);

		alarmIntent = PendingIntent.getService(context, 0, serviceIntent, 0);

		wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, Constants.LOCK_NAME);
		wifiLock.setReferenceCounted(false);

		// determine initial power connected state
		final Intent batteryChanged = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		final int batteryStatus = batteryChanged.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		isPowerConnected = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
	}

	@Override
	public IBinder onBind(final Intent intent)
	{
		return null;
	}

	@Override
	public void onDestroy()
	{
		alarmManager.cancel(alarmIntent);

		wifiLock.release();

		prefs.unregisterOnSharedPreferenceChangeListener(this);

		super.onDestroy();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		final String action = intent.getAction();
		if (Intent.ACTION_POWER_CONNECTED.equals(action))
			isPowerConnected = true;
		else if (Intent.ACTION_POWER_DISCONNECTED.equals(action))
			isPowerConnected = false;

		check();

		return Service.START_STICKY;
	}

	public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key)
	{
		if (Constants.PREFS_KEY_AUTOSYNC.equals(key))
			check();
	}

	private void check()
	{
		final long now = System.currentTimeMillis();

		final boolean prefsAutosync = prefs.getBoolean(Constants.PREFS_KEY_AUTOSYNC, false);
		final long prefsLastUsed = prefs.getLong(Constants.PREFS_KEY_LAST_USED, 0);

		final boolean shouldRunning = prefsAutosync && isPowerConnected;

		if (shouldRunning && !isRunning)
		{
			System.out.println("acquiring wifilock");
			wifiLock.acquire();

			context.startService(serviceIntent);

			final long lastUsedAgo = now - prefsLastUsed;
			final long alarmInterval;
			if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_JUST_MS)
				alarmInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
			else if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_RECENTLY_MS)
				alarmInterval = AlarmManager.INTERVAL_HOUR;
			else
				alarmInterval = AlarmManager.INTERVAL_HALF_DAY;

			alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, now, alarmInterval, alarmIntent);
		}
		else if (!shouldRunning && isRunning)
		{
			alarmManager.cancel(alarmIntent);

			System.out.println("releasing wifilock");
			wifiLock.release();
		}

		isRunning = shouldRunning;
	}
}
