/*
 * Copyright 2012-2015 the original author or authors.
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

import org.bitcoin.protocols.payments.Protos;
import org.bitcoin.protocols.payments.Protos.PaymentACK;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.Bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

/**
 * @author Shahar Livne
 * @author Andreas Schildbach
 */
public abstract class AcceptBluetoothThread extends Thread {
    protected final BluetoothServerSocket listeningSocket;
    protected final AtomicBoolean running = new AtomicBoolean(true);

    protected static final Logger log = LoggerFactory.getLogger(AcceptBluetoothThread.class);

    private AcceptBluetoothThread(final BluetoothServerSocket listeningSocket) {
        this.listeningSocket = listeningSocket;
    }

    public static abstract class ClassicBluetoothThread extends AcceptBluetoothThread {
        public ClassicBluetoothThread(final BluetoothAdapter adapter) throws IOException {
            super(adapter.listenUsingInsecureRfcommWithServiceRecord(Bluetooth.CLASSIC_PAYMENT_PROTOCOL_NAME,
                    Bluetooth.CLASSIC_PAYMENT_PROTOCOL_UUID));
        }

        @Override
        public void run() {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

            while (running.get()) {
                BluetoothSocket socket = null;
                DataInputStream is = null;
                DataOutputStream os = null;

                try {
                    // start a blocking call, and return only on success or exception
                    socket = listeningSocket.accept();

                    log.info("accepted classic bluetooth connection");

                    is = new DataInputStream(socket.getInputStream());
                    os = new DataOutputStream(socket.getOutputStream());

                    boolean ack = true;

                    final int numMessages = is.readInt();

                    for (int i = 0; i < numMessages; i++) {
                        final int msgLength = is.readInt();
                        final byte[] msg = new byte[msgLength];
                        is.readFully(msg);

                        try {
                            final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, msg);

                            if (!handleTx(tx))
                                ack = false;
                        } catch (final ProtocolException x) {
                            log.info("cannot decode message received via bluetooth", x);
                            ack = false;
                        }
                    }

                    os.writeBoolean(ack);
                } catch (final IOException x) {
                    log.info("exception in bluetooth accept loop", x);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (final IOException x) {
                            // swallow
                        }
                    }

                    if (is != null) {
                        try {
                            is.close();
                        } catch (final IOException x) {
                            // swallow
                        }
                    }

                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (final IOException x) {
                            // swallow
                        }
                    }
                }
            }
        }
    }

    public static abstract class PaymentProtocolThread extends AcceptBluetoothThread {
        public PaymentProtocolThread(final BluetoothAdapter adapter) throws IOException {
            super(adapter.listenUsingInsecureRfcommWithServiceRecord(Bluetooth.BIP70_PAYMENT_PROTOCOL_NAME,
                    Bluetooth.BIP70_PAYMENT_PROTOCOL_UUID));
        }

        @Override
        public void run() {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

            while (running.get()) {
                BluetoothSocket socket = null;
                DataInputStream is = null;
                DataOutputStream os = null;

                try {
                    // start a blocking call, and return only on success or exception
                    socket = listeningSocket.accept();

                    log.info("accepted payment protocol bluetooth connection");

                    is = new DataInputStream(socket.getInputStream());
                    os = new DataOutputStream(socket.getOutputStream());

                    boolean ack = true;

                    final Protos.Payment payment = Protos.Payment.parseDelimitedFrom(is);

                    log.debug("got payment message");

                    for (final Transaction tx : PaymentProtocol
                            .parseTransactionsFromPaymentMessage(Constants.NETWORK_PARAMETERS, payment)) {
                        if (!handleTx(tx))
                            ack = false;
                    }

                    final String memo = ack ? "ack" : "nack";

                    log.info("sending {} via bluetooth", memo);

                    final PaymentACK paymentAck = PaymentProtocol.createPaymentAck(payment, memo);
                    paymentAck.writeDelimitedTo(os);
                } catch (final IOException x) {
                    log.info("exception in bluetooth accept loop", x);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (final IOException x) {
                            // swallow
                        }
                    }

                    if (is != null) {
                        try {
                            is.close();
                        } catch (final IOException x) {
                            // swallow
                        }
                    }

                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (final IOException x) {
                            // swallow
                        }
                    }
                }
            }
        }
    }

    public void stopAccepting() {
        running.set(false);

        try {
            listeningSocket.close();
        } catch (final IOException x) {
            // swallow
        }
    }

    protected abstract boolean handleTx(Transaction tx);
}
