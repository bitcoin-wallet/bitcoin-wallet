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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.view.View;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.ui.CurrencyAmountView.Listener;
import de.schildbach.wallet.util.WalletUtils;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class CurrencyCalculatorLink
{
	private final CurrencyAmountView btcAmountView;
	private final CurrencyAmountView localAmountView;

	private Listener listener = null;
	private boolean enabled = true;
	private ExchangeRate exchangeRate = null;
	private boolean exchangeDirection = true;

	private final CurrencyAmountView.Listener btcAmountViewListener = new CurrencyAmountView.Listener()
	{
		@Override
		public void changed()
		{
			if (btcAmountView.getAmount() != null)
			{
				exchangeDirection = true;

				update();
			}
			else
			{
				localAmountView.setHint(null);
			}

			if (listener != null)
				listener.changed();
		}

		@Override
		public void done()
		{
			if (listener != null)
				listener.done();
		}

		@Override
		public void focusChanged(final boolean hasFocus)
		{
			if (listener != null)
				listener.focusChanged(hasFocus);
		}
	};

	private final CurrencyAmountView.Listener localAmountViewListener = new CurrencyAmountView.Listener()
	{
		@Override
		public void changed()
		{
			if (localAmountView.getAmount() != null)
			{
				exchangeDirection = false;

				update();
			}
			else
			{
				btcAmountView.setHint(null);
			}

			if (listener != null)
				listener.changed();
		}

		@Override
		public void done()
		{
			if (listener != null)
				listener.done();
		}

		@Override
		public void focusChanged(final boolean hasFocus)
		{
			if (listener != null)
				listener.focusChanged(hasFocus);
		}
	};

	public CurrencyCalculatorLink(@Nonnull final CurrencyAmountView btcAmountView, @Nonnull final CurrencyAmountView localAmountView)
	{
		this.btcAmountView = btcAmountView;
		this.btcAmountView.setListener(btcAmountViewListener);

		this.localAmountView = localAmountView;
		this.localAmountView.setListener(localAmountViewListener);

		update();
	}

	public void setListener(@Nullable final Listener listener)
	{
		this.listener = listener;
	}

	public void setEnabled(final boolean enabled)
	{
		this.enabled = enabled;

		update();
	}

	public void setExchangeRate(@Nonnull final ExchangeRate exchangeRate)
	{
		this.exchangeRate = exchangeRate;

		update();
	}

	@CheckForNull
	public BigInteger getAmount()
	{
		if (exchangeDirection)
		{
			return btcAmountView.getAmount();
		}
		else if (exchangeRate != null)
		{
			final BigInteger localAmount = localAmountView.getAmount();
			return localAmount != null ? WalletUtils.btcValue(localAmount, exchangeRate.rate) : null;
		}
		else
		{
			return null;
		}
	}

	private void update()
	{
		btcAmountView.setEnabled(enabled);

		if (exchangeRate != null)
		{
			localAmountView.setEnabled(enabled);
			localAmountView.setCurrencySymbol(exchangeRate.currencyCode);

			if (exchangeDirection)
			{
				final BigInteger btcAmount = btcAmountView.getAmount();
				if (btcAmount != null)
				{
					localAmountView.setAmount(null, false);
					localAmountView.setHint(WalletUtils.localValue(btcAmount, exchangeRate.rate));
					btcAmountView.setHint(null);
				}
			}
			else
			{
				final BigInteger localAmount = localAmountView.getAmount();
				if (localAmount != null)
				{
					btcAmountView.setAmount(null, false);
					btcAmountView.setHint(WalletUtils.btcValue(localAmount, exchangeRate.rate));
					localAmountView.setHint(null);
				}
			}
		}
		else
		{
			localAmountView.setEnabled(false);
		}
	}

	public View activeView()
	{
		if (exchangeDirection)
			return btcAmountView;
		else
			return localAmountView;
	}

	public void requestFocus()
	{
		activeView().requestFocus();
	}

	public void setBtcAmount(@Nonnull final BigInteger amount)
	{
		btcAmountView.setAmount(amount, true);
	}
}
