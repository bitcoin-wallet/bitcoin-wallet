/*
 * Copyright 2013-2015 the original author or authors.
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

package de.schildbach.wallet.ui;

import java.util.concurrent.RejectedExecutionException;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;

import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

/**
 * @author Andreas Schildbach
 */
public final class WalletBalanceLoader extends AsyncTaskLoader<Coin> {
    private LocalBroadcastManager broadcastManager;
    private final Wallet wallet;

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceLoader.class);

    public WalletBalanceLoader(final Context context, final Wallet wallet) {
        super(context);

        this.broadcastManager = LocalBroadcastManager.getInstance(context.getApplicationContext());
        this.wallet = wallet;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, walletChangeListener);
        broadcastManager.registerReceiver(walletChangeReceiver,
                new IntentFilter(WalletApplication.ACTION_WALLET_REFERENCE_CHANGED));

        safeForceLoad();
    }

    @Override
    protected void onStopLoading() {
        broadcastManager.unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();

        super.onStopLoading();
    }

    @Override
    protected void onReset() {
        broadcastManager.unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();

        super.onReset();
    }

    @Override
    public Coin loadInBackground() {
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

        return wallet.getBalance(BalanceType.ESTIMATED);
    }

    private final ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            safeForceLoad();
        }
    };

    private final BroadcastReceiver walletChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            safeForceLoad();
        }
    };

    private void safeForceLoad() {
        try {
            forceLoad();
        } catch (final RejectedExecutionException x) {
            log.info("rejected execution: " + WalletBalanceLoader.this.toString());
        }
    }
}
