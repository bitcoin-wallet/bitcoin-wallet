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

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBroadcaster;
import org.bitcoinj.core.Wallet;

/**
 * Binder for the IPaymentChannels interface.
 */
public class PaymentChannelsBinder extends IPaymentChannels.Stub {

    private final Wallet wallet;
    private final TransactionBroadcaster transactionBroadcaster;

    public PaymentChannelsBinder(Wallet wallet, TransactionBroadcaster transactionBroadcaster) {
        this.wallet = wallet;
        this.transactionBroadcaster = transactionBroadcaster;
    }

    @Override
    public IPaymentChannelServerInstance createChannelToWallet(IPaymentChannelCallbacks callbacks) throws RemoteException {
        return new PaymentChannelServerInstanceBinder(wallet, transactionBroadcaster, callbacks);
    }

    @Override
    public IPaymentChannelClientInstance createChannelFromWallet(
            IPaymentChannelCallbacks callbacks,
            long requestedMaxValue,
            byte[] serverId,
            long requestedTimeWindow) throws RemoteException {
        return new PaymentChannelClientInstanceBinder(
                wallet,
                callbacks,
                Coin.valueOf(requestedMaxValue),
                Sha256Hash.wrap(serverId),
                requestedTimeWindow);
    }

}
