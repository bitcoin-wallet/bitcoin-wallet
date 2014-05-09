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

package de.schildbach.wallet.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.bitcoin.core.CoinDefinition;
import org.bitcoin.protocols.payments.Protos;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.protocols.payments.PaymentRequestException;
import com.google.bitcoin.protocols.payments.PaymentSession;
import com.google.bitcoin.protocols.payments.PaymentSession.PkiVerificationData;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UninitializedMessageException;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.PaymentIntent;

/**
 * @author Andreas Schildbach
 */
public final class PaymentProtocol
{
	public static final String MIMETYPE_PAYMENTREQUEST = "application/"+ CoinDefinition.coinName.toLowerCase()+"-paymentrequest"; // BIP 71
	public static final String MIMETYPE_PAYMENT = "application/"+ CoinDefinition.coinName.toLowerCase()+"-payment"; // BIP 71
	public static final String MIMETYPE_PAYMENTACK = "application/"+ CoinDefinition.coinName.toLowerCase()+"-paymentack"; // BIP 71

	public static Protos.PaymentRequest createPaymentRequest(final BigInteger amount, @Nonnull final Address toAddress, final String memo,
			final String paymentUrl)
	{
		if (amount != null && amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
			throw new IllegalArgumentException("amount too big for protobuf: " + amount);

		final Protos.Output.Builder output = Protos.Output.newBuilder();
		output.setAmount(amount != null ? amount.longValue() : 0);
		output.setScript(ByteString.copyFrom(ScriptBuilder.createOutputScript(toAddress).getProgram()));

		final Protos.PaymentDetails.Builder paymentDetails = Protos.PaymentDetails.newBuilder();
		paymentDetails.setNetwork(Constants.NETWORK_PARAMETERS.getPaymentProtocolId());
		paymentDetails.addOutputs(output);
		if (memo != null)
			paymentDetails.setMemo(memo);
		if (paymentUrl != null)
			paymentDetails.setPaymentUrl(paymentUrl);
		paymentDetails.setTime(System.currentTimeMillis());

		final Protos.PaymentRequest.Builder paymentRequest = Protos.PaymentRequest.newBuilder();
		paymentRequest.setSerializedPaymentDetails(paymentDetails.build().toByteString());

		return paymentRequest.build();
	}

	public static PaymentIntent parsePaymentRequest(@Nonnull final byte[] serializedPaymentRequest) throws PaymentRequestException
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

			final long currentTimeSecs = System.currentTimeMillis() / 1000;
			if (paymentDetails.hasExpires() && currentTimeSecs >= paymentDetails.getExpires())
				throw new PaymentRequestException.Expired("payment details expired: current time " + currentTimeSecs + " after expiry time "
						+ paymentDetails.getExpires());

			if (!paymentDetails.getNetwork().equals(Constants.NETWORK_PARAMETERS.getPaymentProtocolId()))
				throw new PaymentRequestException.InvalidNetwork("cannot handle payment request network: " + paymentDetails.getNetwork());

			final ArrayList<PaymentIntent.Output> outputs = new ArrayList<PaymentIntent.Output>(paymentDetails.getOutputsCount());
			for (final Protos.Output output : paymentDetails.getOutputsList())
				outputs.add(parseOutput(output));

			final String memo = paymentDetails.hasMemo() ? paymentDetails.getMemo() : null;
			final String paymentUrl = paymentDetails.hasPaymentUrl() ? paymentDetails.getPaymentUrl() : null;
			final byte[] merchantData = paymentDetails.hasMerchantData() ? paymentDetails.getMerchantData().toByteArray() : null;

			final PaymentIntent paymentIntent = new PaymentIntent(PaymentIntent.Standard.BIP70, pkiName, pkiOrgName, pkiCaName,
					outputs.toArray(new PaymentIntent.Output[0]), memo, paymentUrl, merchantData, null);

			if (paymentIntent.hasPaymentUrl() && !paymentIntent.isSupportedPaymentUrl())
				throw new PaymentRequestException.InvalidPaymentURL("cannot handle payment url: " + paymentIntent.paymentUrl);

			return paymentIntent;
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

	private static PaymentIntent.Output parseOutput(@Nonnull final Protos.Output output) throws PaymentRequestException.InvalidOutputs
	{
		try
		{
			final BigInteger amount = BigInteger.valueOf(output.getAmount());
			final Script script = new Script(output.getScript().toByteArray());
			return new PaymentIntent.Output(amount, script);
		}
		catch (final ScriptException x)
		{
			throw new PaymentRequestException.InvalidOutputs("unparseable script in output: " + output.toString());
		}
	}

	public static Protos.Payment createPaymentMessage(@Nonnull final Transaction transaction, @Nullable final Address refundAddress,
			@Nullable final BigInteger refundAmount, @Nullable final String memo, @Nullable final byte[] merchantData)
	{
		final Protos.Payment.Builder builder = Protos.Payment.newBuilder();

		builder.addTransactions(ByteString.copyFrom(transaction.unsafeBitcoinSerialize()));

		if (refundAddress != null)
		{
			if (refundAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
				throw new IllegalArgumentException("refund amount too big for protobuf: " + refundAmount);

			final Protos.Output.Builder refundOutput = Protos.Output.newBuilder();
			refundOutput.setAmount(refundAmount.longValue());
			refundOutput.setScript(ByteString.copyFrom(ScriptBuilder.createOutputScript(refundAddress).getProgram()));
			builder.addRefundTo(refundOutput);
		}

		if (memo != null)
			builder.setMemo(memo);

		if (merchantData != null)
			builder.setMerchantData(ByteString.copyFrom(merchantData));

		return builder.build();
	}

	public static List<Transaction> parsePaymentMessage(final Protos.Payment paymentMessage)
	{
		final List<Transaction> transactions = new ArrayList<Transaction>(paymentMessage.getTransactionsCount());

		for (final ByteString transaction : paymentMessage.getTransactionsList())
			transactions.add(new Transaction(Constants.NETWORK_PARAMETERS, transaction.toByteArray()));

		return transactions;
	}

	public static Protos.PaymentACK createPaymentAck(@Nonnull final Protos.Payment paymentMessage, @Nullable final String memo)
	{
		final Protos.PaymentACK.Builder builder = Protos.PaymentACK.newBuilder();

		builder.setPayment(paymentMessage);

		builder.setMemo(memo);

		return builder.build();
	}

	public static String parsePaymentAck(@Nonnull final Protos.PaymentACK paymentAck)
	{
		final String memo = paymentAck.hasMemo() ? paymentAck.getMemo() : null;

		return memo;
	}
}
