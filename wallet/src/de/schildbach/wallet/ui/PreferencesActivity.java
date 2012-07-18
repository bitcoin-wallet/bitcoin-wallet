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

package de.schildbach.wallet.ui;

import java.io.File;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class PreferencesActivity extends PreferenceActivity
{
	private WalletApplication application;

	private static final String PREFS_KEY_INITIATE_RESET = "initiate_reset";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = (WalletApplication) getApplication();

		addPreferencesFromResource(R.xml.preferences);
	}

	@Override
	public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference)
	{
		final String key = preference.getKey();

		if (PREFS_KEY_INITIATE_RESET.equals(key))
		{
			final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setTitle(R.string.preferences_initiate_reset_title);
			dialog.setMessage(R.string.preferences_initiate_reset_dialog_message);
			dialog.setPositiveButton(R.string.preferences_initiate_reset_dialog_positive, new OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int which)
				{
					handleResetBlockchain();
					finish();
				}
			});
			dialog.setNegativeButton(R.string.preferences_initiate_reset_dialog_negative, null);
			dialog.show();

			return true;
		}

		return false;
	}

	private void handleResetBlockchain()
	{
		// stop service to make sure peers do not get in the way
		final Intent serviceIntent = new Intent(this, BlockchainServiceImpl.class);
		stopService(serviceIntent);

		// remove block chain
		final File blockChainFile = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE),
				Constants.BLOCKCHAIN_FILENAME);
		blockChainFile.delete();

		// clear transactions from wallet, keep keys
		application.getWallet().clearTransactions(0);

		// start service again
		startService(serviceIntent);
	}
}
