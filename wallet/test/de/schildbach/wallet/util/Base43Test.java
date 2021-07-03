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

package de.schildbach.wallet.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Andreas Schildbach
 */
public class Base43Test {
    @Test
    public void encode() throws Exception {
        assertEquals("", Base43.encode(new byte[0]));

        assertEquals("0", Base43.encode(new byte[] { 0x0 }));

        assertEquals("5.", Base43.encode(new byte[] { (byte) 0xff }));

        assertEquals("RNO2-MYFN0D35RHM", Base43.encode("Hello World".getBytes()));
    }

    @Test
    public void decode() throws Exception {
        assertArrayEquals(new byte[0], Base43.decode(""));

        assertArrayEquals(new byte[] { 0x0 }, Base43.decode("0"));

        assertArrayEquals(new byte[] { (byte) 0xff }, Base43.decode("5."));

        assertArrayEquals("Hello World".getBytes(), Base43.decode("RNO2-MYFN0D35RHM"));
    }
}
