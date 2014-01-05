/*
 * Copyright 2013-2014 the original author or authors.
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

import java.math.BigInteger;
import java.util.List;

import javax.annotation.Nonnull;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;

import com.google.bitcoin.script.Script;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class WalletBalanceLoader extends AsyncTaskLoader<BigInteger>
{
	private final Wallet wallet;

	public WalletBalanceLoader(final Context context, @Nonnull final Wallet wallet)
	{
		super(context);

		this.wallet = wallet;
	}

	@Override
	protected void onStartLoading()
	{
		super.onStartLoading();

		wallet.addEventListener(walletChangeListener);

		forceLoad();
	}

	@Override
	protected void onStopLoading()
	{
		wallet.removeEventListener(walletChangeListener);
		walletChangeListener.removeCallbacks();

		super.onStopLoading();
	}

	@Override
	public BigInteger loadInBackground()
	{
		return wallet.getBalance(BalanceType.ESTIMATED);
	}

	private final ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener()
	{
		@Override
		public void onThrottledWalletChanged()
		{
			forceLoad();
		}

        @Override
        public void onScriptsAdded(Wallet wallet, List<Script> scripts) { }
    };
}
