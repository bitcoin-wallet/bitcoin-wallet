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

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

/**
 * @author Andreas Schildbach
 */
public class GenericUtilsTest
{
	@Test
	public void formatValue() throws Exception
	{
		final BigInteger coin = new BigInteger("100000000");
		assertEquals("1.00", GenericUtils.formatValue(coin, 4));
		assertEquals("1.00", GenericUtils.formatValue(coin, 6));
		assertEquals("1.00", GenericUtils.formatValue(coin, 8));

		final BigInteger justNot = new BigInteger("99999999");
		assertEquals("1.00", GenericUtils.formatValue(justNot, 4));
		assertEquals("1.00", GenericUtils.formatValue(justNot, 6));
		assertEquals("0.99999999", GenericUtils.formatValue(justNot, 8));

		final BigInteger slightlyMore = new BigInteger("100000001");
		assertEquals("1.00", GenericUtils.formatValue(slightlyMore, 4));
		assertEquals("1.00", GenericUtils.formatValue(slightlyMore, 6));
		assertEquals("1.00000001", GenericUtils.formatValue(slightlyMore, 8));

		final BigInteger value = new BigInteger("1122334455667788");
		assertEquals("11223344.5567", GenericUtils.formatValue(value, 4));
		assertEquals("11223344.556678", GenericUtils.formatValue(value, 6));
		assertEquals("11223344.55667788", GenericUtils.formatValue(value, 8));
	}
}
