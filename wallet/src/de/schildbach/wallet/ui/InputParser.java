/*
 * Copyright 2013-2014 the original author or authors.
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

import java.io.IOException;
import java.math.BigInteger;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bitcoin.protocols.payments.Protos;

import android.content.Context;
import android.content.DialogInterface.OnClickListener;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.protocols.payments.PaymentRequestException;
import com.google.bitcoin.protocols.payments.PaymentSession;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.PaymentIntent;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public abstract class InputParser
{
	public abstract static class StringInputParser extends InputParser
	{
		private final String input;

		public StringInputParser(@Nonnull final String input)
		{
			this.input = input;
		}

		@Override
		public void parse()
		{
			if (input.startsWith("BITCOIN:-"))
			{
				try
				{
					final byte[] serializedPaymentRequest = Qr.decodeBinary(input.substring(9));

					parseAndHandlePaymentRequest(serializedPaymentRequest);
				}
				catch (final PaymentRequestException x)
				{
					error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
				}
				catch (final IOException x)
				{
					error(R.string.input_parser_io_error, x.getMessage());
				}
			}
			else if (input.startsWith("bitcoin:"))
			{
				try
				{
					final BitcoinURI bitcoinUri = new BitcoinURI(null, input);

					handlePaymentIntent(PaymentIntent.fromBitcoinUri(bitcoinUri));
				}
				catch (final BitcoinURIParseException x)
				{
					error(R.string.input_parser_invalid_bitcoin_uri, input);
				}
			}
			else if (PATTERN_BITCOIN_ADDRESS.matcher(input).matches())
			{
				try
				{
					final Address address = new Address(Constants.NETWORK_PARAMETERS, input);

					handlePaymentIntent(PaymentIntent.fromAddress(address, null));
				}
				catch (final AddressFormatException x)
				{
					error(R.string.input_parser_invalid_address);
				}
			}
			else if (PATTERN_PRIVATE_KEY.matcher(input).matches())
			{
				try
				{
					final ECKey key = new DumpedPrivateKey(Constants.NETWORK_PARAMETERS, input).getKey();
					final Address address = new Address(Constants.NETWORK_PARAMETERS, key.getPubKeyHash());

					handlePaymentIntent(PaymentIntent.fromAddress(address, null));
				}
				catch (final AddressFormatException x)
				{
					error(R.string.input_parser_invalid_address);
				}
			}
			else if (PATTERN_TRANSACTION.matcher(input).matches())
			{
				try
				{
					final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, Qr.decodeDecompressBinary(input));

					handleDirectTransaction(tx);
				}
				catch (final IOException x)
				{
					error(R.string.input_parser_invalid_transaction, x.getMessage());
				}
				catch (final ProtocolException x)
				{
					error(R.string.input_parser_invalid_transaction, x.getMessage());
				}
			}
			else
			{
				cannotClassify(input);
			}
		}
	}

	public abstract static class BinaryInputParser extends InputParser
	{
		private final String inputType;
		private final byte[] input;

		public BinaryInputParser(@Nonnull final String inputType, @Nonnull final byte[] input)
		{
			this.inputType = inputType;
			this.input = input;
		}

		@Override
		public void parse()
		{
			if (Constants.MIMETYPE_TRANSACTION.equals(inputType))
			{
				try
				{
					final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, input);

					handleDirectTransaction(tx);
				}
				catch (final ProtocolException x)
				{
					error(R.string.input_parser_invalid_transaction, x.getMessage());
				}
			}
			else if (Constants.MIMETYPE_PAYMENTREQUEST.equals(inputType))
			{
				try
				{
					parseAndHandlePaymentRequest(input);
				}
				catch (final PaymentRequestException x)
				{
					error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
				}
				catch (final InvalidProtocolBufferException x)
				{
					error(R.string.input_parser_io_error, x.getMessage());
				}
			}
			else
			{
				cannotClassify(inputType);
			}
		}
	}

	public abstract void parse();

	protected void parseAndHandlePaymentRequest(@Nonnull final byte[] serializedPaymentRequest) throws PaymentRequestException,
			InvalidProtocolBufferException
	{
		if (serializedPaymentRequest.length > 50000)
			throw new PaymentRequestException("payment request too big: " + serializedPaymentRequest.length);

		final Protos.PaymentRequest paymentRequest = Protos.PaymentRequest.parseFrom(serializedPaymentRequest);

		if (!"none".equals(paymentRequest.getPkiType()))
			new PaymentSession(paymentRequest, true); // verify PKI signature

		if (paymentRequest.getPaymentDetailsVersion() != 1)
			throw new PaymentRequestException.InvalidVersion("cannot handle payment details version: " + paymentRequest.getPaymentDetailsVersion());

		final Protos.PaymentDetails paymentDetails = Protos.PaymentDetails.newBuilder().mergeFrom(paymentRequest.getSerializedPaymentDetails())
				.build();

		if (paymentDetails.hasExpires() && System.currentTimeMillis() >= paymentDetails.getExpires())
			throw new PaymentRequestException.Expired("payment details expired: " + paymentDetails.getExpires());

		if (!paymentDetails.getNetwork().equals(Constants.NETWORK_PARAMETERS.getPaymentProtocolId()))
			throw new PaymentRequestException.InvalidNetwork("cannot handle payment request network: " + paymentDetails.getNetwork());

		if (paymentDetails.getOutputsCount() != 1)
			throw new PaymentRequestException.InvalidOutputs("can only handle payment requests with 1 output");

		final Protos.Output output = paymentDetails.getOutputs(0);
		final Script script = new Script(output.getScript().toByteArray());

		if (!script.isSentToAddress())
			throw new PaymentRequestException.InvalidOutputs("can only handle send-to-address scripts in payment request");

		final String paymentUrl = paymentDetails.getPaymentUrl();
		final String bluetoothMac = paymentUrl != null && paymentUrl.startsWith("bt:") ? paymentUrl.substring(3) : null;

		final long amount = output.getAmount();

		final ByteString merchantData = paymentDetails.getMerchantData();

		handlePaymentIntent(new PaymentIntent(PaymentIntent.Standard.BIP70, script.getToAddress(Constants.NETWORK_PARAMETERS),
				paymentDetails.getMemo(), amount != 0 ? BigInteger.valueOf(amount) : null, bluetoothMac,
				merchantData != null ? merchantData.toByteArray() : null));
	}

	protected abstract void handlePaymentIntent(@Nonnull PaymentIntent paymentIntent);

	protected abstract void handleDirectTransaction(@Nonnull Transaction transaction);

	protected abstract void error(int messageResId, Object... messageArgs);

	protected void cannotClassify(@Nonnull final String input)
	{
		error(R.string.input_parser_cannot_classify, input);
	}

	protected void dialog(final Context context, @Nullable final OnClickListener dismissListener, final int titleResId, final int messageResId,
			final Object... messageArgs)
	{
		final DialogBuilder dialog = new DialogBuilder(context);
		if (titleResId != 0)
			dialog.setTitle(titleResId);
		dialog.setMessage(context.getString(messageResId, messageArgs));
		dialog.setNeutralButton(R.string.button_dismiss, dismissListener);
		dialog.show();
	}

	private static final Pattern PATTERN_BITCOIN_ADDRESS = Pattern.compile("[" + new String(Base58.ALPHABET) + "]{20,40}");
	private static final Pattern PATTERN_PRIVATE_KEY = Pattern.compile("5[" + new String(Base58.ALPHABET) + "]{50,51}");
	private static final Pattern PATTERN_TRANSACTION = Pattern.compile("[0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$\\*\\+\\-\\.\\/\\:]{100,}");
}
