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
import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public class Nfc
{
	private static final byte[] RTD_ANDROID_APP = "android.com:pkg".getBytes(Constants.US_ASCII);

	public static boolean publishUri(@Nullable final NfcManager nfcManager, final Activity activity, @Nonnull final String uri)
	{
		if (nfcManager == null)
			return false;

		final NfcAdapter adapter = nfcManager.getDefaultAdapter();
		if (adapter == null)
			return false;

		final NdefRecord uriRecord = wellKnownUriRecord(uri);
		adapter.enableForegroundNdefPush(activity, ndefMessage(uriRecord, true, activity.getPackageName()));

		return true;
	}

	public static boolean publishMimeObject(@Nullable final NfcManager nfcManager, final Activity activity, @Nonnull final String mimeType,
			@Nonnull final byte[] payload, final boolean includeApplicationRecord)
	{
		if (nfcManager == null)
			return false;

		final NfcAdapter adapter = nfcManager.getDefaultAdapter();
		if (adapter == null)
			return false;

		final NdefRecord mimeRecord = mimeRecord(mimeType, payload);
		adapter.enableForegroundNdefPush(activity, ndefMessage(mimeRecord, includeApplicationRecord, activity.getPackageName()));

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

	private static NdefMessage ndefMessage(@Nonnull final NdefRecord record, final boolean includeApplicationRecord, final String packageName)
	{
		if (includeApplicationRecord)
		{
			final NdefRecord appRecord = androidApplicationRecord(packageName);
			return new NdefMessage(new NdefRecord[] { record, appRecord });
		}
		else
		{
			return new NdefMessage(new NdefRecord[] { record });
		}
	}

	private static NdefRecord absoluteUriRecord(@Nonnull final String uri)
	{
		return new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, NdefRecord.RTD_URI, new byte[0], uri.getBytes(Constants.UTF_8));
	}

	private static NdefRecord wellKnownUriRecord(@Nonnull final String uri)
	{
		final byte[] uriBytes = uri.getBytes(Constants.UTF_8);
		final byte[] recordBytes = new byte[uriBytes.length + 1];
		recordBytes[0] = (byte) 0x0; // prefix, alway 0 for bitcoin scheme
		System.arraycopy(uriBytes, 0, recordBytes, 1, uriBytes.length);
		return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_URI, new byte[0], recordBytes);
	}

	private static NdefRecord mimeRecord(@Nonnull final String mimeType, @Nonnull final byte[] payload)
	{
		final byte[] mimeBytes = mimeType.getBytes(Constants.US_ASCII);
		final NdefRecord mimeRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
		return mimeRecord;
	}

	private static NdefRecord androidApplicationRecord(@Nonnull final String packageName)
	{
		return new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, RTD_ANDROID_APP, new byte[0], packageName.getBytes(Constants.US_ASCII));
	}

	@CheckForNull
	public static byte[] extractMimePayload(@Nonnull final String mimeType, @Nonnull final NdefMessage message)
	{
		final byte[] mimeBytes = mimeType.getBytes(Constants.US_ASCII);

		for (final NdefRecord record : message.getRecords())
		{
			if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(record.getType(), mimeBytes))
				return record.getPayload();
		}

		return null;
	}
}
