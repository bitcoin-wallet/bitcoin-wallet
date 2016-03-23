/*
 * Copyright 2016 the original author or authors.
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
import android.os.Binder;
import android.os.RemoteException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoin.paymentchannel.Protos;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBroadcaster;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;
import org.bitcoinj.protocols.channels.PaymentChannelServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class PaymentChannelServerInstanceBinder extends IPaymentChannelServerInstance.Stub {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelServerInstanceBinder.class);
    // TODO @w-shackleton find a more sensible value for this
    private static final Coin MIN_ACCEPTED_CHANNEL_SIZE = Coin.valueOf(10000);

    private final ChannelAsyncTask asyncTask;

    public PaymentChannelServerInstanceBinder(
            Wallet wallet,
            TransactionBroadcaster broadcaster,
            PaymentChannelService service,
            final IPaymentChannelCallbacks callbacks) {
        String callerName = service.getPackageManager().getNameForUid(Binder.getCallingUid());
        asyncTask = new ChannelAsyncTask(wallet, broadcaster, service, callbacks, callerName);
        asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Called when we receive a message from the server.
     * @param message
     * @throws RemoteException
     */
    @Override
    public void sendMessage(byte[] message) throws RemoteException {
        try {
            asyncTask.postMessage(Protos.TwoWayChannelMessage.parseFrom(message));
        } catch (InvalidProtocolBufferException e) {
            log.warn("Received an invalid message from service client");
            asyncTask.postConnectionClosed();
        }
    }

    @Override
    public void closeConnection() throws RemoteException {
        asyncTask.postConnectionClosed();
    }

    private static class ChannelAsyncTask extends AsyncPaymentChannelTask {

        private final Wallet wallet;
        private final TransactionBroadcaster broadcaster;
        private final PaymentChannelService service;
        private final String callerName;

        private PaymentChannelServer paymentChannelServer;

        public ChannelAsyncTask(Wallet wallet,
                                TransactionBroadcaster broadcaster,
                                PaymentChannelService service,
                                IPaymentChannelCallbacks callbacks,
                                String callerName) {
            super(callbacks);
            this.service = service;
            this.wallet = wallet;
            this.broadcaster = broadcaster;
            this.callerName = callerName;
        }

        @Override
        protected void handleThreadMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CONNECTION_CLOSED:
                    paymentChannelServer.connectionClosed();
                    break;
                case MESSAGE_TWO_WAY_CHANNEL_MESSAGE:
                    try {
                        paymentChannelServer.receiveMessage((Protos.TwoWayChannelMessage)msg.obj);
                    } catch (ECKey.KeyIsEncryptedException e) {
                        log.warn("Encrypted wallet, no key given", e);
                        paymentChannelServer.connectionClosed();
                        closeConnection();
                        // TODO @w-shackleton Tell user key was wrong
                    } catch (KeyCrypterException e) {
                        log.warn("Encrypted wallet, invalid key given", e);
                        paymentChannelServer.connectionClosed();
                        closeConnection();
                        // TODO @w-shackleton Tell user key was wrong
                    }
                    break;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            paymentChannelServer = new PaymentChannelServer(
                    broadcaster,
                    wallet,
                    MIN_ACCEPTED_CHANNEL_SIZE,
                    new PaymentChannelServer.ServerConnection() {
                        @Override
                        public void sendToClient(Protos.TwoWayChannelMessage msg) {
                            publishProgress(msg);
                        }

                        @Override
                        public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
                            // onPostExecute will call the remote close
                            cancel(false);
                        }

                        @Override
                        public void channelOpen(Sha256Hash contractHash) {
                            service.addChannelToAddressBook(
                                    paymentChannelServer.state().getContract(),
                                    true,
                                    callerName);
                        }

                        @Nullable
                        @Override
                        public ListenableFuture<ByteString> paymentIncrease(Coin by, Coin to, ByteString info) {
                            // Someone is giving our wallet money - return no message.
                            service.notifyChannelIncrement(by, callerName);
                            return null;
                        }
                    });
            paymentChannelServer.connectionOpen();
            return super.doInBackground(params);
        }
    }
}
