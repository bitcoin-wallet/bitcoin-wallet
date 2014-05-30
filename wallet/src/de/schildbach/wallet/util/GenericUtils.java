/*
 * Copyright 2011-2014 the original author or authors.
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

import javax.annotation.Nonnull;

import android.view.View;
import android.widget.TextView;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.NetworkParameters;

import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class GenericUtils
{
	private static final int ONE_BTC_INT = (int) Coin.COIN.value;
	private static final int ONE_MBTC_INT = (int) Coin.MILLICOIN.value;
	private static final int ONE_UBTC_INT = (int) Coin.MICROCOIN.value;

	public static String formatValue(@Nonnull final Coin value, final int precision, final int shift)
	{
		return formatValue(value, "", "-", precision, shift);
	}

	public static String formatValue(@Nonnull final Coin value, @Nonnull final String plusSign, @Nonnull final String minusSign, final int precision,
			final int shift)
	{
		long longValue = value.value;

		final String sign = longValue < 0 ? minusSign : plusSign;

		if (shift == 0)
		{
			if (precision == 2)
				longValue = longValue - longValue % 1000000 + longValue % 1000000 / 500000 * 1000000;
			else if (precision == 4)
				longValue = longValue - longValue % 10000 + longValue % 10000 / 5000 * 10000;
			else if (precision == 6)
				longValue = longValue - longValue % 100 + longValue % 100 / 50 * 100;
			else if (precision == 8)
				;
			else
				throw new IllegalArgumentException("cannot handle precision/shift: " + precision + "/" + shift);

			final long absValue = Math.abs(longValue);
			final long coins = absValue / ONE_BTC_INT;
			final int satoshis = (int) (absValue % ONE_BTC_INT);

			if (satoshis % 1000000 == 0)
				return String.format(Locale.US, "%s%d.%02d", sign, coins, satoshis / 1000000);
			else if (satoshis % 10000 == 0)
				return String.format(Locale.US, "%s%d.%04d", sign, coins, satoshis / 10000);
			else if (satoshis % 100 == 0)
				return String.format(Locale.US, "%s%d.%06d", sign, coins, satoshis / 100);
			else
				return String.format(Locale.US, "%s%d.%08d", sign, coins, satoshis);
		}
		else if (shift == 3)
		{
			if (precision == 2)
				longValue = longValue - longValue % 1000 + longValue % 1000 / 500 * 1000;
			else if (precision == 4)
				longValue = longValue - longValue % 10 + longValue % 10 / 5 * 10;
			else if (precision == 5)
				;
			else
				throw new IllegalArgumentException("cannot handle precision/shift: " + precision + "/" + shift);

			final long absValue = Math.abs(longValue);
			final long coins = absValue / ONE_MBTC_INT;
			final int satoshis = (int) (absValue % ONE_MBTC_INT);

			if (satoshis % 1000 == 0)
				return String.format(Locale.US, "%s%d.%02d", sign, coins, satoshis / 1000);
			else if (satoshis % 10 == 0)
				return String.format(Locale.US, "%s%d.%04d", sign, coins, satoshis / 10);
			else
				return String.format(Locale.US, "%s%d.%05d", sign, coins, satoshis);
		}
		else if (shift == 6)
		{
			if (precision == 0)
				longValue = longValue - longValue % 100 + longValue % 100 / 50 * 100;
			else if (precision == 2)
				;
			else
				throw new IllegalArgumentException("cannot handle precision/shift: " + precision + "/" + shift);

			final long absValue = Math.abs(longValue);
			final long coins = absValue / ONE_UBTC_INT;
			final int satoshis = (int) (absValue % ONE_UBTC_INT);

			if (satoshis % 100 == 0)
				return String.format(Locale.US, "%s%d", sign, coins);
			else
				return String.format(Locale.US, "%s%d.%02d", sign, coins, satoshis);
		}
		else
		{
			throw new IllegalArgumentException("cannot handle shift: " + shift);
		}
	}

	public static String formatDebugValue(@Nonnull final Coin value)
	{
		return formatValue(value, Constants.BTC_MAX_PRECISION, 0);
	}

	public static Coin parseCoin(final String str, final int shift) throws ArithmeticException
	{
		final Coin coin = Coin.valueOf(new BigDecimal(str).movePointRight(8 - shift).toBigIntegerExact().longValue());

		if (coin.signum() < 0)
			throw new ArithmeticException("negative amount: " + str);
		if (coin.compareTo(NetworkParameters.MAX_MONEY) > 0)
			throw new ArithmeticException("amount too large: " + str);

		return coin;
	}

	public static boolean startsWithIgnoreCase(final String string, final String prefix)
	{
		return string.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	public static void setNextFocusForwardId(final View view, final int nextFocusForwardId)
	{
		try
		{
			final Method setNextFocusForwardId = TextView.class.getMethod("setNextFocusForwardId", Integer.TYPE);
			setNextFocusForwardId.invoke(view, nextFocusForwardId);
		}
		catch (final NoSuchMethodException x)
		{
			// expected on API levels below 11
		}
		catch (final Exception x)
		{
			throw new RuntimeException(x);
		}
	}

	public static String currencySymbol(@Nonnull final String currencyCode)
	{
		try
		{
			final Currency currency = Currency.getInstance(currencyCode);
			return currency.getSymbol();
		}
		catch (final IllegalArgumentException x)
		{
			return currencyCode;
		}
	}
}
