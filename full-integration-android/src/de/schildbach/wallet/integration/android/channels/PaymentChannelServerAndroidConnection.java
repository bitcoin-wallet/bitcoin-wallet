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
package de.schildbach.wallet.integration.android.channels;

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
import org.bitcoinj.protocols.channels.ServerConnectionEventHandler;
import org.bitcoinj.protocols.channels.StoredPaymentChannelServerStates;
import org.bitcoinj.protocols.channels.StoredServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.annotation.Nullable;

import de.schildbach.wallet.channels.IPaymentChannelClientInstance;

/**
 * Implements a connection to the Bitcoin Wallet service that can accept money from a payment
 * channel client running in the wallet app. It event listeners when payments arrive. This is the
 * highest level class in the Android payment channels API. Internally, sends protobuf messages
 * to/from a newly created {@link PaymentChannelServer}.
 */
public class PaymentChannelServerAndroidConnection {
    private static final Logger log = LoggerFactory.getLogger(PaymentChannelServerAndroidConnection.class);

    private PaymentChannelCloseException.CloseReason closeReason;

    private final PaymentChannelServer channelServer;
    private final IPaymentChannelClientInstance remote;

    public static abstract class EventHandler {
        private PaymentChannelConnector.PaymentChannelCallbacks callbacks;

        synchronized void setRemoteCallbacks(@Nullable PaymentChannelConnector.PaymentChannelCallbacks callbacks) {
            this.callbacks = callbacks;
        }

        /**
         * <p>Closes the channel with the client (will generate a
         * {@link ServerConnectionEventHandler#channelClosed(PaymentChannelCloseException.CloseReason)} event)</p>
         *
         * <p>Note that this does <i>NOT</i> actually broadcast the most recent payment transaction, which will be triggered
         * automatically when the channel times out by the {@link StoredPaymentChannelServerStates}, or manually by calling
         * {@link StoredPaymentChannelServerStates#closeChannel(StoredServerChannel)} with the channel returned by
         * {@link StoredPaymentChannelServerStates#getChannel(org.bitcoinj.core.Sha256Hash)} with the id provided in
         * {@link ServerConnectionEventHandler#channelOpen(org.bitcoinj.core.Sha256Hash)}</p>
         */
        protected final synchronized void closeChannel() throws RemoteException {
            if (callbacks == null)
                throw new IllegalStateException("Channel is not fully initialized/has already been closed");
            callbacks.sendMessage(Protos.TwoWayChannelMessage.newBuilder()
                    .setType(Protos.TwoWayChannelMessage.MessageType.CLOSE)
                    .build().toByteArray());
            callbacks.closeConnection();
        }

        /**
         * Triggered when the channel is opened and application messages/payments can begin
         *
         * @param channelId A unique identifier which represents this channel (actually the hash of the multisig contract)
         */
        public abstract void channelOpen(Sha256Hash channelId);

        /**
         * Called when the payment in this channel was successfully incremented by the client
         *
         * @param by The increase in total payment
         * @param to The new total payment to us (not including fees which may be required to claim the payment)
         * @param info Information about this payment increase, used to extend this protocol.
         * @return acknowledgment information to be sent to the client.
         */
        @Nullable
        public abstract ListenableFuture<ByteString> paymentIncrease(Coin by, Coin to, ByteString info);

        /**
         * <p>Called when the channel was closed for some reason. May be called without a call to
         * {@link ServerConnectionEventHandler#channelOpen(Sha256Hash)}.</p>
         *
         * <p>Note that the same channel can be reopened at any point before it expires if the client reconnects and
         * requests it.</p>
         */
        public abstract void channelClosed(PaymentChannelCloseException.CloseReason reason);
    }

    /**
     * Sets up a new payment channel server which connects through the given {@link PaymentChannelConnector}
     *
     * @param connector The Wallet connector to initiate a connection through.
     * @param eventHandler An event handler that receives notifications of channel updates.
     * @param broadcaster The PeerGroup on which transactions will be broadcast - should have multiple connections.
     * @param wallet The wallet which will be used to complete transactions
     * @param minAcceptedChannelSize The minimum amount of coins clients must lock in to create a channel. Clients which
     *                               are unwilling or unable to lock in at least this value will immediately disconnect.
     *                               For this reason, a fairly conservative value (in terms of average value spent on a
     *                               channel) should generally be chosen.
     * @param requestedMaxValue The maximum value of channel we would like the wallet to create to us.
     * @param serverId The ID of a saved server to resume.
     * @param requestedTimeWindow The maxmimum amount of time we would like the client to create the channel for.
     */
    public PaymentChannelServerAndroidConnection(PaymentChannelConnector connector,
                                                 final EventHandler eventHandler,
                                                 TransactionBroadcaster broadcaster, Wallet wallet,
                                                 Coin minAcceptedChannelSize,
                                                 Coin requestedMaxValue, byte[] serverId,
                                                 long requestedTimeWindow)
            throws IOException, RemoteException, InterruptedException {
        final PaymentChannelConnector.PaymentChannelCallbacks remoteCallbacks =
                new PaymentChannelConnector.PaymentChannelCallbacks() {
                    @Override
                    public void sendMessage(byte[] message) throws RemoteException {
                        try {
                            log.debug("Receiving TwoWayChannelMessage {}", Protos.TwoWayChannelMessage.parseFrom(message).getType());
                            channelServer.receiveMessage(Protos.TwoWayChannelMessage.parseFrom(message));
                        } catch (InvalidProtocolBufferException e) {
                            log.warn("Failed to decode message received from wallet service", e);
                            channelServer.connectionClosed();
                        }
                    }

                    @Override
                    public void closeConnection() {
                        channelServer.connectionClosed();
                        if (closeReason != null) {
                            eventHandler.channelClosed(closeReason);
                        } else {
                            eventHandler.channelClosed(PaymentChannelCloseException.CloseReason.CONNECTION_CLOSED);
                        }
                        eventHandler.setRemoteCallbacks(null);
                    }
                };

        remote = connector.createChannelFromWallet(remoteCallbacks, requestedMaxValue.value, serverId, requestedTimeWindow);

        channelServer = new PaymentChannelServer(
                broadcaster,
                wallet,
                minAcceptedChannelSize,
                new PaymentChannelServer.ServerConnection() {
            @Override
            public void sendToClient(Protos.TwoWayChannelMessage msg) {
                try {
                    log.debug("Sending TwoWayChannelMessage {}", msg.getType());
                    remote.sendMessage(msg.toByteArray());
                } catch (RemoteException e) {
                    log.warn("Failed to send message to wallet service", e);
                    channelServer.connectionClosed();
                }
            }

            @Override
            public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
                if (closeReason != null) {
                    closeReason = reason;
                }
                try {
                    remote.closeConnection();
                } catch (RemoteException e) {
                    log.info("closeConnection() failed", e);
                }
            }

            @Override
            public void channelOpen(Sha256Hash contractHash) {
                eventHandler.channelOpen(contractHash);
            }

            @Nullable
            @Override
            public ListenableFuture<ByteString> paymentIncrease(Coin by, Coin to, ByteString info) {
                return eventHandler.paymentIncrease(by, to, info);
            }
        });

        eventHandler.setRemoteCallbacks(remoteCallbacks);
        channelServer.connectionOpen();
    }

    public void requestIncrement(Coin increment) throws RemoteException {
        remote.requestIncrement(increment.longValue());
    }

    public void settleChannel() throws RemoteException {
        remote.requestSettle();
    }
}
