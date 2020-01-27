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

import android.bluetooth.BluetoothAdapter;
import androidx.annotation.Nullable;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.UUID;

/**
 * @author Andreas Schildbach
 */
public class Bluetooth {
    /** Used for local fetching of BIP70 payment requests. */
    public static final UUID PAYMENT_REQUESTS_UUID = UUID.fromString("3357A7BB-762D-464A-8D9A-DCA592D57D59");
    /** Used for talking BIP70 payment messages and payment acks locally. */
    public static final UUID BIP70_PAYMENT_PROTOCOL_UUID = UUID.fromString("3357A7BB-762D-464A-8D9A-DCA592D57D5A");
    public static final String BIP70_PAYMENT_PROTOCOL_NAME = "Bitcoin BIP70 payment protocol";
    /** Used for talking the deprecated pre-BIP70 payment protocol. */
    public static final UUID CLASSIC_PAYMENT_PROTOCOL_UUID = UUID.fromString("3357A7BB-762D-464A-8D9A-DCA592D57D5B");
    public static final String CLASSIC_PAYMENT_PROTOCOL_NAME = "Bitcoin classic payment protocol (deprecated)";
    /** This URI parameter holds the MAC address for the deprecated pre-BIP70 payment protocol. */
    public static final String MAC_URI_PARAM = "bt";
    /** Android 6 uses this MAC address instead of the real one. */
    private static final String MARSHMELLOW_FAKE_MAC = "02:00:00:00:00:00";

    private static final Logger log = LoggerFactory.getLogger(Bluetooth.class);

    public static @Nullable String getAddress(final BluetoothAdapter adapter) {
        if (adapter == null)
            return null;

        final String address = adapter.getAddress();
        if (!MARSHMELLOW_FAKE_MAC.equals(address))
            return address;

        // Horrible reflection hack needed to get the Bluetooth MAC for Marshmellow and above.
        try {
            final Field mServiceField = BluetoothAdapter.class.getDeclaredField("mService");
            mServiceField.setAccessible(true);
            final Object mService = mServiceField.get(adapter);
            if (mService == null)
                return null;
            return (String) mService.getClass().getMethod("getAddress").invoke(mService);
        } catch (final InvocationTargetException x) {
            log.info("Problem determining Bluetooth MAC via reflection", x);
            return null;
        } catch (final Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static String compressMac(final String decompressedMac) throws IllegalArgumentException {
        final StringBuilder compressedMac = new StringBuilder();
        for (final CharSequence segment : Splitter.on(':').split(decompressedMac)) {
            if (segment.length() > 2)
                throw new IllegalArgumentException("Oversized segment in: " + decompressedMac);
            for (int i = 0; i < segment.length(); i++) {
                final char c = segment.charAt(i);
                if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F'))
                    throw new IllegalArgumentException("Illegal character '" + c + "' in: " + decompressedMac);
            }
            compressedMac.append(Strings.padStart(segment.toString(), 2, '0').toUpperCase(Locale.US));
        }
        return compressedMac.toString();
    }

    public static String decompressMac(final String compressedMac) throws IllegalArgumentException {
        if (compressedMac.length() % 2 != 0)
            throw new IllegalArgumentException("Impossible length: " + compressedMac);
        final StringBuilder decompressedMac = new StringBuilder();
        for (int i = 0; i < compressedMac.length(); i++) {
            final char c = compressedMac.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F'))
                throw new IllegalArgumentException("Illegal character '" + c + "' in: " + compressedMac);
            if (i % 2 == 0 && decompressedMac.length() > 0)
                decompressedMac.append(':');
            decompressedMac.append(Character.toUpperCase(c));
        }
        return decompressedMac.toString();
    }

    public static boolean isBluetoothUrl(final String url) {
        return url != null && GenericUtils.startsWithIgnoreCase(url, "bt:");
    }

    public static String getBluetoothMac(final String url) {
        if (!isBluetoothUrl(url))
            throw new IllegalArgumentException(url);

        final int queryIndex = url.indexOf('/');
        if (queryIndex != -1)
            return url.substring(3, queryIndex);
        else
            return url.substring(3);
    }

    public static String getBluetoothQuery(final String url) {
        if (!isBluetoothUrl(url))
            throw new IllegalArgumentException(url);

        final int queryIndex = url.indexOf('/');
        if (queryIndex != -1)
            return url.substring(queryIndex);
        else
            return "/";
    }
}
