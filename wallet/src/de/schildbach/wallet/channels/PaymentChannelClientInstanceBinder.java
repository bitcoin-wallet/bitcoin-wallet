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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
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
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.protocols.channels.PaymentChannelClient;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;
import org.bitcoinj.protocols.channels.PaymentIncrementAck;
import org.bitcoinj.protocols.channels.ValueOutOfRangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;

import de.schildbach.wallet.ui.channels.ChannelCreateActivity;
import de.schildbach.wallet.ui.channels.ChannelIncrementActivity;

public class PaymentChannelClientInstanceBinder extends IPaymentChannelClientInstance.Stub {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelClientInstanceBinder.class);

    private final Context context;
    private ChannelAsyncTask asyncTask;
    private final Wallet wallet;
    private final IPaymentChannelCallbacks callbacks;
    private final Coin maxValue;
    private final Sha256Hash serverId;
    private final int channelId;
    private final long timeWindow;

    private Coin pendingIncrement = null;

    public PaymentChannelClientInstanceBinder(
            Context context,
            Wallet wallet,
            final IPaymentChannelCallbacks callbacks,
            int channelId,
            Coin maxValue,
            Sha256Hash serverId,
            final long timeWindow
            ) {
        this.context = context;
        this.wallet = wallet;
        this.callbacks = callbacks;
        this.maxValue = maxValue;
        this.serverId = serverId;
        this.timeWindow = timeWindow;
        this.channelId = channelId;

        String callerName = context.getPackageManager().getNameForUid(Binder.getCallingUid());

        Intent confirmIntent = new Intent(context, ChannelCreateActivity.class);
        confirmIntent.setAction(Intent.ACTION_VIEW);
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        confirmIntent.putExtra(ChannelCreateActivity.INTENT_EXTRA_AMOUNT, maxValue.value);
        confirmIntent.putExtra(ChannelCreateActivity.INTENT_EXTRA_TIME_WINDOW, timeWindow);
        confirmIntent.putExtra(ChannelCreateActivity.INTENT_EXTRA_CALLER_PACKAGE, callerName);
        confirmIntent.putExtra(ChannelCreateActivity.INTENT_EXTRA_CHANNEL_ID, channelId);
        confirmIntent.putExtra(ChannelCreateActivity.INTENT_EXTRA_PASSWORD_REQUIRED, wallet.isEncrypted());
        context.startActivity(confirmIntent);
    }

    /**
     * Called from the activity when the user confirms that they want to set up the requested
     * payment channel
     */
    public void onChannelConfirmed(@Nullable KeyParameter keySetup) {
        asyncTask = new ChannelAsyncTask(wallet, callbacks, maxValue, serverId, timeWindow, keySetup);
        asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Called from the activity when the user cancels the payment channel.
     */
    public void onChannelCancelled() {
        try {
            callbacks.closeConnection();
        } catch (RemoteException e) {
            log.warn("Failed to cancel channel", e);
        }
    }

    public synchronized void onChannelIncrementConfirmed(@Nullable KeyParameter key) {
        // TODO @w-shackleton make dialog wait until we have ack
        ListenableFuture<PaymentIncrementAck> ackFuture =
                asyncTask.postIncrementPayment(pendingIncrement, key);
        Futures.addCallback(ackFuture,
                new FutureCallback<PaymentIncrementAck>() {
                    @Override
                    public void onSuccess(@Nullable PaymentIncrementAck result) {
                        log.debug("Payment increment succeeded");
                        synchronized (PaymentChannelClientInstanceBinder.this) {
                            pendingIncrement = null;
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        if (t instanceof ECKey.KeyIsEncryptedException ||
                                t instanceof KeyCrypterException) {
                            Coin amount = pendingIncrement;
                            requestIncrement(amount.longValue(), true);
                        } else if (t instanceof ValueOutOfRangeException) {
                            // TODO @w-shackleton something here
                        }
                        log.debug("Payment increment failed", t);
                        synchronized (PaymentChannelClientInstanceBinder.this) {
                            pendingIncrement = null;
                        }
                    }
                });
    }

    public synchronized void onChannelIncrementCancelled() {
        pendingIncrement = null;
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
        public final @Nullable KeyParameter userKey;

        public IncrementRequest(Coin value, @Nullable KeyParameter userKey, SettableFuture<PaymentIncrementAck> result) {
            this.value = value;
            this.result = result;
            this.userKey = userKey;
        }
    }

    @Override
    public boolean requestIncrement(long satoshis) {
        return requestIncrement(satoshis, false);
    }

    public synchronized boolean requestIncrement(long satoshis, boolean pinWasInvalid) {
        if (pendingIncrement != null) {
            // Only one pending increment at once
            return false;
        }
        pendingIncrement = Coin.valueOf(satoshis);

        String callerName = context.getPackageManager().getNameForUid(Binder.getCallingUid());

        Intent confirmIntent = new Intent(context, ChannelIncrementActivity.class);
        confirmIntent.setAction(Intent.ACTION_VIEW);
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        confirmIntent.putExtra(ChannelIncrementActivity.INTENT_EXTRA_AMOUNT, pendingIncrement.longValue());
        confirmIntent.putExtra(ChannelIncrementActivity.INTENT_EXTRA_CALLER_PACKAGE, callerName);
        confirmIntent.putExtra(ChannelIncrementActivity.INTENT_EXTRA_PIN_WAS_INVALID, pinWasInvalid);
        confirmIntent.putExtra(ChannelIncrementActivity.INTENT_EXTRA_CHANNEL_ID, channelId);
        confirmIntent.putExtra(ChannelIncrementActivity.INTENT_EXTRA_PASSWORD_REQUIRED, wallet.isEncrypted());
        context.startActivity(confirmIntent);
        return true;
    }

    @Override
    public void requestSettle() throws RemoteException {
        asyncTask.postSettle();
    }

    @Override
    public void closeConnection() throws RemoteException {
        asyncTask.postConnectionClosed();
    }

    private static class ChannelAsyncTask extends AsyncPaymentChannelTask {

        public static final int MESSAGE_INCREMENT_PAYMENT = 3;
        public static final int MESSAGE_SETTLE = 4;

        private final Wallet wallet;
        private final Coin maxValue;
        private final Sha256Hash serverId;
        private final long timeWindow;
        private final KeyParameter keySetup;

        private PaymentChannelClient paymentChannelClient;

        public ChannelAsyncTask(
                Wallet wallet,
                final IPaymentChannelCallbacks callbacks,
                Coin maxValue,
                Sha256Hash serverId,
                final long timeWindow,
                @Nullable KeyParameter keySetup) {
            super(callbacks);
            this.wallet = wallet;
            this.maxValue = maxValue;
            this.serverId = serverId;
            this.timeWindow = timeWindow;
            this.keySetup = keySetup;
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
                            final IncrementRequest req = (IncrementRequest) msg.obj;
                            try {
                                ListenableFuture<PaymentIncrementAck> future =
                                        paymentChannelClient.incrementPayment(req.value, null, req.userKey);
                                Futures.addCallback(future,
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
                                log.warn("Payment increment failed", e);
                                req.result.setException(e);
                            } catch (ECKey.KeyIsEncryptedException e) {
                                log.warn("Encrypted wallet, no key given", e);
                                req.result.setException(e);
                            } catch (KeyCrypterException e) {
                                log.warn("Encrypted wallet, invalid key given", e);
                                req.result.setException(e);
                            } catch (IllegalStateException e) {
                                log.warn("Channel in inconsistent state", e);
                                req.result.setException(e);
                            }
                            break;
                        case MESSAGE_TWO_WAY_CHANNEL_MESSAGE:
                            try {
                                paymentChannelClient.receiveMessage((Protos.TwoWayChannelMessage)msg.obj);
                            } catch (InsufficientMoneyException e) {
                                // TODO @w-shackleton display a message to the user saying what's happened.
                                log.info("Not enough money in wallet to satisfy contract", e);
                            } catch (ECKey.KeyIsEncryptedException e) {
                                log.warn("Encrypted wallet, no key given", e);
                                paymentChannelClient.connectionClosed();
                                closeConnection();
                                // TODO @w-shackleton Tell user key was wrong
                            } catch (KeyCrypterException e) {
                                log.warn("Encrypted wallet, invalid key given", e);
                                paymentChannelClient.connectionClosed();
                                closeConnection();
                                // TODO @w-shackleton Tell user key was wrong
                            }
                            break;
                        case MESSAGE_SETTLE:
                            paymentChannelClient.settle();
                            break;
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
            if (keySetup != null) {
                myKey = myKey.encrypt(wallet.getKeyCrypter(), keySetup);
            }
            wallet.importKey(myKey);
            wallet.allowSpendingUnconfirmedTransactions();

            paymentChannelClient = new PaymentChannelClient(
                    wallet,
                    myKey,
                    maxValue,
                    serverId,
                    timeWindow,
                    keySetup,
                    new PaymentChannelClient.ClientConnection() {
                        @Override
                        public void sendToServer(Protos.TwoWayChannelMessage msg) {
                            log.debug("Sending TwoWayChannelMessage {}", msg.getType());
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

        public ListenableFuture<PaymentIncrementAck> postIncrementPayment(Coin value, @Nullable KeyParameter userKey) {
            SettableFuture<PaymentIncrementAck> result = SettableFuture.create();
            getHandler().sendMessage(Message.obtain(getHandler(), MESSAGE_INCREMENT_PAYMENT,
                    new IncrementRequest(value, userKey, result)));
            return result;
        }

        public void postSettle() {
            getHandler().sendMessage(Message.obtain(getHandler(), MESSAGE_SETTLE));
        }
    }
}
