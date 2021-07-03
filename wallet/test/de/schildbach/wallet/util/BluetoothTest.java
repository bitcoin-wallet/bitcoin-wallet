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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Andreas Schildbach
 */
public class BluetoothTest {
    @Test
    public void compressMac() {
        assertEquals("11223344556677", Bluetooth.compressMac("11:22:33:44:55:66:77"));
        assertEquals("110A3344550B00", Bluetooth.compressMac("11:A:33:44:55:B:"));
        assertEquals("AA", Bluetooth.compressMac("aa"));
        assertEquals("00", Bluetooth.compressMac(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void compressMac_oversizedSegment() {
        Bluetooth.compressMac("111");
    }

    @Test(expected = IllegalArgumentException.class)
    public void compressMac_illegalCharacter() {
        Bluetooth.compressMac("1z");
    }

    @Test
    public void decompressMac() {
        assertEquals("11:22:33:44:55:66:77", Bluetooth.decompressMac("11223344556677"));
        assertEquals("11:0A:33:44:55:0B:00", Bluetooth.decompressMac("110A3344550B00"));
        assertEquals("AA", Bluetooth.decompressMac("aa"));
        assertEquals("", Bluetooth.decompressMac(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decompressMac_impossibleLength() {
        Bluetooth.decompressMac("123");
    }

    @Test(expected = IllegalArgumentException.class)
    public void decompressMac_illegalCharacter() {
        Bluetooth.decompressMac("1z");
    }

    @Test
    public void compressDecompressMac() throws Exception {
        final String mac = "00:11:22:33:44:55:66";
        assertEquals(mac, Bluetooth.decompressMac(Bluetooth.compressMac(mac)));
    }

    @Test
    public void isBluetoothUri() throws Exception {
        assertTrue(Bluetooth.isBluetoothUrl("bt:00112233445566"));
        assertTrue(Bluetooth.isBluetoothUrl("BT:00112233445566"));
    }

    @Test
    public void getBluetooth() throws Exception {
        final String simpleUri = "bt:00112233445566";
        assertEquals("00112233445566", Bluetooth.getBluetoothMac(simpleUri));
        assertEquals("/", Bluetooth.getBluetoothQuery(simpleUri));

        final String queryUri = "bt:00112233445566/abc";
        assertEquals("00112233445566", Bluetooth.getBluetoothMac(queryUri));
        assertEquals("/abc", Bluetooth.getBluetoothQuery(queryUri));
    }
}
