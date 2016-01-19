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

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.google.common.util.concurrent.SettableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;

/**
 * An Android service for exposing the bitcoinj payment channel system to other apps, such that they
 * can request and send micropayments to and from the device's local wallet.
 */
public class PaymentChannelService extends Service {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelService.class);

    private SettableFuture<BlockchainService> blockchainServiceFuture;

    @Override
    public void onCreate() {
        super.onCreate();
        Intent blockchainServiceIntent = new Intent(this, BlockchainServiceImpl.class);
        if (bindService(blockchainServiceIntent, blockchainServiceConn, BIND_AUTO_CREATE)) {
            log.debug("Binding to blockchain service");
            blockchainServiceFuture = SettableFuture.create();
        } else {
            log.warn("Failed to connect to blockchain service");
        }
    }

    @Override
    public void onDestroy() {
        unbindService(blockchainServiceConn);
        super.onDestroy();
    }

    private ServiceConnection blockchainServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            log.debug("Bound to blockchain service");
            blockchainServiceFuture.set(((BlockchainServiceImpl.LocalBinder) binder).getService());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            blockchainServiceFuture = null;
            log.debug("Disconnecting from blockchain service");
        }
    };

    private WalletApplication getWalletApplication() {
        return (WalletApplication) getApplication();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log.debug("onBind()");
        if (blockchainServiceFuture == null) {
            return null;
        }
        return new PaymentChannelsBinder(
                getWalletApplication().getWallet(),
                blockchainServiceFuture);
    }
}
