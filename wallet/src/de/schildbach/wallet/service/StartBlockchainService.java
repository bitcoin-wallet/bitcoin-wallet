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

package de.schildbach.wallet.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.text.format.DateUtils;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class StartBlockchainService extends JobService {
    private PowerManager pm;

    private static final Logger log = LoggerFactory.getLogger(StartBlockchainService.class);

    public static void schedule(final WalletApplication application, final boolean expectLargeData) {
        final Configuration config = application.getConfiguration();
        final long lastUsedAgo = config.getLastUsedAgo();

        // apply some backoff
        final long interval;
        if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_JUST_MS)
            interval = DateUtils.MINUTE_IN_MILLIS * 15;
        else if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_TODAY_MS)
            interval = DateUtils.HOUR_IN_MILLIS;
        else if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_RECENTLY_MS)
            interval = DateUtils.DAY_IN_MILLIS / 2;
        else
            interval = DateUtils.DAY_IN_MILLIS;

        log.info("last used {} minutes ago{}, rescheduling block chain sync in roughly {} minutes",
                lastUsedAgo / DateUtils.MINUTE_IN_MILLIS, expectLargeData ? " and expecting large data" : "",
                interval / DateUtils.MINUTE_IN_MILLIS);

        final JobScheduler jobScheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        final JobInfo.Builder jobInfo = new JobInfo.Builder(0, new ComponentName(application,
                StartBlockchainService.class));
        jobInfo.setMinimumLatency(interval);
        jobInfo.setOverrideDeadline(DateUtils.WEEK_IN_MILLIS);
        jobInfo.setRequiredNetworkType(expectLargeData ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY);
        jobInfo.setRequiresDeviceIdle(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            jobInfo.setRequiresBatteryNotLow(true);
            jobInfo.setRequiresStorageNotLow(true);
        }
        jobScheduler.schedule(jobInfo.build());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final boolean storageLow = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW)) != null;
        final boolean batteryLow = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_LOW)) != null;
        final boolean powerSaveMode = pm.isPowerSaveMode();
        if (storageLow)
            log.info("storage low, not starting block chain sync");
        if (batteryLow)
            log.info("battery low, not starting block chain sync");
        if (powerSaveMode)
            log.info("power save mode, not starting block chain sync");
        if (!storageLow && !batteryLow && !powerSaveMode)
            BlockchainService.start(this, false);
        return false;
    }

    @Override
    public boolean onStopJob(final JobParameters params) {
        return false;
    }
}
