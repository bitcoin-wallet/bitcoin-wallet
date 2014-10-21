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

import java.util.Arrays;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;

import com.google.common.base.Charsets;

/**
 * @author Andreas Schildbach
 */
public class Nfc
{
	public static boolean publish(@Nullable final NfcManager nfcManager, final Activity activity, @Nonnull final NdefRecord record)
	{
		if (nfcManager == null)
			return false;

		final NfcAdapter adapter = nfcManager.getDefaultAdapter();
		if (adapter == null)
			return false;

		final NdefMessage message = new NdefMessage(new NdefRecord[] { record });
		adapter.enableForegroundNdefPush(activity, message);

		return true;
	}

	public static void unpublish(@Nullable final NfcManager nfcManager, final Activity activity)
	{
		if (nfcManager == null)
			return;

		final NfcAdapter adapter = nfcManager.getDefaultAdapter();
		if (adapter == null)
			return;

		adapter.disableForegroundNdefPush(activity);
	}

	public static NdefRecord createMime(@Nonnull final String mimeType, @Nonnull final byte[] payload)
	{
		final byte[] mimeBytes = mimeType.getBytes(Charsets.US_ASCII);
		final NdefRecord mimeRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
		return mimeRecord;
	}

	@CheckForNull
	public static byte[] extractMimePayload(@Nonnull final String mimeType, @Nonnull final NdefMessage message)
	{
		final byte[] mimeBytes = mimeType.getBytes(Charsets.US_ASCII);

		for (final NdefRecord record : message.getRecords())
		{
			if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(record.getType(), mimeBytes))
				return record.getPayload();
		}

		return null;
	}
}
