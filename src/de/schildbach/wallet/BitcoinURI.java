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
	private Address address;
	private BigInteger amount;

	private static final Pattern P_AMOUNT = Pattern.compile("([\\d.]+)(?:X(\\d+))?");

	public BitcoinURI(final String uri)
	{
		this(Uri.parse(uri));
	}

	public BitcoinURI(final Uri uri)
	{
		if ("bitcoin".equals(uri.getScheme()))
		{
			final Uri u = Uri.parse("bitcoin://" + uri.getSchemeSpecificPart());

			final String addressStr = u.getHost();
			try
			{
				address = new Address(Constants.NETWORK_PARAMETERS, addressStr);
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
