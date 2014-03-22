/*
 * Copyright 2014 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import de.schildbach.wallet.util.Bluetooth;

/**
 * @author Andreas Schildbach
 */
public final class ServeBluetoothThread extends Thread
{
	private final BluetoothServerSocket listeningSocket;
	private final AtomicBoolean running = new AtomicBoolean(true);

	private static final Logger log = LoggerFactory.getLogger(ServeBluetoothThread.class);

	private AtomicReference<byte[]> paymentRequestRef = new AtomicReference<byte[]>();

	public ServeBluetoothThread(@Nonnull final BluetoothAdapter adapter)
	{
		this.listeningSocket = listen(adapter, Bluetooth.BLUETOOTH_UUID_PAYMENT_REQUESTS);
	}

	public void setPaymentRequest(final byte[] paymentRequest)
	{
		paymentRequestRef.set(paymentRequest);
	}

	@Override
	public void run()
	{
		while (running.get())
		{
			BluetoothSocket socket = null;
			InputStream is = null;
			OutputStream os = null;

			try
			{
				// start a blocking call, and return only on success or exception
				socket = listeningSocket.accept();

				log.info("accepted bluetooth connection");

				is = socket.getInputStream();
				os = socket.getOutputStream();

				final CodedInputStream cis = CodedInputStream.newInstance(is);
				final CodedOutputStream cos = CodedOutputStream.newInstance(os);

				final int requestCode = cis.readInt32();

				if (requestCode == 0)
				{
					final String query = cis.readString();

					log.debug("query for {}", query);

					final byte[] paymentRequest = paymentRequestRef.get();
					if (query.equals("/") && paymentRequest != null)
					{
						// ok
						cos.writeInt32NoTag(200);
						cos.writeBytesNoTag(ByteString.copyFrom(paymentRequest));
					}
					else
					{
						// not found
						cos.writeInt32NoTag(404);
					}
				}
				else
				{
					// not implemented
					cos.writeInt32NoTag(501);
				}

				cos.flush();
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

	private static BluetoothServerSocket listen(final BluetoothAdapter adapter, final UUID uuid)
	{
		try
		{
			return adapter.listenUsingInsecureRfcommWithServiceRecord("Bitcoin Payment Request Server", uuid);
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}
}
