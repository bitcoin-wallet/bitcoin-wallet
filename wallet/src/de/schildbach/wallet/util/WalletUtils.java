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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.common.base.Charsets;

import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class WalletUtils
{
	public static Editable formatAddress(@Nonnull final Address address, final int groupSize, final int lineSize)
	{
		return formatHash(address.toString(), groupSize, lineSize);
	}

	public static Editable formatAddress(@Nullable final String prefix, @Nonnull final Address address, final int groupSize, final int lineSize)
	{
		return formatHash(prefix, address.toString(), groupSize, lineSize, Constants.CHAR_THIN_SPACE);
	}

	public static Editable formatHash(@Nonnull final String address, final int groupSize, final int lineSize)
	{
		return formatHash(null, address, groupSize, lineSize, Constants.CHAR_THIN_SPACE);
	}

	public static long longHash(@Nonnull final Sha256Hash hash)
	{
		final byte[] bytes = hash.getBytes();

		return (bytes[31] & 0xFFl) | ((bytes[30] & 0xFFl) << 8) | ((bytes[29] & 0xFFl) << 16) | ((bytes[28] & 0xFFl) << 24)
				| ((bytes[27] & 0xFFl) << 32) | ((bytes[26] & 0xFFl) << 40) | ((bytes[25] & 0xFFl) << 48) | ((bytes[23] & 0xFFl) << 56);
	}

	public static Editable formatHash(@Nullable final String prefix, @Nonnull final String address, final int groupSize, final int lineSize,
			final char groupSeparator)
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

	public static void formatSignificant(@Nonnull final Spannable spannable, @Nullable final RelativeSizeSpan insignificantRelativeSizeSpan)
	{
		spannable.removeSpan(SIGNIFICANT_SPAN);
		if (insignificantRelativeSizeSpan != null)
			spannable.removeSpan(insignificantRelativeSizeSpan);

		final Matcher m = P_SIGNIFICANT.matcher(spannable);
		if (m.find())
		{
			final int pivot = m.group().length();
			if (pivot > 0)
				spannable.setSpan(SIGNIFICANT_SPAN, 0, pivot, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (spannable.length() > pivot && insignificantRelativeSizeSpan != null)
				spannable.setSpan(insignificantRelativeSizeSpan, pivot, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}

	public static BigInteger localValue(@Nonnull final BigInteger btcValue, @Nonnull final BigInteger rate)
	{
		return btcValue.multiply(rate).divide(GenericUtils.ONE_BTC);
	}

	public static BigInteger btcValue(@Nonnull final BigInteger localValue, @Nonnull final BigInteger rate)
	{
		return localValue.multiply(GenericUtils.ONE_BTC).divide(rate);
	}

	@CheckForNull
	public static Address getFirstFromAddress(@Nonnull final Transaction tx)
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

	@CheckForNull
	public static Address getFirstToAddress(@Nonnull final Transaction tx)
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

	public static boolean isInternal(@Nonnull final Transaction tx)
	{
		if (tx.isCoinBase())
			return false;

		final List<TransactionOutput> outputs = tx.getOutputs();
		if (outputs.size() != 1)
			return false;

		try
		{
			final TransactionOutput output = outputs.get(0);
			final Script scriptPubKey = output.getScriptPubKey();
			if (!scriptPubKey.isSentToRawPubKey())
				return false;

			return true;
		}
		catch (final ScriptException x)
		{
			return false;
		}
	}

	public static void writeKeys(@Nonnull final Writer out, @Nonnull final List<ECKey> keys) throws IOException
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

	public static List<ECKey> readKeys(@Nonnull final BufferedReader in) throws IOException
	{
		try
		{
			final DateFormat format = Iso8601Format.newDateTimeFormatT();

			final List<ECKey> keys = new LinkedList<ECKey>();

			long charCount = 0;
			while (true)
			{
				final String line = in.readLine();
				if (line == null)
					break; // eof
				charCount += line.length();
				if (charCount > Constants.BACKUP_MAX_CHARS)
					throw new IOException("read more than the limit of " + Constants.BACKUP_MAX_CHARS + " characters");
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
		@Override
		public boolean accept(final File file)
		{
			BufferedReader reader = null;

			try
			{
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
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
						// swallow
					}
				}
			}
		}
	};

	public static final FileFilter BACKUP_FILE_FILTER = new FileFilter()
	{
		@Override
		public boolean accept(final File file)
		{
			InputStream is = null;

			try
			{
				is = new FileInputStream(file);
				return WalletProtobufSerializer.isWallet(is);
			}
			catch (final IOException x)
			{
				return false;
			}
			finally
			{
				if (is != null)
				{
					try
					{
						is.close();
					}
					catch (final IOException x)
					{
						// swallow
					}
				}
			}
		}
	};

	@CheckForNull
	public static ECKey pickOldestKey(@Nonnull final Wallet wallet)
	{
		ECKey oldestKey = null;

		for (final ECKey key : wallet.getImportedKeys())
			if (!wallet.isKeyRotating(key))
				if (oldestKey == null || key.getCreationTimeSeconds() < oldestKey.getCreationTimeSeconds())
					oldestKey = key;

		return oldestKey;
	}

	public static byte[] walletToByteArray(@Nonnull final Wallet wallet)
	{
		try
		{
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			new WalletProtobufSerializer().writeWallet(wallet, os);
			os.close();
			return os.toByteArray();
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	public static Wallet walletFromByteArray(@Nonnull final byte[] walletBytes)
	{
		try
		{
			final ByteArrayInputStream is = new ByteArrayInputStream(walletBytes);
			final Wallet wallet = new WalletProtobufSerializer().readWallet(is);
			is.close();
			return wallet;
		}
		catch (final UnreadableWalletException x)
		{
			throw new RuntimeException(x);
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}
}
