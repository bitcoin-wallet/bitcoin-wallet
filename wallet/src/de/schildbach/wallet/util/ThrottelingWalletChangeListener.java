/*
 * Copyright 2013 the original author or authors.
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

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

import android.os.Handler;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

/**
 * @author Andreas Schildbach
 */
public abstract class ThrottelingWalletChangeListener implements WalletEventListener
{
	private final long throttleMs;
	private static final long DEFAULT_THROTTLE_MS = 250;
	private final AtomicLong lastMessageTime = new AtomicLong(0);
	private final Handler handler = new Handler();

	public ThrottelingWalletChangeListener()
	{
		this(DEFAULT_THROTTLE_MS);
	}

	public ThrottelingWalletChangeListener(final long throttleMs)
	{
		this.throttleMs = throttleMs;
	}

	public final void onWalletChanged(final Wallet wallet)
	{
		handler.removeCallbacksAndMessages(null);

		final long now = System.currentTimeMillis();

		if (now - lastMessageTime.get() > throttleMs)
			handler.post(runnable);
		else
			handler.postDelayed(runnable, throttleMs);
	}

	private final Runnable runnable = new Runnable()
	{
		public void run()
		{
			lastMessageTime.set(System.currentTimeMillis());

			onThrotteledWalletChanged();
		}
	};

	public void removeCallbacks()
	{
		handler.removeCallbacksAndMessages(null);
	}

	/** will be called back on UI thread */
	public abstract void onThrotteledWalletChanged();

	public void onCoinsReceived(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
	{
		// swallow
	}

	public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
	{
		// swallow
	}

	public void onReorganize(final Wallet wallet)
	{
		// swallow
	}

	public void onTransactionConfidenceChanged(final Wallet wallet, final Transaction tx)
	{
		// swallow
	}

	public void onKeyAdded(final ECKey key)
	{
		// swallow
	}
}
