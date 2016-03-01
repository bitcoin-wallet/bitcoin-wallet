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
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoin.paymentchannel.Protos;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.protocols.channels.PaymentChannelClient;
import org.bitcoinj.protocols.channels.PaymentChannelClientConnection;
import org.bitcoinj.protocols.channels.PaymentChannelClientState;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;
import org.bitcoinj.protocols.channels.PaymentIncrementAck;
import org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates;
import org.bitcoinj.protocols.channels.ValueOutOfRangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.IOException;

import javax.annotation.Nullable;

import de.schildbach.wallet.channels.IPaymentChannelServerInstance;

/**
 * A utility class that runs the micropayment channel over an Android service.
 */
public class PaymentChannelClientAndroidConnection {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelClientAndroidConnection.class);

    private final SettableFuture<PaymentChannelClientAndroidConnection> channelOpenFuture = SettableFuture.create();

    private final PaymentChannelClient channelClient;

    private final IPaymentChannelServerInstance remote;

    /**
     * Attempts to open a new connection to and open a payment channel on the given connector, blocking until the
     * connection is open. The server is requested to keep the channel open for
     * {@link org.bitcoinj.protocols.channels.PaymentChannelClient#DEFAULT_TIME_WINDOW}
     * seconds. If the server proposes a longer time the channel will be closed.
     *
     * @param connector The Android service connector to use.
     * @param wallet The wallet which will be paid from, and where completed transactions will be committed.
     *               Must be unencrypted. Must already have a {@link StoredPaymentChannelClientStates} object in its extensions set.
     * @param myKey A freshly generated keypair used for the multisig contract and refund output.
     * @param maxValue The maximum value this channel is allowed to request
     * @param serverId A unique ID which is used to attempt reopening of an existing channel.
     *                 This must be unique to the server, and, if your application is exposing payment channels to some
     *                 API, this should also probably encompass some caller UID to avoid applications opening channels
     *                 which were created by others.
     *
     * @throws IOException if there's an issue using the network.
     * @throws ValueOutOfRangeException if the balance of wallet is lower than maxValue.
     */
    public PaymentChannelClientAndroidConnection(PaymentChannelConnector connector, Wallet wallet, ECKey myKey,
                                                 Coin maxValue, String serverId) throws IOException, ValueOutOfRangeException, RemoteException, InterruptedException {
        this(connector, wallet, myKey, maxValue, serverId,
                PaymentChannelClient.DEFAULT_TIME_WINDOW, null);
    }

    /**
     * Attempts to open a new connection to and open a payment channel on the given connector, blocking until the
     * connection is open.  The server is requested to keep the channel open for {@param timeWindow}
     * seconds. If the server proposes a longer time the channel will be closed.
     *
     * @param connector The Android service connector to use.
     * @param wallet The wallet which will be paid from, and where completed transactions will be committed.
     *               Can be encrypted if user key is supplied when needed. Must already have a
     *               {@link StoredPaymentChannelClientStates} object in its extensions set.
     * @param myKey A freshly generated keypair used for the multisig contract and refund output.
     * @param maxValue The maximum value this channel is allowed to request
     * @param serverId A unique ID which is used to attempt reopening of an existing channel.
     *                 This must be unique to the server, and, if your application is exposing payment channels to some
     *                 API, this should also probably encompass some caller UID to avoid applications opening channels
     *                 which were created by others.
     * @param timeWindow The time in seconds, relative to now, on how long this channel should be kept open.
     * @param userKeySetup Key derived from a user password, used to decrypt myKey, if it is encrypted, during setup.
     *
     * @throws IOException if there's an issue using the network.
     * @throws ValueOutOfRangeException if the balance of wallet is lower than maxValue.
     */
    public PaymentChannelClientAndroidConnection(PaymentChannelConnector connector, Wallet wallet, ECKey myKey,
                                                 Coin maxValue, String serverId, final long timeWindow,
                                                 @Nullable KeyParameter userKeySetup)
            throws IOException, ValueOutOfRangeException, RemoteException, InterruptedException {
        final PaymentChannelConnector.PaymentChannelCallbacks remoteCallbacks =
                new PaymentChannelConnector.PaymentChannelCallbacks() {
                    @Override
                    public void sendMessage(byte[] message) throws RemoteException {
                        try {
                            channelClient.receiveMessage(Protos.TwoWayChannelMessage.parseFrom(message));
                        } catch (InsufficientMoneyException e) {
                            channelOpenFuture.setException(e);
                        } catch (InvalidProtocolBufferException e) {
                            log.warn("Failed to decode message received from wallet service", e);
                            channelClient.connectionClosed();
                        }
                    }

                    @Override
                    public void closeConnection() {
                        channelClient.connectionClosed();
                        channelOpenFuture.setException(new PaymentChannelCloseException(
                                "Wallet connection was closed by remote",
                                PaymentChannelCloseException.CloseReason.CONNECTION_CLOSED));
                    }
                };
        remote = connector.createChannelToWallet(remoteCallbacks);
        channelClient = new PaymentChannelClient(wallet, myKey, maxValue, Sha256Hash.of(serverId.getBytes()), timeWindow,
                userKeySetup, new PaymentChannelClient.ClientConnection() {
            @Override
            public void sendToServer(Protos.TwoWayChannelMessage msg) {
                try {
                    remote.sendMessage(msg.toByteArray());
                } catch (RemoteException e) {
                    log.warn("Failed to send message to wallet service", e);
                    channelClient.connectionClosed();
                }
            }

            @Override
            public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
                channelOpenFuture.setException(new PaymentChannelCloseException(
                        "Payment channel client requested that the connection be closed: " +
                                reason, reason));
                disconnectWithoutSettlement();
            }

            @Override
            public boolean acceptExpireTime(long expireTime) {
                // Accept one extra minute to allow for latency and clock skew
                return expireTime <= (timeWindow + Utils.currentTimeSeconds() + 60);
            }

            @Override
            public void channelOpen(boolean wasInitiated) {
                channelOpenFuture.set(PaymentChannelClientAndroidConnection.this);
            }
        }, PaymentChannelClient.VersionSelector.VERSION_2);
        channelClient.connectionOpen();
    }

    /**
     * <p>Gets a future which returns this when the channel is successfully opened, or throws an exception if there is
     * an error before the channel has reached the open state.</p>
     *
     * <p>After this future completes successfully, you may call
     * {@link PaymentChannelClientAndroidConnection#incrementPayment(Coin)} or
     * {@link PaymentChannelClientAndroidConnection#incrementPayment(Coin, com.google.protobuf.ByteString, KeyParameter)} to
     * begin paying the server.</p>
     */
    public ListenableFuture<PaymentChannelClientAndroidConnection> getChannelOpenFuture() {
        return channelOpenFuture;
    }


    /**
     * Increments the total value which we pay the server.
     *
     * @param size How many satoshis to increment the payment by (note: not the new total).
     * @throws ValueOutOfRangeException If the size is negative or would pay more than this channel's total value
     *                                  ({@link PaymentChannelClientConnection#state()}.getTotalValue())
     * @throws IllegalStateException If the channel has been closed or is not yet open
     *                               (see {@link PaymentChannelClientConnection#getChannelOpenFuture()} for the second)
     */
    public ListenableFuture<PaymentIncrementAck> incrementPayment(Coin size) throws ValueOutOfRangeException, IllegalStateException {
        return incrementPayment(size, null, null);
    }

    /**
     * Increments the total value which we pay the server.
     *
     * @param size How many satoshis to increment the payment by (note: not the new total).
     * @param info Information about this payment increment, used to extend this protocol.
     * @param userKey Key derived from a user password, needed for any signing when the wallet is encrypted.
     *                The wallet KeyCrypter is assumed.
     * @throws ValueOutOfRangeException If the size is negative or would pay more than this channel's total value
     *                                  ({@link PaymentChannelClientConnection#state()}.getTotalValue())
     * @throws IllegalStateException If the channel has been closed or is not yet open
     *                               (see {@link PaymentChannelClientConnection#getChannelOpenFuture()} for the second)
     */
    public ListenableFuture<PaymentIncrementAck> incrementPayment(Coin size,
                                                                  @Nullable ByteString info,
                                                                  @Nullable KeyParameter userKey)
            throws ValueOutOfRangeException, IllegalStateException {
        return channelClient.incrementPayment(size, info, userKey);
    }

    /**
     * <p>Gets the {@link PaymentChannelClientState} object which stores the current state of the connection with the
     * server.</p>
     *
     * <p>Note that if you call any methods which update state directly the server will not be notified and channel
     * initialization logic in the connection may fail unexpectedly.</p>
     */
    public PaymentChannelClientState state() {
        return channelClient.state();
    }

    /**
     * Closes the connection, notifying the server it should settle the channel by broadcasting the most recent payment
     * transaction.
     */
    public void settle() {
        // Shutdown is a little complicated.
        //
        // This call will cause the CLOSE message to be written to the wire, and then the destroyConnection() method that
        // we defined above will be called, which in turn will call wireParser.closeConnection(), which in turn will invoke
        // NioClient.closeConnection(), which will then close the socket triggering interruption of the network
        // thread it had created. That causes the background thread to die, which on its way out calls
        // ProtobufConnection.connectionClosed which invokes the connectionClosed method we defined above which in turn
        // then configures the open-future correctly and closes the state object. Phew!
        try {
            channelClient.settle();
        } catch (IllegalStateException e) {
            // Already closed...oh well
        }
    }

    /**
     * Disconnects the network connection but doesn't request the server to settle the channel first (literally just
     * unplugs the network socket and marks the stored channel state as inactive).
     */
    public void disconnectWithoutSettlement() {
        channelClient.connectionClosed();
        try {
            remote.closeConnection();
        } catch (RemoteException e) {
            log.info("closeConnection() failed", e);
        }
    }
}
