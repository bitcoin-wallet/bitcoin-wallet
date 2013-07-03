/*
 * Copyright 2011-2013 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class AutosyncReceiver extends BroadcastReceiver
{
	private static final Logger log = LoggerFactory.getLogger(AutosyncReceiver.class);

	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		log.info("got broadcast intent: " + intent);

		// other app got replaced
		if (intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED) && !intent.getDataString().equals("package:" + context.getPackageName()))
			return;

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final boolean prefsAutosync = prefs.getBoolean(Constants.PREFS_KEY_AUTOSYNC, true);
		final long prefsLastUsed = prefs.getLong(Constants.PREFS_KEY_LAST_USED, 0);

		// determine power connected state
		final Intent batteryChanged = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		final int batteryStatus = batteryChanged.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		boolean isPowerConnected = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL;

		final boolean running = prefsAutosync && isPowerConnected;

		final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		final Intent serviceIntent = new Intent(BlockchainService.ACTION_HOLD_WIFI_LOCK, null, context, BlockchainServiceImpl.class);
		final PendingIntent alarmIntent = PendingIntent.getService(context, 0, serviceIntent, 0);

		if (running)
		{
			context.startService(serviceIntent);

			final long now = System.currentTimeMillis();

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
		else
		{
			alarmManager.cancel(alarmIntent);
		}
	}
}
