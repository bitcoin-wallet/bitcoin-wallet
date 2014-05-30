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

package de.schildbach.wallet.util;

import static com.google.bitcoin.core.Coin.COIN;
import static com.google.bitcoin.core.Coin.SATOSHI;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.NetworkParameters;

/**
 * @author Andreas Schildbach
 */
public class GenericUtilsTest
{
	@Test
	public void formatValue() throws Exception
	{
		assertEquals("1.00", GenericUtils.formatValue(COIN, 4, 0));
		assertEquals("1.00", GenericUtils.formatValue(COIN, 6, 0));
		assertEquals("1.00", GenericUtils.formatValue(COIN, 8, 0));

		final Coin justNot = COIN.subtract(SATOSHI);
		assertEquals("1.00", GenericUtils.formatValue(justNot, 4, 0));
		assertEquals("1.00", GenericUtils.formatValue(justNot, 6, 0));
		assertEquals("0.99999999", GenericUtils.formatValue(justNot, 8, 0));

		final Coin slightlyMore = COIN.add(SATOSHI);
		assertEquals("1.00", GenericUtils.formatValue(slightlyMore, 4, 0));
		assertEquals("1.00", GenericUtils.formatValue(slightlyMore, 6, 0));
		assertEquals("1.00000001", GenericUtils.formatValue(slightlyMore, 8, 0));

		final Coin value = Coin.valueOf(1122334455667788l);
		assertEquals("11223344.5567", GenericUtils.formatValue(value, 4, 0));
		assertEquals("11223344.556678", GenericUtils.formatValue(value, 6, 0));
		assertEquals("11223344.55667788", GenericUtils.formatValue(value, 8, 0));

		assertEquals("21000000.00", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 8, 0));
	}

	@Test
	public void formatMbtcValue() throws Exception
	{
		assertEquals("1000.00", GenericUtils.formatValue(COIN, 2, 3));
		assertEquals("1000.00", GenericUtils.formatValue(COIN, 4, 3));

		final Coin justNot = COIN.subtract(SATOSHI.multiply(10));
		assertEquals("1000.00", GenericUtils.formatValue(justNot, 2, 3));
		assertEquals("999.9999", GenericUtils.formatValue(justNot, 4, 3));

		final Coin slightlyMore = COIN.add(SATOSHI.multiply(10));
		assertEquals("1000.00", GenericUtils.formatValue(slightlyMore, 2, 3));
		assertEquals("1000.0001", GenericUtils.formatValue(slightlyMore, 4, 3));

		final Coin value = Coin.valueOf(1122334455667788l);
		assertEquals("11223344556.68", GenericUtils.formatValue(value, 2, 3));
		assertEquals("11223344556.6779", GenericUtils.formatValue(value, 4, 3));

		assertEquals("21000000000.00", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 5, 3));
	}

	@Test
	public void formatUbtcValue() throws Exception
	{
		assertEquals("1000000", GenericUtils.formatValue(COIN, 0, 6));
		assertEquals("1000000", GenericUtils.formatValue(COIN, 2, 6));

		final Coin justNot = COIN.subtract(SATOSHI);
		assertEquals("1000000", GenericUtils.formatValue(justNot, 0, 6));
		assertEquals("999999.99", GenericUtils.formatValue(justNot, 2, 6));

		final Coin slightlyMore = COIN.add(SATOSHI);
		assertEquals("1000000", GenericUtils.formatValue(slightlyMore, 0, 6));
		assertEquals("1000000.01", GenericUtils.formatValue(slightlyMore, 2, 6));

		final Coin value = Coin.valueOf(1122334455667788l);
		assertEquals("11223344556678", GenericUtils.formatValue(value, 0, 6));
		assertEquals("11223344556677.88", GenericUtils.formatValue(value, 2, 6));

		assertEquals("21000000000000", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 2, 6));
	}
}
