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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class AutosyncReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		final String action = intent.getAction();

		if (Intent.ACTION_POWER_CONNECTED.equals(action))
		{
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			final boolean autosync = prefs.getBoolean(Constants.PREFS_KEY_AUTOSYNC, false);

			if (autosync)
				context.startService(new Intent(context, BlockchainService.class));
		}
		else if (Intent.ACTION_POWER_DISCONNECTED.equals(action))
		{
			context.stopService(new Intent(context, BlockchainService.class));
		}
	}
}
