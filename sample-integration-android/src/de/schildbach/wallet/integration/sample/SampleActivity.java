/**
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.integration.sample;

import org.bitcoin.protocols.payments.Protos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.protobuf.ByteString;

import de.schildbach.wallet.integration.android.BitcoinIntegration;

/**
 * @author Andreas Schildbach
 */
public class SampleActivity extends Activity
{
	private static final long AMOUNT = 500000;
	private static final String DONATION_ADDRESS = "18CK5k1gajRKKSC7yVSTXT9LUzbheh1XY4"; // mainnet
	private static final String DONATION_ADDRESS2 = "1PZmMahjbfsTy6DsaRyfStzoWTPppWwDnZ"; // mainnet
	// private static final String DONATION_ADDRESS = "mkCLjaXncyw8eSWJBcBtnTgviU85z5PfwS"; // testnet
	// private static final String DONATION_ADDRESS2 = "mwEacn7pYszzxfgcNaVUzYvzL6ypRJzB6A"; // testnet
	private static final String MEMO = "Sample donation";
	private static final int REQUEST_CODE = 0;

	private Button donateButton, requestButton;
	private TextView donateMessage;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.sample_activity);

		donateButton = (Button) findViewById(R.id.sample_donate_button);
		donateButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				handleDonate();
			}
		});

		requestButton = (Button) findViewById(R.id.sample_request_button);
		requestButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				handleRequest();
			}
		});

		donateMessage = (TextView) findViewById(R.id.sample_donate_message);
	}

	private void handleDonate()
	{
		BitcoinIntegration.requestForResult(SampleActivity.this, REQUEST_CODE, DONATION_ADDRESS);
	}

	private void handleRequest()
	{
		try
		{
			final NetworkParameters params = Address.getParametersFromAddress(DONATION_ADDRESS);

			final Protos.Output.Builder output1 = Protos.Output.newBuilder();
			output1.setAmount(AMOUNT);
			output1.setScript(ByteString.copyFrom(ScriptBuilder.createOutputScript(new Address(params, DONATION_ADDRESS)).getProgram()));

			final Protos.Output.Builder output2 = Protos.Output.newBuilder();
			output2.setAmount(AMOUNT);
			output2.setScript(ByteString.copyFrom(ScriptBuilder.createOutputScript(new Address(params, DONATION_ADDRESS2)).getProgram()));

			final Protos.PaymentDetails.Builder paymentDetails = Protos.PaymentDetails.newBuilder();
			paymentDetails.setNetwork(params.getPaymentProtocolId());
			paymentDetails.addOutputs(output1);
			paymentDetails.addOutputs(output2);
			paymentDetails.setMemo(MEMO);
			paymentDetails.setTime(System.currentTimeMillis());

			final Protos.PaymentRequest.Builder paymentRequest = Protos.PaymentRequest.newBuilder();
			paymentRequest.setSerializedPaymentDetails(paymentDetails.build().toByteString());

			BitcoinIntegration.requestForResult(SampleActivity.this, REQUEST_CODE, paymentRequest.build().toByteArray());
		}
		catch (final AddressFormatException x)
		{
			throw new RuntimeException(x);
		}
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		if (requestCode == REQUEST_CODE)
		{
			if (resultCode == Activity.RESULT_OK)
			{
				final String txHash = BitcoinIntegration.transactionHashFromResult(data);
				if (txHash != null)
				{
					final SpannableStringBuilder messageBuilder = new SpannableStringBuilder("Transaction hash:\n");
					messageBuilder.append(txHash);
					messageBuilder.setSpan(new TypefaceSpan("monospace"), messageBuilder.length() - txHash.length(), messageBuilder.length(),
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

					if (BitcoinIntegration.paymentFromResult(data) != null)
						messageBuilder.append("\n(also a BIP70 payment message was received)");

					donateMessage.setText(messageBuilder);
					donateMessage.setVisibility(View.VISIBLE);
				}

				Toast.makeText(this, "Thank you!", Toast.LENGTH_LONG).show();
			}
			else if (resultCode == Activity.RESULT_CANCELED)
			{
				Toast.makeText(this, "Cancelled.", Toast.LENGTH_LONG).show();
			}
			else
			{
				Toast.makeText(this, "Unknown result.", Toast.LENGTH_LONG).show();
			}
		}
	}
}
