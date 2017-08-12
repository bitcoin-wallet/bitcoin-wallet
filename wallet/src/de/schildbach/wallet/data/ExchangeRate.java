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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.data;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRate {
    public ExchangeRate(final org.bitcoinj.utils.ExchangeRate rate, final String source) {
        checkNotNull(rate.fiat.currencyCode);

        this.rate = rate;
        this.source = source;
    }

    public final org.bitcoinj.utils.ExchangeRate rate;
    public final String source;

    public String getCurrencyCode() {
        return rate.fiat.currencyCode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + rate.fiat + ']';
    }
}