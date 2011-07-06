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

import java.util.Hashtable;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * @author Andreas Schildbach
 */
public class WalletUtils
{
	public static String splitIntoLines(final String str, final int lines)
	{
		if (lines < 2)
			return str;

		final int len = (int) Math.ceil((float) str.length() / lines);
		final StringBuilder builder = new StringBuilder(str);
		for (int i = 0; i < lines - 1; i++)
			builder.insert(len + i * (len + 1), '\n');

		return builder.toString();
	}

	public final static QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();

	public static Bitmap getQRCodeBitmap(final String url)
	{
		final int SIZE = 256;

		try
		{
			final Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
			final BitMatrix result = QR_CODE_WRITER.encode(url, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);

			final int width = result.getWidth();
			final int height = result.getHeight();
			final int[] pixels = new int[width * height];

			for (int y = 0; y < height; y++)
			{
				final int offset = y * width;
				for (int x = 0; x < width; x++)
				{
					pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
				}
			}

			final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
			return bitmap;
		}
		catch (final WriterException x)
		{
			x.printStackTrace();
			return null;
		}
	}
}
