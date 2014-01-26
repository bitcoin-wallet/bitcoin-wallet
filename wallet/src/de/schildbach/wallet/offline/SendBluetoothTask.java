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
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bitcoin.protocols.payments.Protos;
import org.bitcoin.protocols.payments.Protos.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.protobuf.ByteString;

import de.schildbach.wallet.PaymentIntent;
import de.schildbach.wallet.util.Bluetooth;

/**
 * @author Andreas Schildbach
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

	public void send(@Nonnull final String bluetoothMac, @Nonnull final PaymentIntent.Standard standard, @Nonnull final Transaction transaction,
			@Nonnull final Address refundAddress, @Nonnull final BigInteger refundAmount, @Nonnull final byte[] merchantData)
	{
		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				log.info("trying to send tx {} via bluetooth {} using {} standard", new Object[] { transaction.getHashAsString(), bluetoothMac,
						standard });

				final byte[] serializedTx = transaction.unsafeBitcoinSerialize();

				BluetoothSocket socket = null;
				DataOutputStream os = null;
				DataInputStream is = null;

				try
				{
					final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(Bluetooth.decompressMac(bluetoothMac));

					final boolean ack;

					if (standard == PaymentIntent.Standard.BIP21)
					{
						socket = device.createInsecureRfcommSocketToServiceRecord(Bluetooth.BLUETOOTH_UUID_CLASSIC);

						socket.connect();
						log.info("connected to classic {}", bluetoothMac);

						is = new DataInputStream(socket.getInputStream());
						os = new DataOutputStream(socket.getOutputStream());

						os.writeInt(1);
						os.writeInt(serializedTx.length);
						os.write(serializedTx);

						os.flush();

						log.info("tx {} sent via bluetooth", transaction.getHashAsString());

						ack = is.readBoolean();
					}
					else if (standard == PaymentIntent.Standard.BIP70)
					{
						socket = device.createInsecureRfcommSocketToServiceRecord(Bluetooth.BLUETOOTH_UUID_PAYMENT_PROTOCOL);

						socket.connect();
						log.info("connected to payment protocol {}", bluetoothMac);

						is = new DataInputStream(socket.getInputStream());
						os = new DataOutputStream(socket.getOutputStream());

						final Payment payment = writePaymentMessage(os, transaction, refundAddress, refundAmount, null, merchantData);
						os.flush();

						log.info("tx {} sent via bluetooth", transaction.getHashAsString());

						ack = !"nack".equals(parsePaymentAck(is, payment));
					}
					else
					{
						throw new IllegalArgumentException("cannot handle: " + standard);
					}

					log.info("received {} via bluetooth", ack ? "ack" : "nack");

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

	private static Payment writePaymentMessage(@Nonnull OutputStream os, @Nonnull final Transaction transaction,
			@Nullable final Address refundAddress, @Nullable final BigInteger refundAmount, @Nullable final String memo,
			@Nullable final byte[] merchantData) throws IOException
	{
		final Protos.Payment.Builder builder = Protos.Payment.newBuilder();

		builder.addTransactions(ByteString.copyFrom(transaction.unsafeBitcoinSerialize()));

		if (refundAddress != null)
		{
			if (refundAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
				throw new IllegalArgumentException("refund amount too big for protobuf: " + refundAmount);

			final Protos.Output.Builder refundOutput = Protos.Output.newBuilder();
			refundOutput.setAmount(refundAmount.longValue());
			refundOutput.setScript(ByteString.copyFrom(ScriptBuilder.createOutputScript(refundAddress).getProgram()));
			builder.addRefundTo(refundOutput);
		}

		if (memo != null)
			builder.setMemo(memo);

		if (merchantData != null)
			builder.setMerchantData(ByteString.copyFrom(merchantData));

		final Payment payment = builder.build();
		payment.writeDelimitedTo(os);
		return payment;
	}

	private static String parsePaymentAck(@Nonnull final InputStream is, @Nonnull final Payment expectedPaymentMessage) throws IOException
	{
		final Protos.PaymentACK paymentAck = Protos.PaymentACK.parseDelimitedFrom(is);

		if (!paymentAck.getPayment().equals(expectedPaymentMessage))
			return null;

		return paymentAck.getMemo();
	}
}
