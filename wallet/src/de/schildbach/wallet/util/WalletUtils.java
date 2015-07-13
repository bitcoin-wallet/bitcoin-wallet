/*
 * Copyright 2011-2015 the original author or authors.
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
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.store.WalletProtobufSerializer;
import org.bitcoinj.wallet.KeyChainGroup;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.TypefaceSpan;

import com.google.common.base.Charsets;

import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class WalletUtils
{
	public static Editable formatAddress(final Address address, final int groupSize, final int lineSize)
	{
		return formatHash(address.toString(), groupSize, lineSize);
	}

	public static Editable formatAddress(@Nullable final String prefix, final Address address, final int groupSize, final int lineSize)
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

	public static Editable formatHash(@Nullable final String prefix, final String address, final int groupSize, final int lineSize,
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

	@Nullable
	public static Address getToAddressOfSent(final Transaction tx, final Wallet wallet)
	{
		for (final TransactionOutput output : tx.getOutputs())
		{
			try
			{
				if (!output.isMine(wallet))
				{
					final Script script = output.getScriptPubKey();
					return script.getToAddress(Constants.NETWORK_PARAMETERS, true);
				}
			}
			catch (final ScriptException x)
			{
				// swallow
			}
		}

		return null;
	}

	@Nullable
	public static Address getWalletAddressOfReceived(final Transaction tx, final Wallet wallet)
	{
		for (final TransactionOutput output : tx.getOutputs())
		{
			try
			{
				if (output.isMine(wallet))
				{
					final Script script = output.getScriptPubKey();
					return script.getToAddress(Constants.NETWORK_PARAMETERS, true);
				}
			}
			catch (final ScriptException x)
			{
				// swallow
			}
		}

		return null;
	}

	public static Wallet restoreWalletFromProtobufOrBase58(final InputStream is) throws IOException
	{
		is.mark((int) Constants.BACKUP_MAX_CHARS);

		try
		{
			return restoreWalletFromProtobuf(is);
		}
		catch (final IOException x)
		{
			try
			{
				is.reset();
				return restorePrivateKeysFromBase58(is);
			}
			catch (final IOException x2)
			{
				throw new IOException("cannot read protobuf (" + x.getMessage() + ") or base58 (" + x2.getMessage() + ")", x);
			}
		}
	}

	public static Wallet restoreWalletFromProtobuf(final InputStream is) throws IOException
	{
		try
		{
			final Wallet wallet = new WalletProtobufSerializer().readWallet(is);

			if (!wallet.getParams().equals(Constants.NETWORK_PARAMETERS))
				throw new IOException("bad wallet network parameters: " + wallet.getParams().getId());

			return wallet;
		}
		catch (final UnreadableWalletException x)
		{
			throw new IOException("unreadable wallet", x);
		}
	}

	public static Wallet restorePrivateKeysFromBase58(final InputStream is) throws IOException
	{
		final BufferedReader keyReader = new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));

		// create non-HD wallet
		final KeyChainGroup group = new KeyChainGroup(Constants.NETWORK_PARAMETERS);
		group.importKeys(WalletUtils.readKeys(keyReader));
		return new Wallet(Constants.NETWORK_PARAMETERS, group);
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

	public static byte[] walletToByteArray(final Wallet wallet)
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

	public static Wallet walletFromByteArray(final byte[] walletBytes)
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

	public static Address newAddressOrThrow(final NetworkParameters params, final String base58) throws IllegalArgumentException
	{
		try
		{
			return new Address(params, base58);
		}
		catch (AddressFormatException x)
		{
			throw new IllegalArgumentException(x);
		}
	}

	public static boolean isPayToManyTransaction(final Transaction transaction)
	{
		return transaction.getOutputs().size() > 20;
	}
}
