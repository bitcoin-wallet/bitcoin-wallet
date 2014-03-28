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

package de.schildbach.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import de.schildbach.wallet.WalletApplication;

/**
 * @author Andreas Schildbach
 */
public class AutosyncReceiver extends BroadcastReceiver
{
	private static final Logger log = LoggerFactory.getLogger(AutosyncReceiver.class);

	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		log.info("got broadcast: " + intent);

		if (!Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction()) || intent.getDataString().equals("package:" + context.getPackageName()))
		{
			// make sure wallet is upgraded to HD
			UpgradeWalletService.startUpgrade(context);
		}

		// make sure there is always an alarm scheduled
		WalletApplication.scheduleStartBlockchainService(context);
	}
}
