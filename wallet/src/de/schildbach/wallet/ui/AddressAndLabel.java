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

/**
 * @author Andreas Schildbach
 */
public class AddressAndLabel implements Parcelable
{
	public final Address address;
	public final String label;

	public AddressAndLabel(final NetworkParameters addressParams, final String address, @Nullable final String label) throws WrongNetworkException,
			AddressFormatException
	{
		this.address = new Address(addressParams, address);
		this.label = label;
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags)
	{
		dest.writeSerializable(address.getParameters());
		dest.writeByteArray(address.getHash160());

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
		final NetworkParameters addressParameters = (NetworkParameters) in.readSerializable();
		final byte[] addressHash = new byte[Address.LENGTH];
		in.readByteArray(addressHash);
		address = new Address(addressParameters, addressHash);

		label = in.readString();
	}
}
