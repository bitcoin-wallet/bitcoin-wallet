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

    private final PaymentChannelClient paymentChannelClient;

    public PaymentChannelClientInstanceBinder(
            Wallet wallet,
            final IPaymentChannelCallbacks callbacks,
            Coin maxValue,
            Sha256Hash serverId,
            final long timeWindow
            ) {
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
                try {
                    callbacks.sendMessage(msg.toByteArray());
                } catch (RemoteException e) {
                    log.warn("Failed to deliver message to service client", e);
                    paymentChannelClient.connectionClosed();
                }
            }

            @Override
            public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {

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
    }

    @Override
    public void sendMessage(byte[] message) throws RemoteException {
        try {
            paymentChannelClient.receiveMessage(Protos.TwoWayChannelMessage.parseFrom(message));
        } catch (InvalidProtocolBufferException e) {
            log.warn("Received an invalid message from service client");
            paymentChannelClient.connectionClosed();
        } catch (InsufficientMoneyException e) {
            // TODO @w-shackleton display a message to the user saying what's happened.
            log.info("Not enough money in wallet to satisfy contract", e);
        }

    }

    @Override
    public boolean requestIncrement(long satoshis) throws RemoteException {
        // TODO @w-shackleton confirm with the user that they want to accept this payment
        try {
            // TODO @w-shackleton make dialog wait until we have ack
            @SuppressWarnings("unused")
            ListenableFuture<PaymentIncrementAck> ackFuture =
                    paymentChannelClient.incrementPayment(Coin.valueOf(satoshis));
            return true;
        } catch (ValueOutOfRangeException e) {
            log.info("Increment requested by server was out of range", e);
            return false;
        }
    }
}
