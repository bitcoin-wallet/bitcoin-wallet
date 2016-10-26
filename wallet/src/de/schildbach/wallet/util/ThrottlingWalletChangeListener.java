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

package de.schildbach.wallet.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.bitcoinj.wallet.listeners.WalletReorganizeEventListener;

import android.os.Handler;

/**
 * @author Andreas Schildbach
 */
public abstract class ThrottlingWalletChangeListener implements WalletChangeEventListener, WalletCoinsSentEventListener,
        WalletCoinsReceivedEventListener, WalletReorganizeEventListener, TransactionConfidenceEventListener {
    private final long throttleMs;
    private final boolean coinsRelevant;
    private final boolean reorganizeRelevant;
    private final boolean confidenceRelevant;

    private final AtomicLong lastMessageTime = new AtomicLong(0);
    private final Handler handler = new Handler();
    private final AtomicBoolean relevant = new AtomicBoolean();

    private static final long DEFAULT_THROTTLE_MS = 500;

    public ThrottlingWalletChangeListener() {
        this(DEFAULT_THROTTLE_MS);
    }

    public ThrottlingWalletChangeListener(final long throttleMs) {
        this(throttleMs, true, true, true);
    }

    public ThrottlingWalletChangeListener(final boolean coinsRelevant, final boolean reorganizeRelevant,
            final boolean confidenceRelevant) {
        this(DEFAULT_THROTTLE_MS, coinsRelevant, reorganizeRelevant, confidenceRelevant);
    }

    public ThrottlingWalletChangeListener(final long throttleMs, final boolean coinsRelevant,
            final boolean reorganizeRelevant, final boolean confidenceRelevant) {
        this.throttleMs = throttleMs;
        this.coinsRelevant = coinsRelevant;
        this.reorganizeRelevant = reorganizeRelevant;
        this.confidenceRelevant = confidenceRelevant;
    }

    @Override
    public final void onWalletChanged(final Wallet wallet) {
        if (relevant.getAndSet(false)) {
            handler.removeCallbacksAndMessages(null);

            final long now = System.currentTimeMillis();

            if (now - lastMessageTime.get() > throttleMs)
                handler.post(runnable);
            else
                handler.postDelayed(runnable, throttleMs);
        }
    }

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            lastMessageTime.set(System.currentTimeMillis());

            onThrottledWalletChanged();
        }
    };

    public void removeCallbacks() {
        handler.removeCallbacksAndMessages(null);
    }

    /** will be called back on UI thread */
    public abstract void onThrottledWalletChanged();

    @Override
    public void onCoinsReceived(final Wallet wallet, final Transaction tx, final Coin prevBalance,
            final Coin newBalance) {
        if (coinsRelevant)
            relevant.set(true);
    }

    @Override
    public void onCoinsSent(final Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
        if (coinsRelevant)
            relevant.set(true);
    }

    @Override
    public void onReorganize(final Wallet wallet) {
        if (reorganizeRelevant)
            relevant.set(true);
    }

    @Override
    public void onTransactionConfidenceChanged(final Wallet wallet, final Transaction tx) {
        if (confidenceRelevant)
            relevant.set(true);
    }
}
