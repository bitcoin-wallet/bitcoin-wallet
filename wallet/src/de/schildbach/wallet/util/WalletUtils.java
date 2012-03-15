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

import java.math.BigInteger;
import java.util.Hashtable;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Utils;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class WalletUtils
{
	public final static QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();

	public static Bitmap getQRCodeBitmap(final String url, final int size)
	{
		try
		{
			final Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
			final BitMatrix result = QR_CODE_WRITER.encode(url, BarcodeFormat.QR_CODE, size, size, hints);

			final int width = result.getWidth();
			final int height = result.getHeight();
			final int[] pixels = new int[width * height];

			for (int y = 0; y < height; y++)
			{
				final int offset = y * width;
				for (int x = 0; x < width; x++)
				{
					pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
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

	public static Editable formatAddress(final Address address, final int groupSize, final int lineSize)
	{
		return formatAddress(address.toString(), groupSize, lineSize);
	}

	public static Editable formatAddress(final String address, final int groupSize, final int lineSize)
	{
		final SpannableStringBuilder builder = new SpannableStringBuilder();

		final int len = address.length();
		for (int i = 0; i < len; i += groupSize)
		{
			final int end = i + groupSize;
			final String part = address.substring(i, end < len ? end : len);

			builder.append(part);
			builder.setSpan(new TypefaceSpan("monospace"), builder.length() - part.length(), builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (end < len)
			{
				final boolean endOfLine = end % lineSize == 0;
				builder.append(endOfLine ? "\n" : Constants.THIN_SPACE);
			}
		}

		return builder;
	}

	public static String formatValue(final BigInteger value)
	{
		return formatValue(value, "", "-");
	}

	public static String formatValue(final BigInteger value, final String plusSign, final String minusSign)
	{
		final boolean negative = value.compareTo(BigInteger.ZERO) < 0;
		final BigInteger absValue = value.abs();

		final String sign = negative ? minusSign : plusSign;

		final int coins = absValue.divide(Utils.COIN).intValue();
		final int cents = absValue.remainder(Utils.COIN).intValue();

		if (cents % 1000000 == 0)
			return String.format("%s%d.%02d", sign, coins, cents / 1000000);
		else if (cents % 10000 == 0)
			return String.format("%s%d.%04d", sign, coins, cents / 10000);
		else
			return String.format("%s%d.%08d", sign, coins, cents);
	}
}
