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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bitcoin.protocols.payments.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.google.bitcoin.protocols.payments.PaymentRequestException.PkiVerificationException;
import com.google.bitcoin.protocols.payments.PaymentSession;
import com.google.bitcoin.protocols.payments.PaymentSession.PkiVerificationData;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UninitializedMessageException;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.PaymentIntent;
import de.schildbach.wallet.util.Io;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public abstract class InputParser
{
	private static final Logger log = LoggerFactory.getLogger(InputParser.class);

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
				catch (final IOException x)
				{
					log.info("i/o error while fetching payment request", x);

					error(R.string.input_parser_io_error, x.getMessage());
				}
				catch (final PkiVerificationException x)
				{
					log.info("got unverifyable payment request", x);

					error(R.string.input_parser_unverifyable_paymentrequest, x.getMessage());
				}
				catch (final PaymentRequestException x)
				{
					log.info("got invalid payment request", x);

					error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
				}
			}
			else if (input.startsWith("bitcoin:"))
			{
				try
				{
					final BitcoinURI bitcoinUri = new BitcoinURI(null, input);

					final Address address = bitcoinUri.getAddress();
					if (address == null)
						throw new BitcoinURIParseException("missing address");

					if (address.getParameters().equals(Constants.NETWORK_PARAMETERS))
						handlePaymentIntent(PaymentIntent.fromBitcoinUri(bitcoinUri));
					else
						error(R.string.input_parser_invalid_address, input);
				}
				catch (final BitcoinURIParseException x)
				{
					log.info("got invalid bitcoin uri: '" + input + "'", x);

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
					log.info("got invalid address", x);

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
					log.info("got invalid address", x);

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
					log.info("i/o error while fetching transaction", x);

					error(R.string.input_parser_invalid_transaction, x.getMessage());
				}
				catch (final ProtocolException x)
				{
					log.info("got invalid transaction", x);

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
					log.info("got invalid transaction", x);

					error(R.string.input_parser_invalid_transaction, x.getMessage());
				}
			}
			else if (Constants.MIMETYPE_PAYMENTREQUEST.equals(inputType))
			{
				try
				{
					parseAndHandlePaymentRequest(input);
				}
				catch (final PkiVerificationException x)
				{
					log.info("got unverifyable payment request", x);

					error(R.string.input_parser_unverifyable_paymentrequest, x.getMessage());
				}
				catch (final PaymentRequestException x)
				{
					log.info("got invalid payment request", x);

					error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
				}
			}
			else
			{
				cannotClassify(inputType);
			}
		}
	}

	public abstract static class StreamInputParser extends InputParser
	{
		private final String inputType;
		private final InputStream is;

		public StreamInputParser(@Nonnull final String inputType, @Nonnull final InputStream is)
		{
			this.inputType = inputType;
			this.is = is;
		}

		@Override
		public void parse()
		{
			if (Constants.MIMETYPE_PAYMENTREQUEST.equals(inputType))
			{
				ByteArrayOutputStream baos = null;

				try
				{
					baos = new ByteArrayOutputStream();
					Io.copy(is, baos);
					parseAndHandlePaymentRequest(baos.toByteArray());
				}
				catch (final IOException x)
				{
					log.info("i/o error while fetching payment request", x);

					error(R.string.input_parser_io_error, x.getMessage());
				}
				catch (final PkiVerificationException x)
				{
					log.info("got unverifyable payment request", x);

					error(R.string.input_parser_unverifyable_paymentrequest, x.getMessage());
				}
				catch (final PaymentRequestException x)
				{
					log.info("got invalid payment request", x);

					error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
				}
				finally
				{
					try
					{
						if (baos != null)
							baos.close();
					}
					catch (IOException x)
					{
						x.printStackTrace();
					}

					try
					{
						is.close();
					}
					catch (IOException x)
					{
						x.printStackTrace();
					}
				}
			}
			else
			{
				cannotClassify(inputType);
			}
		}
	}

	public abstract void parse();

	protected void parseAndHandlePaymentRequest(@Nonnull final byte[] serializedPaymentRequest) throws PaymentRequestException
	{
		try
		{
			if (serializedPaymentRequest.length > 50000)
				throw new PaymentRequestException("payment request too big: " + serializedPaymentRequest.length);

			final Protos.PaymentRequest paymentRequest = Protos.PaymentRequest.parseFrom(serializedPaymentRequest);

			final String pkiName;
			final String pkiOrgName;
			final String pkiCaName;
			if (!"none".equals(paymentRequest.getPkiType()))
			{
				// implicitly verify PKI signature
				final PkiVerificationData verificationData = new PaymentSession(paymentRequest, true).pkiVerificationData;
				pkiName = verificationData.name;
				pkiOrgName = verificationData.orgName;
				pkiCaName = verificationData.rootAuthorityName;
			}
			else
			{
				pkiName = null;
				pkiOrgName = null;
				pkiCaName = null;
			}

			if (paymentRequest.getPaymentDetailsVersion() != 1)
				throw new PaymentRequestException.InvalidVersion("cannot handle payment details version: "
						+ paymentRequest.getPaymentDetailsVersion());

			final Protos.PaymentDetails paymentDetails = Protos.PaymentDetails.newBuilder().mergeFrom(paymentRequest.getSerializedPaymentDetails())
					.build();

			if (paymentDetails.hasExpires() && System.currentTimeMillis() / 1000 >= paymentDetails.getExpires())
				throw new PaymentRequestException.Expired("payment details expired: " + paymentDetails.getExpires());

			if (!paymentDetails.getNetwork().equals(Constants.NETWORK_PARAMETERS.getPaymentProtocolId()))
				throw new PaymentRequestException.InvalidNetwork("cannot handle payment request network: " + paymentDetails.getNetwork());

			final ArrayList<PaymentIntent.Output> outputs = new ArrayList<PaymentIntent.Output>(paymentDetails.getOutputsCount());
			for (final Protos.Output output : paymentDetails.getOutputsList())
			{
				final BigInteger amount = BigInteger.valueOf(output.getAmount());
				final Script script = new Script(output.getScript().toByteArray());
				outputs.add(new PaymentIntent.Output(amount, script));
			}

			final String memo = paymentDetails.hasMemo() ? paymentDetails.getMemo() : null;
			final String paymentUrl = paymentDetails.hasPaymentUrl() ? paymentDetails.getPaymentUrl() : null;
			final byte[] merchantData = paymentDetails.hasMerchantData() ? paymentDetails.getMerchantData().toByteArray() : null;

			final PaymentIntent paymentIntent = new PaymentIntent(PaymentIntent.Standard.BIP70, pkiName, pkiOrgName, pkiCaName,
					outputs.toArray(new PaymentIntent.Output[0]), memo, paymentUrl, merchantData, null);

			if (paymentIntent.hasPaymentUrl() && !paymentIntent.isSupportedPaymentUrl())
				throw new PaymentRequestException.InvalidPaymentURL("cannot handle payment url: " + paymentIntent.paymentUrl);

			handlePaymentIntent(paymentIntent);
		}
		catch (final InvalidProtocolBufferException x)
		{
			throw new PaymentRequestException(x);
		}
		catch (final UninitializedMessageException x)
		{
			throw new PaymentRequestException(x);
		}
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
		dialog.singleDismissButton(dismissListener);
		dialog.show();
	}

	private static final Pattern PATTERN_BITCOIN_ADDRESS = Pattern.compile("[" + new String(Base58.ALPHABET) + "]{20,40}");
	private static final Pattern PATTERN_PRIVATE_KEY = Pattern.compile("5[" + new String(Base58.ALPHABET) + "]{50,51}");
	private static final Pattern PATTERN_TRANSACTION = Pattern.compile("[0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$\\*\\+\\-\\.\\/\\:]{100,}");
}
