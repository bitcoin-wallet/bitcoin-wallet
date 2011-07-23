/*
 * Copyright 2010 the original author or authors.
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

package de.schildbach.wallet;

import java.io.UnsupportedEncodingException;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;

/**
 * @author Andreas Schildbach
 */
public class NfcTools
{
	public static boolean publishUri(final Object nfcManager, final Activity activity, final String uri)
	{
		final NfcAdapter adapter = ((NfcManager) nfcManager).getDefaultAdapter();
		if (adapter == null)
			return false;

		adapter.enableForegroundNdefPush(activity, ndefMessage(uri));
		return true;
	}

	private static NdefMessage ndefMessage(final String uri)
	{
		try
		{
			final byte[] data = uri.getBytes("UTF-8");
			final NdefRecord record = new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, NdefRecord.RTD_URI, new byte[0], data);
			return new NdefMessage(new NdefRecord[] { record });
		}
		catch (final UnsupportedEncodingException x)
		{
			throw new RuntimeException(x);
		}
	}
}
