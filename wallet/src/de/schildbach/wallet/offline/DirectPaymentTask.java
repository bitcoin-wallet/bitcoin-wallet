/*
 * Copyright 2013-2015 the original author or authors.
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

import javax.annotation.Nullable;

import org.bitcoin.protocols.payments.Protos;
import org.bitcoin.protocols.payments.Protos.Payment;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.R;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import okio.BufferedSink;

/**
 * @author Andreas Schildbach
 */
public abstract class DirectPaymentTask {
    private final Handler backgroundHandler;
    private final Handler callbackHandler;
    private final ResultCallback resultCallback;

    private static final Logger log = LoggerFactory.getLogger(DirectPaymentTask.class);

    public interface ResultCallback {
        void onResult(boolean ack);

        void onFail(int messageResId, Object... messageArgs);
    }

    public DirectPaymentTask(final Handler backgroundHandler, final ResultCallback resultCallback) {
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
        this.resultCallback = resultCallback;
    }

    public final static class HttpPaymentTask extends DirectPaymentTask {
        private final String url;
        @Nullable
        private final String userAgent;

        public HttpPaymentTask(final Handler backgroundHandler, final ResultCallback resultCallback, final String url,
                @Nullable final String userAgent) {
            super(backgroundHandler, resultCallback);

            this.url = url;
            this.userAgent = userAgent;
        }

        @Override
        public void send(final Payment payment) {
            super.backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    log.info("trying to send tx to {}", url);

                    final Request.Builder request = new Request.Builder();
                    request.url(url);
                    request.cacheControl(new CacheControl.Builder().noCache().build());
                    request.header("Accept", PaymentProtocol.MIMETYPE_PAYMENTACK);
                    if (userAgent != null)
                        request.header("User-Agent", userAgent);
                    request.post(new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            return MediaType.parse(PaymentProtocol.MIMETYPE_PAYMENT);
                        }

                        @Override
                        public long contentLength() throws IOException {
                            return payment.getSerializedSize();
                        }

                        @Override
                        public void writeTo(final BufferedSink sink) throws IOException {
                            payment.writeTo(sink.outputStream());
                        }
                    });

                    final Call call = Constants.HTTP_CLIENT.newCall(request.build());
                    try {
                        final Response response = call.execute();
                        if (response.isSuccessful()) {
                            log.info("tx sent via http");

                            final InputStream is = response.body().byteStream();
                            final Protos.PaymentACK paymentAck = Protos.PaymentACK.parseFrom(is);
                            is.close();

                            final boolean ack = !"nack".equals(PaymentProtocol.parsePaymentAck(paymentAck).getMemo());

                            log.info("received {} via http", ack ? "ack" : "nack");

                            onResult(ack);
                        } else {
                            final int responseCode = response.code();
                            final String responseMessage = response.message();

                            log.info("got http error {}: {}", responseCode, responseMessage);
                            onFail(R.string.error_http, responseCode, responseMessage);
                        }
                    } catch (final IOException x) {
                        log.info("problem sending", x);

                        onFail(R.string.error_io, x.getMessage());
                    }
                }
            });
        }
    }

    public final static class BluetoothPaymentTask extends DirectPaymentTask {
        private final BluetoothAdapter bluetoothAdapter;
        private final String bluetoothMac;

        public BluetoothPaymentTask(final Handler backgroundHandler, final ResultCallback resultCallback,
                final BluetoothAdapter bluetoothAdapter, final String bluetoothMac) {
            super(backgroundHandler, resultCallback);

            this.bluetoothAdapter = bluetoothAdapter;
            this.bluetoothMac = bluetoothMac;
        }

        @Override
        public void send(final Payment payment) {
            super.backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    log.info("trying to send tx via bluetooth {}", bluetoothMac);

                    if (payment.getTransactionsCount() != 1)
                        throw new IllegalArgumentException("wrong transactions count");

                    final BluetoothDevice device = bluetoothAdapter
                            .getRemoteDevice(Bluetooth.decompressMac(bluetoothMac));

                    BluetoothSocket socket = null;
                    DataOutputStream os = null;
                    DataInputStream is = null;

                    try {
                        socket = device
                                .createInsecureRfcommSocketToServiceRecord(Bluetooth.BIP70_PAYMENT_PROTOCOL_UUID);
                        socket.connect();

                        log.info("connected to payment protocol {}", bluetoothMac);

                        is = new DataInputStream(socket.getInputStream());
                        os = new DataOutputStream(socket.getOutputStream());

                        payment.writeDelimitedTo(os);
                        os.flush();

                        log.info("tx sent via bluetooth");

                        final Protos.PaymentACK paymentAck = Protos.PaymentACK.parseDelimitedFrom(is);

                        final boolean ack = "ack".equals(PaymentProtocol.parsePaymentAck(paymentAck).getMemo());

                        log.info("received {} via bluetooth", ack ? "ack" : "nack");

                        onResult(ack);
                    } catch (final IOException x) {
                        log.info("problem sending", x);

                        onFail(R.string.error_io, x.getMessage());
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
            });
        }
    }

    public abstract void send(Payment payment);

    protected void onResult(final boolean ack) {
        callbackHandler.post(new Runnable() {
            @Override
            public void run() {
                resultCallback.onResult(ack);
            }
        });
    }

    protected void onFail(final int messageResId, final Object... messageArgs) {
        callbackHandler.post(new Runnable() {
            @Override
            public void run() {
                resultCallback.onFail(messageResId, messageArgs);
            }
        });
    }
}
