/*
 * Copyright 2011-2013 the original author or authors.
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
import java.util.Locale;

import javax.annotation.Nonnull;

import com.google.bitcoin.core.Utils;

/**
 * @author Andreas Schildbach
 */
public class GenericUtils
{
	private static final int COIN_INT = Utils.COIN.intValue();

	public static String formatValue(@Nonnull final BigInteger value, final int precision)
	{
		return formatValue(value, "", "-", precision);
	}

	public static String formatValue(@Nonnull final BigInteger value, @Nonnull final String plusSign, @Nonnull final String minusSign,
			final int precision)
	{
		long longValue = value.longValue();
		if (precision <= 2)
			longValue = longValue - longValue % 1000000 + longValue % 1000000 / 500000 * 1000000;
		else if (precision <= 4)
			longValue = longValue - longValue % 10000 + longValue % 10000 / 5000 * 10000;
		else if (precision <= 6)
			longValue = longValue - longValue % 100 + longValue % 100 / 50 * 100;

		final String sign = longValue < 0 ? minusSign : plusSign;

		final long absValue = Math.abs(longValue);
		final int coins = (int) (absValue / COIN_INT);
		final int satoshis = (int) (absValue % COIN_INT);

		if (satoshis % 1000000 == 0)
			return String.format(Locale.US, "%s%d.%02d", sign, coins, satoshis / 1000000);
		else if (satoshis % 10000 == 0)
			return String.format(Locale.US, "%s%d.%04d", sign, coins, satoshis / 10000);
		else if (satoshis % 100 == 0)
			return String.format(Locale.US, "%s%d.%06d", sign, coins, satoshis / 100);
		else
			return String.format(Locale.US, "%s%d.%08d", sign, coins, satoshis);
	}
}
