/*
 * Copyright 2013-2015 the original author or authors.
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

package de.schildbach.wallet.ui;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.WrongNetworkException;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Objects;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WalletUtils;

/**
 * @author Andreas Schildbach
 */
public class AddressAndLabel implements Parcelable
{
	public final Address address;
	public final String label;

	public AddressAndLabel(final Address address, @Nullable final String label)
	{
		this.address = address;
		this.label = label;
	}

	public AddressAndLabel(final NetworkParameters addressParams, final String address, @Nullable final String label) throws WrongNetworkException,
			AddressFormatException
	{
		this(Address.fromBase58(addressParams, address), label);
	}

	@Override
	public boolean equals(final Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		final AddressAndLabel other = (AddressAndLabel) o;
		return Objects.equal(this.address, other.address) && Objects.equal(this.label, other.label);
	}

	@Override
	public int hashCode()
	{
		return Objects.hashCode(address, label);
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags)
	{
		dest.writeString(address.toBase58());
		dest.writeString(label);
	}

	public static final Parcelable.Creator<AddressAndLabel> CREATOR = new Parcelable.Creator<AddressAndLabel>()
	{
		@Override
		public AddressAndLabel createFromParcel(final Parcel in)
		{
			return new AddressAndLabel(in);
		}

		@Override
		public AddressAndLabel[] newArray(final int size)
		{
			return new AddressAndLabel[size];
		}
	};

	private AddressAndLabel(final Parcel in)
	{
		address = Address.fromBase58(Constants.NETWORK_PARAMETERS, in.readString());
		label = in.readString();
	}
}
