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
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.IBinder;
import android.preference.PreferenceManager;
import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class AutosyncService extends Service
{
	private static final long AUTOSYNC_INTERVAL = AlarmManager.INTERVAL_HOUR;

	private Context context;
	private SharedPreferences prefs;
	private AlarmManager alarmManager;
	private WifiManager wifiManager;

	private PendingIntent alarmIntent;
	private WifiLock wifiLock;

	@Override
	public void onCreate()
	{
		super.onCreate();

		context = getApplicationContext();

		prefs = PreferenceManager.getDefaultSharedPreferences(context);

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

		super.onDestroy();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		final String action = intent.getAction();

		if (Intent.ACTION_POWER_CONNECTED.equals(action))
		{
			final boolean autosync = prefs.getBoolean(Constants.PREFS_KEY_AUTOSYNC, false);

			if (autosync)
			{
				System.out.println("acquiring wifilock");
				wifiLock.acquire();

				final Intent serviceIntent = new Intent(context, BlockchainServiceImpl.class);

				context.startService(serviceIntent);

				alarmIntent = PendingIntent.getService(context, 0, serviceIntent, 0);
				alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AUTOSYNC_INTERVAL, alarmIntent);
			}
			else
			{
				cancelAlarm();

				System.out.println("releasing wifilock");
				wifiLock.release();
			}
		}
		else if (Intent.ACTION_POWER_DISCONNECTED.equals(action))
		{
			cancelAlarm();

			System.out.println("releasing wifilock");
			wifiLock.release();
		}

		return Service.START_STICKY;
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
