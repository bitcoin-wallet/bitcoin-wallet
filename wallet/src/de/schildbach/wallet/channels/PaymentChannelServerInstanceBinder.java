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

import android.os.RemoteException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoin.paymentchannel.Protos;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBroadcaster;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;
import org.bitcoinj.protocols.channels.PaymentChannelServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class PaymentChannelServerInstanceBinder extends IPaymentChannelServerInstance.Stub {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelServerInstanceBinder.class);
    // TODO @w-shackleton find a more sensible value for this
    private static final Coin MIN_ACCEPTED_CHANNEL_SIZE = Coin.valueOf(100000);

    private final PaymentChannelServer paymentChannelServer;

    public PaymentChannelServerInstanceBinder(
            Wallet wallet,
            TransactionBroadcaster broadcaster,
            final IPaymentChannelCallbacks callbacks) {
        paymentChannelServer = new PaymentChannelServer(
                broadcaster,
                wallet,
                MIN_ACCEPTED_CHANNEL_SIZE,
                new PaymentChannelServer.ServerConnection() {
            @Override
            public void sendToClient(Protos.TwoWayChannelMessage msg) {
                try {
                    callbacks.sendMessage(msg.toByteArray());
                } catch (RemoteException e) {
                    log.warn("Failed to deliver message to service client", e);
                    paymentChannelServer.connectionClosed();
                }
            }

            @Override
            public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {

            }

            @Override
            public void channelOpen(Sha256Hash contractHash) {

            }

            @Nullable
            @Override
            public ListenableFuture<ByteString> paymentIncrease(Coin by, Coin to, ByteString info) {
                // Someone is giving our wallet money - return no message.
                return null;
            }
        });
    }

    /**
     * Called when we receive a message from the server.
     * @param message
     * @throws RemoteException
     */
    @Override
    public void sendMessage(byte[] message) throws RemoteException {
        try {
            paymentChannelServer.receiveMessage(Protos.TwoWayChannelMessage.parseFrom(message));
        } catch (InvalidProtocolBufferException e) {
            log.warn("Received an invalid message from service client");
            paymentChannelServer.connectionClosed();
        }
    }
}
