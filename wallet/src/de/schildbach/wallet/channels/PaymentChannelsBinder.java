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

import android.os.RemoteException;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBroadcaster;
import org.bitcoinj.core.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.schildbach.wallet.WalletApplication;

/**
 * Binder for the IPaymentChannels interface.
 */
public class PaymentChannelsBinder extends IPaymentChannels.Stub {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelsBinder.class);

    private final PaymentChannelService parent;
    private final Wallet wallet;
    private final Future<? extends TransactionBroadcaster> transactionBroadcaster;

    public PaymentChannelsBinder(PaymentChannelService parent, Wallet wallet, Future<? extends TransactionBroadcaster> transactionBroadcaster) {
        this.parent = parent;
        this.wallet = wallet;
        this.transactionBroadcaster = transactionBroadcaster;
    }

    @Override
    public IPaymentChannelServerInstance createChannelToWallet(IPaymentChannelCallbacks callbacks) throws RemoteException {
        checkFeatureEnabled();
        try {
            return new PaymentChannelServerInstanceBinder(
                    wallet,
                    transactionBroadcaster.get(2, TimeUnit.SECONDS),
                    parent,
                    callbacks);
        } catch (InterruptedException e) {
            log.warn("Failed to connect to blockchain service", e);
        } catch (ExecutionException e) {
            log.warn("Failed to connect to blockchain service", e);
        } catch (TimeoutException e) {
            log.warn("Failed to connect to blockchain service", e);
        }
        return null;
    }

    @Override
    public IPaymentChannelClientInstance createChannelFromWallet(
            IPaymentChannelCallbacks callbacks,
            long requestedMaxValue,
            byte[] serverId,
            long requestedTimeWindow) throws RemoteException {
        checkFeatureEnabled();
        // Null ID was masqueraded as an empty array
        Sha256Hash serverIdHash = serverId.length == 0 ? Sha256Hash.ZERO_HASH : Sha256Hash.wrap(serverId);
        return parent.createClientChannel(
                wallet,
                callbacks,
                Coin.valueOf(requestedMaxValue),
                serverIdHash,
                requestedTimeWindow);
    }

    private void checkFeatureEnabled() throws RemoteException {
        WalletApplication app = parent.getWalletApplication();
        if (!app.getConfiguration().getPaymentChannelsEnabled()) {
            throw new RemoteException("Payment channels feature not enabled in this wallet");
        }
    }
}
