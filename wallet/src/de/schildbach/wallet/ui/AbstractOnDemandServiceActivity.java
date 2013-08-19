/*
 * Copyright 2013 the original author or authors.
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.google.bitcoin.core.Transaction;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;

/**
 * @author Andreas Schildbach
 */
public class AbstractOnDemandServiceActivity extends AbstractWalletActivity
{
	protected void broadcastTransaction(final Transaction tx)
	{
		getWalletApplication().startBlockchainService(false); // make sure service will run for a while

		bindService(new Intent(this, BlockchainServiceImpl.class), new ServiceConnection()
		{
			public void onServiceConnected(final ComponentName name, final IBinder binder)
			{
				final BlockchainService blockchainService = ((BlockchainServiceImpl.LocalBinder) binder).getService();

				blockchainService.broadcastTransaction(tx);
			}

			public void onServiceDisconnected(final ComponentName name)
			{
				unbindService(this);
			}
		}, Context.BIND_AUTO_CREATE);
	}
}
