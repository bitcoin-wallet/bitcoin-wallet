/*
 * Copyright 2011-2015 the original author or authors.
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
package de.schildbach.wallet.channels;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoin.paymentchannel.Protos;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.protocols.channels.PaymentChannelClient;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;
import org.bitcoinj.protocols.channels.PaymentIncrementAck;
import org.bitcoinj.protocols.channels.ValueOutOfRangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentChannelClientInstanceBinder extends IPaymentChannelClientInstance.Stub {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelClientInstanceBinder.class);

    private final ChannelAsyncTask asyncTask;

    public PaymentChannelClientInstanceBinder(
            Wallet wallet,
            final IPaymentChannelCallbacks callbacks,
            Coin maxValue,
            Sha256Hash serverId,
            final long timeWindow
            ) {
        asyncTask = new ChannelAsyncTask(wallet, callbacks, maxValue, serverId, timeWindow);
        asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void sendMessage(byte[] message) throws RemoteException {
        try {
            asyncTask.postMessage(Protos.TwoWayChannelMessage.parseFrom(message));
        } catch (InvalidProtocolBufferException e) {
            log.warn("Received an invalid message from service client");
            asyncTask.postConnectionClosed();
        }

    }

    private static class IncrementRequest {
        public final Coin value;
        public final SettableFuture<PaymentIncrementAck> result;

        public IncrementRequest(long satoshis, SettableFuture<PaymentIncrementAck> result) {
            this.value = Coin.valueOf(satoshis);
            this.result = result;
        }
    }

    @Override
    public boolean requestIncrement(long satoshis) throws RemoteException {
        // TODO @w-shackleton confirm with the user that they want to accept this payment
        // TODO @w-shackleton make dialog wait until we have ack
        @SuppressWarnings("unused")
        ListenableFuture<PaymentIncrementAck> ackFuture =
                asyncTask.postIncrementPayment(satoshis);
        return true;
    }

    @Override
    public void closeConnection() throws RemoteException {
        asyncTask.postConnectionClosed();
    }

    private static class ChannelAsyncTask extends AsyncPaymentChannelTask {

        public static final int MESSAGE_INCREMENT_PAYMENT = 3;

        private final Wallet wallet;
        private final Coin maxValue;
        private final Sha256Hash serverId;
        private final long timeWindow;

        private PaymentChannelClient paymentChannelClient;

        public ChannelAsyncTask(
                Wallet wallet,
                final IPaymentChannelCallbacks callbacks,
                Coin maxValue,
                Sha256Hash serverId,
                final long timeWindow) {
            super(callbacks);
            this.wallet = wallet;
            this.maxValue = maxValue;
            this.serverId = serverId;
            this.timeWindow = timeWindow;
        }

        @Override
        protected Handler createHandler() {
            return new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MESSAGE_CONNECTION_CLOSED:
                            paymentChannelClient.connectionClosed();
                            break;
                        case MESSAGE_INCREMENT_PAYMENT:
                            try {
                                final IncrementRequest req = (IncrementRequest) msg.obj;
                                Futures.addCallback(
                                        paymentChannelClient.incrementPayment(req.value),
                                        new FutureCallback<PaymentIncrementAck>() {
                                            @Override
                                            public void onSuccess(PaymentIncrementAck result) {
                                                req.result.set(result);
                                            }

                                            @Override
                                            public void onFailure(Throwable t) {
                                                req.result.setException(t);
                                            }
                                        });
                            } catch (ValueOutOfRangeException e) {
                                e.printStackTrace();
                            }
                        case MESSAGE_TWO_WAY_CHANNEL_MESSAGE:
                            try {
                                paymentChannelClient.receiveMessage((Protos.TwoWayChannelMessage)msg.obj);
                            } catch (InsufficientMoneyException e) {
                                // TODO @w-shackleton display a message to the user saying what's happened.
                                log.info("Not enough money in wallet to satisfy contract", e);
                            }
                        default:
                            super.handleMessage(msg);
                    }
                }
            };
        }

        @Override
        protected Void doInBackground(Void... params) {
            super.doInBackground(params);
            // TODO @w-shackleton confirm that these values are OK with the user rather than blindly accepting
            ECKey myKey = new ECKey();
            wallet.importKey(myKey);
            wallet.allowSpendingUnconfirmedTransactions();

            paymentChannelClient = new PaymentChannelClient(
                    wallet,
                    myKey,
                    maxValue,
                    serverId,
                    timeWindow,
                    null,
                    new PaymentChannelClient.ClientConnection() {
                        @Override
                        public void sendToServer(Protos.TwoWayChannelMessage msg) {
                            publishProgress(msg);
                        }

                        @Override
                        public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
                            Looper.myLooper().quit();
                        }

                        @Override
                        public boolean acceptExpireTime(long expireTime) {
                            // Accept one extra minute to allow for latency and clock skew
                            return expireTime <= (timeWindow + Utils.currentTimeSeconds() + 60);
                        }

                        @Override
                        public void channelOpen(boolean wasInitiated) {

                        }
                    });
            paymentChannelClient.connectionOpen();
            Looper.loop();
            return null;
        }

        public ListenableFuture<PaymentIncrementAck> postIncrementPayment(long satoshis) {
            SettableFuture<PaymentIncrementAck> result = SettableFuture.create();
            getHandler().sendMessage(Message.obtain(getHandler(), MESSAGE_INCREMENT_PAYMENT,
                    new IncrementRequest(satoshis, result)));
            return result;
        }
    }
}
