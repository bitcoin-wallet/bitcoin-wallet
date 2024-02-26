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

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import androidx.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Andreas Schildbach
 */
public class Nfc {
    private static final Logger log = LoggerFactory.getLogger(Nfc.class);

    public static NdefRecord createMime(final String mimeType, final byte[] payload) {
        final byte[] mimeBytes = mimeType.getBytes(StandardCharsets.US_ASCII);
        return new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
    }

    @Nullable
    public static byte[] extractMimePayload(final String mimeType, final NdefMessage message) {
        final byte[] mimeBytes = mimeType.getBytes(StandardCharsets.US_ASCII);

        for (final NdefRecord record : message.getRecords()) {
            if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(record.getType(), mimeBytes))
                return record.getPayload();
        }

        return null;
    }

    public static void setNdefPushMessage(final NfcAdapter adapter, final NdefMessage message,
                                          final Activity activity) {
        try {
            // reflection hack needed for Android 14 and above
            final Method setNdefPushMessage = adapter.getClass().getMethod("setNdefPushMessage",
                    NdefMessage.class, Activity.class, Activity[].class);
            setNdefPushMessage.invoke(adapter, message, activity, new Activity[0]);
        } catch (final ReflectiveOperationException x) {
            log.info("problem setting NDEF push message", x);
        } catch (final Exception x) {
            throw new RuntimeException(x);
        }
    }
}
