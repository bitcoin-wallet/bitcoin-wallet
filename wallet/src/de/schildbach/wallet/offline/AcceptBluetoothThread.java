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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.bitcoin.protocols.payments.Protos;
import org.bitcoin.protocols.payments.Protos.PaymentACK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Transaction;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.PaymentProtocol;

/**
 * @author Shahar Livne
 * @author Andreas Schildbach
 */
public abstract class AcceptBluetoothThread extends Thread
{
	protected final BluetoothAdapter adapter;
	protected final AtomicBoolean running = new AtomicBoolean(true);
	
	protected BluetoothServerSocket listeningSocket = null;

	protected static final Logger log = LoggerFactory.getLogger(AcceptBluetoothThread.class);

	private AcceptBluetoothThread(@Nonnull final BluetoothAdapter adapter)
	{
		this.adapter = adapter;
	}

	public static abstract class ClassicBluetoothThread extends AcceptBluetoothThread
	{
		public ClassicBluetoothThread(@Nonnull final BluetoothAdapter adapter)
		{
			super(adapter);
		}

		@Override
		public void run()
		{
			try
			{
				listeningSocket = listen(adapter, Bluetooth.CLASSIC_PAYMENT_PROTOCOL_NAME, Bluetooth.CLASSIC_PAYMENT_PROTOCOL_UUID);
			}
			catch (IOException x)
			{
				log.info("exception while creating a listening socket for classic bluetooth service", x);
				running.set(false);
			}
			
			while (running.get())
			{
				BluetoothSocket socket = null;
				DataInputStream is = null;
				DataOutputStream os = null;

				try
				{
					// start a blocking call, and return only on success or exception
					socket = listeningSocket.accept();

					log.info("accepted classic bluetooth connection");

					is = new DataInputStream(socket.getInputStream());
					os = new DataOutputStream(socket.getOutputStream());

					boolean ack = true;

					final int numMessages = is.readInt();

					for (int i = 0; i < numMessages; i++)
					{
						final int msgLength = is.readInt();
						final byte[] msg = new byte[msgLength];
						is.readFully(msg);

						try
						{
							final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, msg);

							if (!handleTx(tx))
								ack = false;
						}
						catch (final ProtocolException x)
						{
							log.info("cannot decode message received via bluetooth", x);
							ack = false;
						}
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
	}

	public static abstract class PaymentProtocolThread extends AcceptBluetoothThread
	{
		public PaymentProtocolThread(@Nonnull final BluetoothAdapter adapter)
		{
			super(adapter);
		}

		@Override
		public void run()
		{
			try
			{
				listeningSocket = listen(adapter, Bluetooth.BIP70_PAYMENT_PROTOCOL_NAME, Bluetooth.BIP70_PAYMENT_PROTOCOL_UUID);
			}
			catch (IOException x)
			{
				log.info("exception while creating a listening socket for payment protocol bluetooth service", x);
				running.set(false);
			}
			
			while (running.get())
			{
				BluetoothSocket socket = null;
				DataInputStream is = null;
				DataOutputStream os = null;

				try
				{
					// start a blocking call, and return only on success or exception
					socket = listeningSocket.accept();

					log.info("accepted payment protocol bluetooth connection");

					is = new DataInputStream(socket.getInputStream());
					os = new DataOutputStream(socket.getOutputStream());

					boolean ack = true;

					final Protos.Payment payment = Protos.Payment.parseDelimitedFrom(is);

					log.debug("got payment message");

					for (final Transaction tx : PaymentProtocol.parsePaymentMessage(payment))
					{
						if (!handleTx(tx))
							ack = false;
					}

					final String memo = ack ? "ack" : "nack";

					log.info("sending {} via bluetooth", memo);

					final PaymentACK paymentAck = PaymentProtocol.createPaymentAck(payment, memo);
					paymentAck.writeDelimitedTo(os);
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
	}

	public void stopAccepting()
	{
		running.set(false);

		try
		{
			if (listeningSocket != null)
				listeningSocket.close();
		}
		catch (final IOException x)
		{
			// swallow
		}
	}

	protected static BluetoothServerSocket listen(final BluetoothAdapter adapter, final String serviceName, final UUID serviceUuid) throws IOException
	{
		return adapter.listenUsingInsecureRfcommWithServiceRecord(serviceName, serviceUuid);
	}

	protected abstract boolean handleTx(@Nonnull Transaction tx);
}
