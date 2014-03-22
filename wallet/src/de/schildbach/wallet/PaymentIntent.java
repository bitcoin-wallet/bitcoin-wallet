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
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.core.WrongNetworkException;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
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

	public final static class Output implements Parcelable
	{
		public final BigInteger amount;
		public final Script script;

		public Output(final BigInteger amount, final Script script)
		{
			this.amount = amount;
			this.script = script;
		}

		public boolean hasAmount()
		{
			return amount != null && amount.signum() != 0;
		}

		@Override
		public String toString()
		{
			final StringBuilder builder = new StringBuilder();

			builder.append(getClass().getSimpleName());
			builder.append('[');
			builder.append(hasAmount() ? GenericUtils.formatDebugValue(amount) : "null");
			builder.append(',');
			if (script.isSentToAddress() || script.isSentToP2SH())
				builder.append(script.getToAddress(Constants.NETWORK_PARAMETERS));
			else if (script.isSentToRawPubKey())
				for (final byte b : script.getPubKey())
					builder.append(String.format("%02x", b));
			else if (script.isSentToMultiSig())
				builder.append("multisig");
			else
				builder.append("unknown");
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
			dest.writeSerializable(amount);

			final byte[] program = script.getProgram();
			dest.writeInt(program.length);
			dest.writeByteArray(program);
		}

		public static final Parcelable.Creator<Output> CREATOR = new Parcelable.Creator<Output>()
		{
			@Override
			public Output createFromParcel(final Parcel in)
			{
				return new Output(in);
			}

			@Override
			public Output[] newArray(final int size)
			{
				return new Output[size];
			}
		};

		private Output(final Parcel in)
		{
			amount = (BigInteger) in.readSerializable();

			final int programLength = in.readInt();
			final byte[] program = new byte[programLength];
			in.readByteArray(program);
			script = new Script(program);
		}
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
	public final Output[] outputs;

	@CheckForNull
	public final String memo;

	@CheckForNull
	public final String paymentUrl;

	@CheckForNull
	public final byte[] payeeData;

	@CheckForNull
	public final String paymentRequestUrl;

	public PaymentIntent(@Nullable final Standard standard, @Nullable final String payeeName, @Nullable final String payeeOrganization,
			@Nullable final String payeeVerifiedBy, @Nullable final Output[] outputs, @Nullable final String memo, @Nullable final String paymentUrl,
			@Nullable final byte[] payeeData, @Nullable final String paymentRequestUrl)
	{
		this.standard = standard;
		this.payeeName = payeeName;
		this.payeeOrganization = payeeOrganization;
		this.payeeVerifiedBy = payeeVerifiedBy;
		this.outputs = outputs;
		this.memo = memo;
		this.paymentUrl = paymentUrl;
		this.payeeData = payeeData;
		this.paymentRequestUrl = paymentRequestUrl;
	}

	private PaymentIntent(@Nonnull final Address address, @Nullable final String addressLabel)
	{
		this(null, null, null, null, buildSimplePayTo(BigInteger.ZERO, address), addressLabel, null, null, null);
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
		final Output[] outputs = buildSimplePayTo(bitcoinUri.getAmount(), bitcoinUri.getAddress());
		final String bluetoothMac = (String) bitcoinUri.getParameterByName(Bluetooth.MAC_URI_PARAM);

		return new PaymentIntent(PaymentIntent.Standard.BIP21, null, null, null, outputs, bitcoinUri.getLabel(), bluetoothMac != null ? "bt:"
				+ bluetoothMac : null, null, bitcoinUri.getPaymentRequestUrl());
	}

	public PaymentIntent mergeWithEditedValues(@Nullable final BigInteger editedAmount, @Nullable final Address editedAddress)
	{
		final Output[] outputs;

		if (hasOutputs())
		{
			if (mayEditAmount())
			{
				// put all coins on first output, skip the others
				outputs = new Output[] { new Output(editedAmount, this.outputs[0].script) };
			}
			else
			{
				// exact copy of outputs
				outputs = this.outputs;
			}
		}
		else
		{
			// custom output
			outputs = buildSimplePayTo(editedAmount, editedAddress);
		}

		return new PaymentIntent(standard, payeeName, payeeOrganization, payeeVerifiedBy, outputs, memo, null, payeeData, null);
	}

	public SendRequest toSendRequest()
	{
		final Transaction transaction = new Transaction(Constants.NETWORK_PARAMETERS);
		for (final PaymentIntent.Output output : outputs)
			transaction.addOutput(output.amount, output.script);
		return SendRequest.forTx(transaction);
	}

	private static Output[] buildSimplePayTo(final BigInteger amount, final Address address)
	{
		return new Output[] { new Output(amount, ScriptBuilder.createOutputScript(address)) };
	}

	public boolean hasPayee()
	{
		return payeeName != null;
	}

	public boolean hasOutputs()
	{
		return outputs != null && outputs.length > 0;
	}

	public boolean hasAddress()
	{
		if (outputs == null || outputs.length != 1)
			return false;

		final Script script = outputs[0].script;
		return script.isSentToAddress() || script.isSentToP2SH() || script.isSentToRawPubKey();
	}

	public Address getAddress()
	{
		if (!hasAddress())
			throw new IllegalStateException();

		final Script script = outputs[0].script;
		if (script.isSentToAddress() || script.isSentToP2SH())
			return script.getToAddress(Constants.NETWORK_PARAMETERS);
		else if (script.isSentToRawPubKey())
			return new Address(Constants.NETWORK_PARAMETERS, script.getPubKeyHash());
		else
			throw new IllegalStateException();
	}

	public boolean mayEditAddress()
	{
		return standard == null;
	}

	public boolean hasAmount()
	{
		if (hasOutputs())
			for (final Output output : outputs)
				if (output.hasAmount())
					return true;

		return false;
	}

	public BigInteger getAmount()
	{
		BigInteger amount = BigInteger.ZERO;

		if (hasOutputs())
			for (final Output output : outputs)
				if (output.hasAmount())
					amount = amount.add(output.amount);

		if (amount.signum() != 0)
			return amount;
		else
			return null;
	}

	public boolean mayEditAmount()
	{
		return !(standard == Standard.BIP70 && hasAmount());
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
		return Bluetooth.isBluetoothUrl(paymentUrl);
	}

	public boolean hasPaymentRequestUrl()
	{
		return paymentRequestUrl != null;
	}

	public boolean isSupportedPaymentRequestUrl()
	{
		return isHttpPaymentRequestUrl() || isBluetoothPaymentRequestUrl();
	}

	public boolean isHttpPaymentRequestUrl()
	{
		return paymentRequestUrl != null
				&& (GenericUtils.startsWithIgnoreCase(paymentRequestUrl, "http:") || GenericUtils.startsWithIgnoreCase(paymentRequestUrl, "https:"));
	}

	public boolean isBluetoothPaymentRequestUrl()
	{
		return Bluetooth.isBluetoothUrl(paymentRequestUrl);
	}

	public boolean isSecurityExtendedBy(final PaymentIntent paymentIntent)
	{
		// check address
		final boolean hasAddress = hasAddress();
		if (hasAddress != paymentIntent.hasAddress())
			return false;
		if (hasAddress && !getAddress().equals(paymentIntent.getAddress()))
			return false;

		// check amount
		final boolean hasAmount = hasAmount();
		if (hasAmount != paymentIntent.hasAmount())
			return false;
		if (hasAmount && !getAmount().equals(paymentIntent.getAmount()))
			return false;

		return true;
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
		builder.append(hasOutputs() ? Arrays.toString(outputs) : "null");
		builder.append(',');
		builder.append(paymentUrl);
		if (payeeData != null)
		{
			builder.append(',');
			builder.append(Arrays.toString(payeeData));
		}
		if (paymentRequestUrl != null)
		{
			builder.append(",paymentRequestUrl=");
			builder.append(paymentRequestUrl);
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

		if (outputs != null)
		{
			dest.writeInt(outputs.length);
			dest.writeTypedArray(outputs, 0);
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

		dest.writeString(paymentRequestUrl);
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

		final int outputsLength = in.readInt();
		if (outputsLength > 0)
		{
			outputs = new Output[outputsLength];
			in.readTypedArray(outputs, Output.CREATOR);
		}
		else
		{
			outputs = null;
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

		paymentRequestUrl = in.readString();
	}
}
