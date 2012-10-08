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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
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

	public static Editable formatAddress(final String prefix, final Address address, final int groupSize, final int lineSize)
	{
		return formatAddress(prefix, address.toString(), groupSize, lineSize);
	}

	public static Editable formatAddress(final String address, final int groupSize, final int lineSize)
	{
		return formatAddress(null, address, groupSize, lineSize);
	}

	public static Editable formatAddress(final String prefix, final String address, final int groupSize, final int lineSize)
	{
		final SpannableStringBuilder builder = prefix != null ? new SpannableStringBuilder(prefix) : new SpannableStringBuilder();

		final int len = address.length();
		for (int i = 0; i < len; i += groupSize)
		{
			final int end = i + groupSize;
			final String part = address.substring(i, end < len ? end : len);

			builder.append(part);
			builder.setSpan(new TypefaceSpan("monospace"), builder.length() - part.length(), builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (end < len)
			{
				final boolean endOfLine = lineSize > 0 && end % lineSize == 0;
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

	private static final Pattern P_SIGNIFICANT = Pattern.compile("^([-+]" + Constants.THIN_SPACE + ")?\\d*(\\.\\d{0,2})?");
	private static Object SIGNIFICANT_SPAN = new StyleSpan(Typeface.BOLD);
	private static Object UNSIGNIFICANT_SPAN = new RelativeSizeSpan(0.85f);

	public static void formatValue(final Editable s)
	{
		s.removeSpan(SIGNIFICANT_SPAN);
		s.removeSpan(UNSIGNIFICANT_SPAN);

		final Matcher m = P_SIGNIFICANT.matcher(s);
		if (m.find())
		{
			final int pivot = m.group().length();
			s.setSpan(SIGNIFICANT_SPAN, 0, pivot, 0);
			if (s.length() > pivot)
				s.setSpan(UNSIGNIFICANT_SPAN, pivot, s.length(), 0);
		}
	}

	public static Address getFromAddress(final Transaction tx)
	{
		try
		{
			for (final TransactionInput input : tx.getInputs())
			{
				return input.getFromAddress();
			}

			throw new IllegalStateException();
		}
		catch (final ScriptException x)
		{
			// this will happen on inputs connected to coinbase transactions
			return null;
		}
	}

	public static Address getToAddress(final Transaction tx)
	{
		try
		{
			for (final TransactionOutput output : tx.getOutputs())
			{
				return output.getScriptPubKey().getToAddress();
			}

			throw new IllegalStateException();
		}
		catch (final ScriptException x)
		{
			return null;
		}
	}

	public static void writeKeys(final Writer out, final List<ECKey> keys) throws IOException
	{
		final DateFormat format = Iso8601Format.newDateTimeFormatT();

		for (final ECKey key : keys)
		{
			out.write(key.getPrivateKeyEncoded(Constants.NETWORK_PARAMETERS).toString());
			if (key.getCreationTimeSeconds() != 0)
			{
				out.write(' ');
				out.write(format.format(new Date(key.getCreationTimeSeconds() * 1000)));
			}
			out.write('\n');
		}
	}

	public static List<ECKey> readKeys(final BufferedReader in) throws IOException
	{
		try
		{
			final DateFormat format = Iso8601Format.newDateTimeFormatT();

			final List<ECKey> keys = new LinkedList<ECKey>();

			while (true)
			{
				final String line = in.readLine();
				if (line == null)
					break; // eof
				if (line.length() == 0 || line.charAt(0) == '#')
					continue; // skip comment

				final String[] parts = line.split(" ");

				final ECKey key = new DumpedPrivateKey(Constants.NETWORK_PARAMETERS, parts[0]).getKey();
				key.setCreationTimeSeconds(parts.length >= 2 ? format.parse(parts[1]).getTime() / 1000 : 0);

				keys.add(key);
			}

			return keys;
		}
		catch (final AddressFormatException x)
		{
			throw new IOException(x);
		}
		catch (final ParseException x)
		{
			throw new IOException(x);
		}
	}
}
