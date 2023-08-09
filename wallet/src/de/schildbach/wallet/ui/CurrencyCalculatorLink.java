/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.view.View;
import androidx.annotation.Nullable;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.CurrencyAmountView.Listener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import java.util.Objects;

/**
 * @author Andreas Schildbach
 */
public final class CurrencyCalculatorLink {
    private final CurrencyAmountView btcAmountView;
    private final CurrencyAmountView localAmountView;

    private Listener listener = null;
    private boolean enabled = true;
    private ExchangeRate exchangeRate = null;
    private boolean exchangeDirection = true;

    private final CurrencyAmountView.Listener btcAmountViewListener = new CurrencyAmountView.Listener() {
        @Override
        public void changed() {
            if (btcAmountView.getAmount() != null)
                setExchangeDirection(true);
            else
                localAmountView.setHint(null);

            if (listener != null)
                listener.changed();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (listener != null)
                listener.focusChanged(hasFocus);
        }
    };

    private final CurrencyAmountView.Listener localAmountViewListener = new CurrencyAmountView.Listener() {
        @Override
        public void changed() {
            if (localAmountView.getAmount() != null)
                setExchangeDirection(false);
            else
                btcAmountView.setHint(null);

            if (listener != null)
                listener.changed();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (listener != null)
                listener.focusChanged(hasFocus);
        }
    };

    public CurrencyCalculatorLink(final CurrencyAmountView btcAmountView, final CurrencyAmountView localAmountView) {
        this.btcAmountView = btcAmountView;
        this.btcAmountView.setListener(btcAmountViewListener);

        this.localAmountView = localAmountView;
        this.localAmountView.setListener(localAmountViewListener);

        update();
    }

    public void setListener(@Nullable final Listener listener) {
        this.listener = listener;
    }

    public void setEnabled(final boolean enabled) {
        if (enabled != this.enabled) {
            this.enabled = enabled;
            update();
        }
    }

    public void setExchangeRate(final ExchangeRate exchangeRate) {
        if (!Objects.equals(exchangeRate, this.exchangeRate)) {
            this.exchangeRate = exchangeRate;
            update();
        }
    }

    public ExchangeRate getExchangeRate() {
        return exchangeRate;
    }

    @Nullable
    public Coin getAmount() {
        if (exchangeDirection)
            return (Coin) btcAmountView.getAmount();
        else if (exchangeRate != null)
            return fiatToCoin((Fiat) localAmountView.getAmount());
        else
            return null;
    }

    public boolean hasAmount() {
        return getAmount() != null;
    }

    private void update() {
        btcAmountView.setEnabled(enabled);

        if (exchangeRate != null) {
            localAmountView.setEnabled(enabled);
            localAmountView.setCurrencySymbol(exchangeRate.fiat.currencyCode);

            if (exchangeDirection) {
                final Coin btcAmount = (Coin) btcAmountView.getAmount();
                if (btcAmount != null) {
                    btcAmountView.setHint(null);
                    localAmountView.setAmount(null);
                    localAmountView.setHint(coinToFiat(btcAmount));
                }
            } else {
                final Fiat localAmount = (Fiat) localAmountView.getAmount();
                if (localAmount != null) {
                    localAmountView.setHint(null);
                    btcAmountView.setAmount(null);
                    btcAmountView.setHint(fiatToCoin(localAmount));
                }
            }
        } else {
            localAmountView.setEnabled(false);
            localAmountView.setHint(null);
            btcAmountView.setHint(null);
        }
    }

    public void setExchangeDirection(final boolean exchangeDirection) {
        if (exchangeDirection != this.exchangeDirection) {
            this.exchangeDirection = exchangeDirection;
            update();
        }
    }

    public boolean getExchangeDirection() {
        return exchangeDirection;
    }

    public View activeTextView() {
        if (exchangeDirection)
            return btcAmountView.getTextView();
        else
            return localAmountView.getTextView();
    }

    public void requestFocus() {
        activeTextView().requestFocus();
    }

    public void setBtcAmount(final Coin amount) {
        final Listener listener = this.listener;
        this.listener = null;

        if (exchangeDirection) {
            btcAmountView.setAmount(amount);
            if (exchangeRate != null)
                localAmountView.setHint(coinToFiat(amount));
        } else {
            btcAmountView.setHint(amount);
            if (exchangeRate != null)
                localAmountView.setAmount(coinToFiat(amount));
        }

        this.listener = listener;
    }

    public void setNextFocusId(final int nextFocusId) {
        btcAmountView.setNextFocusId(nextFocusId);
        localAmountView.setNextFocusId(nextFocusId);
    }

    private Fiat coinToFiat(final Coin coinAmount) {
        if (coinAmount == null)
            return null;
        try {
            return exchangeRate.coinToFiat(coinAmount);
        } catch (final ArithmeticException x) {
            return null;
        }
    }

    private Coin fiatToCoin(final Fiat fiatAmount) {
        if (fiatAmount == null)
            return null;
        try {
            final Coin coin = exchangeRate.fiatToCoin(fiatAmount);
            if (coin.isGreaterThan(Constants.NETWORK_PARAMETERS.getMaxMoney()))
                throw new ArithmeticException();
            return coin;
        } catch (final ArithmeticException x) {
            return null;
        }
    }
}
