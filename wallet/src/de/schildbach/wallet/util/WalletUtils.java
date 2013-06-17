/*
 * Copyright 2011-2013 the original author or authors.
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
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
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
import android.text.format.DateUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Sha256Hash;
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
			hints.put(EncodeHintType.MARGIN, 0);
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
		return formatHash(address.toString(), groupSize, lineSize);
	}

	public static Editable formatAddress(final String prefix, final Address address, final int groupSize, final int lineSize)
	{
		return formatHash(prefix, address.toString(), groupSize, lineSize, Constants.CHAR_THIN_SPACE);
	}

	public static Editable formatHash(final String address, final int groupSize, final int lineSize)
	{
		return formatHash(null, address, groupSize, lineSize, Constants.CHAR_THIN_SPACE);
	}

	public static long longHash(final Sha256Hash hash)
	{
		final byte[] bytes = hash.getBytes();

		return (bytes[31] & 0xFFl) | ((bytes[30] & 0xFFl) << 8) | ((bytes[29] & 0xFFl) << 16) | ((bytes[28] & 0xFFl) << 24)
				| ((bytes[27] & 0xFFl) << 32) | ((bytes[26] & 0xFFl) << 40) | ((bytes[25] & 0xFFl) << 48) | ((bytes[23] & 0xFFl) << 56);
	}

	public static Editable formatHash(final String prefix, final String address, final int groupSize, final int lineSize, final char groupSeparator)
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
				builder.append(endOfLine ? '\n' : groupSeparator);
			}
		}

		return builder;
	}

	private static final Pattern P_SIGNIFICANT = Pattern.compile("^([-+]" + Constants.CHAR_THIN_SPACE + ")?\\d*(\\.\\d{0,2})?");
	private static final Object SIGNIFICANT_SPAN = new StyleSpan(Typeface.BOLD);
	public static final RelativeSizeSpan SMALLER_SPAN = new RelativeSizeSpan(0.85f);

	public static void formatSignificant(final Editable s, final RelativeSizeSpan insignificantRelativeSizeSpan)
	{
		s.removeSpan(SIGNIFICANT_SPAN);
		if (insignificantRelativeSizeSpan != null)
			s.removeSpan(insignificantRelativeSizeSpan);

		final Matcher m = P_SIGNIFICANT.matcher(s);
		if (m.find())
		{
			final int pivot = m.group().length();
			s.setSpan(SIGNIFICANT_SPAN, 0, pivot, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (s.length() > pivot && insignificantRelativeSizeSpan != null)
				s.setSpan(insignificantRelativeSizeSpan, pivot, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}

	public static BigInteger localValue(final BigInteger btcValue, final BigInteger rate)
	{
		return btcValue.multiply(rate).divide(Utils.COIN);
	}

	public static BigInteger btcValue(final BigInteger localValue, final BigInteger rate)
	{
		return localValue.multiply(Utils.COIN).divide(rate);
	}

	public static Address getFromAddress(final Transaction tx)
	{
		if (tx.isCoinBase())
			return null;

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
				return output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
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

		out.write("# KEEP YOUR PRIVATE KEYS SAFE! Anyone who can read this can spend your Bitcoins.\n");

		for (final ECKey key : keys)
		{
			out.write(key.getPrivateKeyEncoded(Constants.NETWORK_PARAMETERS).toString());
			if (key.getCreationTimeSeconds() != 0)
			{
				out.write(' ');
				out.write(format.format(new Date(key.getCreationTimeSeconds() * DateUtils.SECOND_IN_MILLIS)));
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
				if (line.trim().isEmpty() || line.charAt(0) == '#')
					continue; // skip comment

				final String[] parts = line.split(" ");

				final ECKey key = new DumpedPrivateKey(Constants.NETWORK_PARAMETERS, parts[0]).getKey();
				key.setCreationTimeSeconds(parts.length >= 2 ? format.parse(parts[1]).getTime() / DateUtils.SECOND_IN_MILLIS : 0);

				keys.add(key);
			}

			return keys;
		}
		catch (final AddressFormatException x)
		{
			throw new IOException("cannot read keys", x);
		}
		catch (final ParseException x)
		{
			throw new IOException("cannot read keys", x);
		}
	}

	public static final FileFilter KEYS_FILE_FILTER = new FileFilter()
	{
		public boolean accept(final File file)
		{
			BufferedReader reader = null;

			try
			{
				reader = new BufferedReader(new FileReader(file));
				WalletUtils.readKeys(reader);

				return true;
			}
			catch (final IOException x)
			{
				return false;
			}
			finally
			{
				if (reader != null)
				{
					try
					{
						reader.close();
					}
					catch (final IOException x)
					{
						x.printStackTrace();
					}
				}
			}
		}
	};

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void chmod(final File path, final int mode)
	{
		try
		{
			final Class fileUtils = Class.forName("android.os.FileUtils");
			final Method setPermissions = fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
			setPermissions.invoke(null, path.getAbsolutePath(), mode, -1, -1);
		}
		catch (final Exception x)
		{
			x.printStackTrace();
		}
	}
}
