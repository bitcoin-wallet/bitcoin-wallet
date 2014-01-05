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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import com.google.bitcoin.core.Transaction;

import de.schildbach.wallet.util.Bluetooth;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public abstract class SendBluetoothTask
{
	private final BluetoothAdapter bluetoothAdapter;
	private Handler backgroundHandler;
	private final Handler callbackHandler;

	private static final Logger log = LoggerFactory.getLogger(SendBluetoothTask.class);

	public SendBluetoothTask(@Nonnull final BluetoothAdapter bluetoothAdapter, @Nonnull final Handler backgroundHandler)
	{
		this.bluetoothAdapter = bluetoothAdapter;
		this.backgroundHandler = backgroundHandler;
		this.callbackHandler = new Handler(Looper.myLooper());
	}

	public void send(@Nonnull final String bluetoothMac, @Nonnull final Transaction transaction)
	{
		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				log.info("trying to send tx " + transaction.getHashAsString() + " via bluetooth");

				final byte[] serializedTx = transaction.unsafeBitcoinSerialize();

				BluetoothSocket socket = null;
				DataOutputStream os = null;
				DataInputStream is = null;

				try
				{
					final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(Bluetooth.decompressMac(bluetoothMac));
					socket = device.createInsecureRfcommSocketToServiceRecord(Bluetooth.BLUETOOTH_UUID);

					socket.connect();
					is = new DataInputStream(socket.getInputStream());
					os = new DataOutputStream(socket.getOutputStream());

					os.writeInt(1);
					os.writeInt(serializedTx.length);
					os.write(serializedTx);
					os.flush();

					log.info("tx " + transaction.getHashAsString() + " sent via bluetooth");

					final boolean ack = is.readBoolean();

					log.info("received " + (ack ? "ack" : "nack"));

					callbackHandler.post(new Runnable()
					{
						@Override
						public void run()
						{
							onResult(ack);
						}
					});
				}
				catch (final IOException x)
				{
					log.info("problem sending", x);
				}
				finally
				{
					if (os != null)
					{
						try
						{
							os.close();
						}
						catch (final IOException x)
						{
							// swallow
						}
					}

					if (is != null)
					{
						try
						{
							is.close();
						}
						catch (final IOException x)
						{
							// swallow
						}
					}

					if (socket != null)
					{
						try
						{
							socket.close();
						}
						catch (final IOException x)
						{
							// swallow
						}
					}
				}
			}
		});
	}

	protected abstract void onResult(boolean ack);
}
