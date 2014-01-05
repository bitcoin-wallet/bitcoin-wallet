/*
 * Copyright 2012-2014 the original author or authors.
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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import de.schildbach.wallet.util.Bluetooth;

/**
 * @author Shahar Livne
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public abstract class AcceptBluetoothThread extends Thread
{
	private final BluetoothServerSocket listeningSocket;
	private final AtomicBoolean running = new AtomicBoolean(true);

	private static final Logger log = LoggerFactory.getLogger(AcceptBluetoothThread.class);

	public AcceptBluetoothThread(@Nonnull final BluetoothAdapter adapter)
	{
		try
		{
			this.listeningSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("Bitcoin Transaction Submission", Bluetooth.BLUETOOTH_UUID);
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	@Override
	public void run()
	{
		while (running.get())
		{
			BluetoothSocket socket = null;
			DataInputStream is = null;
			DataOutputStream os = null;

			try
			{
				// start a blocking call, and return only on success or exception
				socket = listeningSocket.accept();

				is = new DataInputStream(socket.getInputStream());
				os = new DataOutputStream(socket.getOutputStream());

				final int numMessages = is.readInt();
				boolean ack = true;

				for (int i = 0; i < numMessages; i++)
				{
					final int msgLength = is.readInt();
					final byte[] msg = new byte[msgLength];
					is.readFully(msg);

					if (!handleTx(msg))
						ack = false;
				}

				os.writeBoolean(ack);
			}
			catch (final IOException x)
			{
				log.info("exception in bluetooth accept loop", x);
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
	}

	public void stopAccepting()
	{
		running.set(false);

		try
		{
			listeningSocket.close();
		}
		catch (final IOException x)
		{
			// swallow
		}
	}

	protected abstract boolean handleTx(@Nonnull byte[] msg);
}
