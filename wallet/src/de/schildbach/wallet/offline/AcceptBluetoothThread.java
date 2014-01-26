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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bitcoin.protocols.payments.Protos;
import org.bitcoin.protocols.payments.Protos.PaymentACK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Transaction;
import com.google.protobuf.ByteString;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.Bluetooth;

/**
 * @author Shahar Livne
 * @author Andreas Schildbach
 */
public abstract class AcceptBluetoothThread extends Thread
{
	protected final BluetoothServerSocket listeningSocket;
	protected final AtomicBoolean running = new AtomicBoolean(true);

	protected static final Logger log = LoggerFactory.getLogger(AcceptBluetoothThread.class);

	private AcceptBluetoothThread(final BluetoothServerSocket listeningSocket)
	{
		this.listeningSocket = listeningSocket;
	}

	public static abstract class Classic extends AcceptBluetoothThread
	{
		public Classic(@Nonnull final BluetoothAdapter adapter)
		{
			super(listen(adapter, Bluetooth.BLUETOOTH_UUID_CLASSIC));
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

	public static abstract class PaymentProtocol extends AcceptBluetoothThread
	{
		public PaymentProtocol(@Nonnull final BluetoothAdapter adapter)
		{
			super(listen(adapter, Bluetooth.BLUETOOTH_UUID_PAYMENT_PROTOCOL));
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

					log.info("accepted payment protocol bluetooth connection");

					is = new DataInputStream(socket.getInputStream());
					os = new DataOutputStream(socket.getOutputStream());

					boolean ack = true;

					final Protos.Payment payment = Protos.Payment.parseDelimitedFrom(is);

					log.debug("got payment message");

					for (final Transaction tx : parsePaymentMessage(payment))
					{
						if (!handleTx(tx))
							ack = false;
					}

					final String memo = ack ? "ack" : "nack";

					log.info("sending {} via bluetooth", memo);

					writePaymentAck(os, payment, memo);
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

		private List<Transaction> parsePaymentMessage(final Protos.Payment paymentMessage) throws IOException
		{
			final List<Transaction> transactions = new ArrayList<Transaction>(paymentMessage.getTransactionsCount());

			for (final ByteString transaction : paymentMessage.getTransactionsList())
				transactions.add(new Transaction(Constants.NETWORK_PARAMETERS, transaction.toByteArray()));

			return transactions;
		}

		private PaymentACK writePaymentAck(@Nonnull final OutputStream os, @Nonnull final Protos.Payment paymentMessage, @Nullable final String memo)
				throws IOException
		{
			final Protos.PaymentACK.Builder builder = Protos.PaymentACK.newBuilder();

			builder.setPayment(paymentMessage);

			builder.setMemo(memo);

			final PaymentACK paymentAck = builder.build();
			paymentAck.writeDelimitedTo(os);
			return paymentAck;
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

	protected static BluetoothServerSocket listen(final BluetoothAdapter adapter, final UUID uuid)
	{
		try
		{
			return adapter.listenUsingInsecureRfcommWithServiceRecord("Bitcoin Transaction Submission", uuid);
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	protected abstract boolean handleTx(@Nonnull Transaction tx);
}
