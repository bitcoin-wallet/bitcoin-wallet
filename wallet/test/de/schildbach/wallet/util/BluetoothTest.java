/*
 * Copyright 2014-2015 the original author or authors.
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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Andreas Schildbach
 */
public class BluetoothTest {
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
