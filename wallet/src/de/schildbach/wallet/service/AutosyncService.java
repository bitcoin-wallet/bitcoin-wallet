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
	private static final long AUTOSYNC_INTERVAL = AlarmManager.INTERVAL_HOUR;

	private Context context;
	private SharedPreferences prefs;
	private AlarmManager alarmManager;
	private WifiManager wifiManager;

	private PendingIntent alarmIntent;
	private WifiLock wifiLock;

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

		wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, Constants.LOCK_NAME);
		wifiLock.setReferenceCounted(false);
	}

	@Override
	public IBinder onBind(final Intent intent)
	{
		return null;
	}

	@Override
	public void onDestroy()
	{
		cancelAlarm();

		wifiLock.release();

		prefs.unregisterOnSharedPreferenceChangeListener(this);

		super.onDestroy();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
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
		final Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		final int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		final boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

		final boolean prefsAutosync = prefs.getBoolean(Constants.PREFS_KEY_AUTOSYNC, false);

		final boolean shouldRunning = prefsAutosync && isCharging;

		if (shouldRunning && !isRunning)
		{
			System.out.println("acquiring wifilock");
			wifiLock.acquire();

			final Intent serviceIntent = new Intent(context, BlockchainServiceImpl.class);

			context.startService(serviceIntent);

			alarmIntent = PendingIntent.getService(context, 0, serviceIntent, 0);
			alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AUTOSYNC_INTERVAL, alarmIntent);
		}
		else if (!shouldRunning && isRunning)
		{
			cancelAlarm();

			System.out.println("releasing wifilock");
			wifiLock.release();
		}

		isRunning = shouldRunning;
	}

	private void cancelAlarm()
	{
		if (alarmIntent != null)
		{
			alarmManager.cancel(alarmIntent);
			alarmIntent = null;
		}
	}
}
