/*
 * Copyright 2013-2014 the original author or authors.
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

package de.schildbach.wallet.offline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.format.DateUtils;

import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class AcceptBluetoothService extends Service
{
	private WalletApplication application;
	private WakeLock wakeLock;
	private AcceptBluetoothThread acceptBluetoothThread;

	private long serviceCreatedAt;

	private final Handler handler = new Handler();

	private static final long TIMEOUT_MS = 5 * DateUtils.MINUTE_IN_MILLIS;

	private static final Logger log = LoggerFactory.getLogger(AcceptBluetoothService.class);

	@Override
	public IBinder onBind(final Intent intent)
	{
		return null;
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		handler.removeCallbacks(timeoutRunnable);
		handler.postDelayed(timeoutRunnable, TIMEOUT_MS);

		return START_NOT_STICKY;
	}

	@Override
	public void onCreate()
	{
		serviceCreatedAt = System.currentTimeMillis();
		log.debug(".onCreate()");

		super.onCreate();

		application = (WalletApplication) getApplication();

		final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		final Wallet wallet = application.getWallet();

		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + " bluetooth transaction submission");
		wakeLock.acquire();

		registerReceiver(bluetoothStateChangeReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

		acceptBluetoothThread = new AcceptBluetoothThread(bluetoothAdapter)
		{
			@Override
			public boolean handleTx(final byte[] msg)
			{
				try
				{
					final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, msg);
					log.info("tx " + tx.getHashAsString() + " arrived via blueooth");

					try
					{
						if (wallet.isTransactionRelevant(tx))
						{
							wallet.receivePending(tx, null);

							handler.post(new Runnable()
							{
								@Override
								public void run()
								{
									application.broadcastTransaction(tx);
								}
							});
						}
						else
						{
							log.info("tx " + tx.getHashAsString() + " irrelevant");
						}

						return true;
					}
					catch (final VerificationException x)
					{
						log.info("cannot verify tx " + tx.getHashAsString() + " received via bluetooth", x);
					}
				}
				catch (final ProtocolException x)
				{
					log.info("cannot decode message received via bluetooth", x);
				}

				return false;
			}
		};

		acceptBluetoothThread.start();
	}

	@Override
	public void onDestroy()
	{
		acceptBluetoothThread.stopAccepting();

		unregisterReceiver(bluetoothStateChangeReceiver);

		wakeLock.release();

		handler.removeCallbacksAndMessages(null);

		super.onDestroy();

		log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
	}

	private final BroadcastReceiver bluetoothStateChangeReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);

			if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF)
			{
				log.info("bluetooth was turned off, stopping service");

				stopSelf();
			}
		}
	};

	private final Runnable timeoutRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			log.info("timeout expired, stopping service");

			stopSelf();
		}
	};
}
