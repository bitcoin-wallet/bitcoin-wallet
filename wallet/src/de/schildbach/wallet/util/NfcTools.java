/*
 * Copyright 2011-2012 the original author or authors.
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

import java.nio.charset.Charset;
import java.util.Arrays;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class NfcTools
{
	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final Charset US_ASCII = Charset.forName("US-ASCII");
	private static final byte[] RTD_ANDROID_APP = "android.com:pkg".getBytes(US_ASCII);

	public static boolean publishUri(final Object nfcManager, final Activity activity, final String uri)
	{
		final NfcAdapter adapter = ((NfcManager) nfcManager).getDefaultAdapter();
		if (adapter == null)
			return false;

		final NdefRecord uriRecord = wellKnownUriRecord(uri);
		adapter.enableForegroundNdefPush(activity, ndefMessage(uriRecord, true));

		return true;
	}

	public static boolean publishMimeObject(final Object nfcManager, final Activity activity, final String mimeType, final byte[] payload,
			final boolean includeApplicationRecord)
	{
		final NfcAdapter adapter = ((NfcManager) nfcManager).getDefaultAdapter();
		if (adapter == null)
			return false;

		final NdefRecord mimeRecord = mimeRecord(mimeType, payload);
		adapter.enableForegroundNdefPush(activity, ndefMessage(mimeRecord, includeApplicationRecord));

		return true;
	}

	public static void unpublish(final Object nfcManager, final Activity activity)
	{
		final NfcAdapter adapter = ((NfcManager) nfcManager).getDefaultAdapter();
		if (adapter == null)
			return;

		adapter.disableForegroundNdefPush(activity);
	}

	private static NdefMessage ndefMessage(final NdefRecord record, final boolean includeApplicationRecord)
	{
		if (includeApplicationRecord)
		{
			final NdefRecord appRecord = androidApplicationRecord(Constants.PACKAGE_NAME);
			return new NdefMessage(new NdefRecord[] { record, appRecord });
		}
		else
		{
			return new NdefMessage(new NdefRecord[] { record });
		}
	}

	private static NdefRecord absoluteUriRecord(final String uri)
	{
		return new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, NdefRecord.RTD_URI, new byte[0], uri.getBytes(UTF_8));
	}

	private static NdefRecord wellKnownUriRecord(final String uri)
	{
		final byte[] uriBytes = uri.getBytes(UTF_8);
		final byte[] recordBytes = new byte[uriBytes.length + 1];
		recordBytes[0] = (byte) 0x0; // prefix, alway 0 for bitcoin scheme
		System.arraycopy(uriBytes, 0, recordBytes, 1, uriBytes.length);
		return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_URI, new byte[0], recordBytes);
	}

	private static NdefRecord mimeRecord(final String mimeType, final byte[] payload)
	{
		final byte[] mimeBytes = mimeType.getBytes(US_ASCII);
		final NdefRecord mimeRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
		return mimeRecord;
	}

	private static NdefRecord androidApplicationRecord(final String packageName)
	{
		return new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, RTD_ANDROID_APP, new byte[0], packageName.getBytes(US_ASCII));
	}

	public static byte[] extractMimePayload(final String mimeType, final Object message)
	{
		byte[] mimeBytes = mimeType.getBytes(US_ASCII);

		final NdefMessage ndefMessage = (NdefMessage) message;
		for (final NdefRecord record : ndefMessage.getRecords())
		{
			if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(record.getType(), mimeBytes))
				return record.getPayload();
		}

		return null;
	}
}
