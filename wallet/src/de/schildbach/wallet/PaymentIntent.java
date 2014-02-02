/*
 * Copyright 2014 the original author or authors.
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
import java.util.Arrays;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.WrongNetworkException;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.GenericUtils;

/**
 * @author Andreas Schildbach
 */
public final class PaymentIntent implements Parcelable
{
	public enum Standard
	{
		BIP21, BIP70
	}

	@CheckForNull
	public final Standard standard;

	@CheckForNull
	public final String payeeName;

	@CheckForNull
	public final String payeeOrganization;

	@CheckForNull
	public final String payeeVerifiedBy;

	@CheckForNull
	public final BigInteger amount;

	@CheckForNull
	private final Address address;

	@CheckForNull
	public final String memo;

	@CheckForNull
	public final String paymentUrl;

	@CheckForNull
	public final byte[] payeeData;

	public PaymentIntent(@Nullable final Standard standard, @Nullable final String payeeName, @Nullable final String payeeOrganization,
			@Nullable final String payeeVerifiedBy, @Nonnull final Address address, @Nullable final String memo, @Nullable final BigInteger amount,
			@Nullable final String paymentUrl, @Nullable final byte[] payeeData)
	{
		this.standard = standard;
		this.payeeName = payeeName;
		this.payeeOrganization = payeeOrganization;
		this.payeeVerifiedBy = payeeVerifiedBy;
		this.amount = amount;
		this.address = address;
		this.memo = memo;
		this.paymentUrl = paymentUrl;
		this.payeeData = payeeData;
	}

	private PaymentIntent(@Nonnull final Address address, @Nullable final String addressLabel)
	{
		this(null, null, null, null, address, addressLabel, null, null, null);
	}

	public static PaymentIntent blank()
	{
		return new PaymentIntent(null, null, null, null, null, null, null, null, null);
	}

	public static PaymentIntent fromAddress(@Nonnull final Address address, @Nullable final String addressLabel)
	{
		return new PaymentIntent(address, addressLabel);
	}

	public static PaymentIntent fromAddress(@Nonnull final String address, @Nullable final String addressLabel) throws WrongNetworkException,
			AddressFormatException
	{
		return new PaymentIntent(new Address(Constants.NETWORK_PARAMETERS, address), addressLabel);
	}

	public static PaymentIntent fromBitcoinUri(@Nonnull final BitcoinURI bitcoinUri)
	{
		final String bluetoothMac = (String) bitcoinUri.getParameterByName(Bluetooth.MAC_URI_PARAM);

		return new PaymentIntent(PaymentIntent.Standard.BIP21, null, null, null, bitcoinUri.getAddress(), bitcoinUri.getLabel(),
				bitcoinUri.getAmount(), bluetoothMac != null ? "bt:" + bluetoothMac : null, null);
	}

	public boolean hasPayee()
	{
		return payeeName != null;
	}

	public boolean hasAddress()
	{
		return address != null;
	}

	public Address getAddress()
	{
		return address;
	}

	public boolean hasAmount()
	{
		return amount != null;
	}

	public boolean hasPaymentUrl()
	{
		return paymentUrl != null;
	}

	public boolean isSupportedPaymentUrl()
	{
		return isHttpPaymentUrl() || isBluetoothPaymentUrl();
	}

	public boolean isHttpPaymentUrl()
	{
		return paymentUrl != null
				&& (GenericUtils.startsWithIgnoreCase(paymentUrl, "http:") || GenericUtils.startsWithIgnoreCase(paymentUrl, "https:"));
	}

	public boolean isBluetoothPaymentUrl()
	{
		return paymentUrl != null && GenericUtils.startsWithIgnoreCase(paymentUrl, "bt:");
	}

	public String getBluetoothMac()
	{
		if (isBluetoothPaymentUrl())
			return paymentUrl.substring(3);
		else
			throw new IllegalStateException();
	}

	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder();

		builder.append(getClass().getSimpleName());
		builder.append('[');
		builder.append(standard);
		builder.append(',');
		if (hasPayee())
		{
			builder.append(payeeName);
			if (payeeOrganization != null)
				builder.append("/").append(payeeOrganization);
			if (payeeVerifiedBy != null)
				builder.append("/").append(payeeVerifiedBy);
			builder.append(',');
		}
		builder.append(address != null ? address.toString() : "null");
		builder.append(',');
		builder.append(amount != null ? GenericUtils.formatValue(amount, Constants.BTC_MAX_PRECISION, 0) : "null");
		builder.append(',');
		builder.append(paymentUrl);
		if (payeeData != null)
		{
			builder.append(',');
			builder.append(Arrays.toString(payeeData));
		}
		builder.append(']');

		return builder.toString();
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags)
	{
		dest.writeSerializable(standard);

		dest.writeString(payeeName);
		dest.writeString(payeeOrganization);
		dest.writeString(payeeVerifiedBy);

		dest.writeSerializable(amount);

		if (address != null)
		{
			dest.writeInt(1);
			dest.writeSerializable(address.getParameters());
			dest.writeByteArray(address.getHash160());
		}
		else
		{
			dest.writeInt(0);
		}

		dest.writeString(memo);

		dest.writeString(paymentUrl);

		if (payeeData != null)
		{
			dest.writeInt(payeeData.length);
			dest.writeByteArray(payeeData);
		}
		else
		{
			dest.writeInt(0);
		}
	}

	public static final Parcelable.Creator<PaymentIntent> CREATOR = new Parcelable.Creator<PaymentIntent>()
	{
		@Override
		public PaymentIntent createFromParcel(final Parcel in)
		{
			return new PaymentIntent(in);
		}

		@Override
		public PaymentIntent[] newArray(final int size)
		{
			return new PaymentIntent[size];
		}
	};

	private PaymentIntent(final Parcel in)
	{
		standard = (Standard) in.readSerializable();

		payeeName = in.readString();
		payeeOrganization = in.readString();
		payeeVerifiedBy = in.readString();

		amount = (BigInteger) in.readSerializable();

		if (in.readInt() != 0)
		{
			final NetworkParameters addressParameters = (NetworkParameters) in.readSerializable();
			final byte[] addressHash = new byte[Address.LENGTH];
			in.readByteArray(addressHash);
			address = new Address(addressParameters, addressHash);
		}
		else
		{
			address = null;
		}

		memo = in.readString();

		paymentUrl = in.readString();

		final int payeeDataLength = in.readInt();
		if (payeeDataLength > 0)
		{
			payeeData = new byte[payeeDataLength];
			in.readByteArray(payeeData);
		}
		else
		{
			payeeData = null;
		}
	}
}
