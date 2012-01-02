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

package de.schildbach.wallet;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Utils;

/**
 * @author Andreas Schildbach
 */
public class BitcoinURI
{
	public static class ParseException extends Exception
	{
		final String uri;

		public ParseException(final String message, final String uri)
		{
			super(message + ": '" + uri + "'");

			this.uri = uri;
		}
	}

	private Address address;
	private BigInteger amount;

	private static final Pattern P_AMOUNT = Pattern.compile("([\\d.]+)(?:X(\\d+))?");

	public BitcoinURI(final String uri) throws ParseException
	{
		this(Uri.parse(uri));
	}

	public BitcoinURI(final Uri uri) throws ParseException
	{
		final String scheme = uri.getScheme();

		if ("bitcoin".equals(scheme))
		{
			final Uri u = Uri.parse("bitcoin://" + uri.getSchemeSpecificPart());

			try
			{
				if (u.getHost().length() > 0)
					address = new Address(Constants.NETWORK_PARAMETERS, u.getHost());
			}
			catch (final AddressFormatException x)
			{
				x.printStackTrace();
			}

			final String amountStr = u.getQueryParameter("amount");
			if (amountStr != null)
			{
				final Matcher m = P_AMOUNT.matcher(amountStr);
				if (m.matches())
				{
					amount = Utils.toNanoCoins(m.group(1));
					if (m.group(2) != null)
						amount.multiply(BigInteger.valueOf(10).pow(Integer.parseInt(m.group(2)) - 8));
				}
			}
		}
		else
		{
			throw new ParseException("unknown scheme", uri.toString());
		}
	}

	public Address getAddress()
	{
		return address;
	}

	public BigInteger getAmount()
	{
		return amount;
	}

	@Override
	public String toString()
	{
		return "BitcoinURI[" + address + "," + amount + "]";
	}
}
