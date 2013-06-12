/**
 * Copyright 2012-2013 the original author or authors.
 * Copyright 2013 Google Inc.
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

import java.io.IOException;
import java.net.InetSocketAddress;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import de.schildbach.wallet.integration.android.AbstractTCPPaymentChannel;
import de.schildbach.wallet.integration.android.BitcoinIntegration;
import de.schildbach.wallet.integration.android.BitcoinPaymentChannelManager;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author Andreas Schildbach
 */
public class SampleActivity extends Activity
{
	private static final String TAG = SampleActivity.class.getName();

	private static final boolean testNet = true;
	private static final String DONATION_ADDRESS = testNet ? "mwEacn7pYszzxfgcNaVUzYvzL6ypRJzB6A" : "1PZmMahjbfsTy6DsaRyfStzoWTPppWwDnZ";
	private static final int DONATION_REQUEST_CODE = 0;
	private static final int CHANNEL_REQUEST_CODE = 1;

	private Button donateButton;
	private TextView donateMessage;
	private BitcoinPaymentChannelManager channel = null;
	private Button openChannelButton;
	private Button payChannelButton;
	private Button closeChannelButton;
	private EditText hostText;

	static String hexEncodeHash(byte[] contractHash) {
		StringBuilder buf = new StringBuilder(20);
		for (int i = 0; i < 10; i++) {
			String s = Integer.toString(0xFF & contractHash[i], 16);
			if (s.length() < 2)
				buf.append('0');
			buf.append(s);
		}
		return buf.toString();
	}

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
				BitcoinIntegration.requestForResult(SampleActivity.this, DONATION_REQUEST_CODE, DONATION_ADDRESS);
			}
		});

		donateMessage = (TextView) findViewById(R.id.sample_donate_message);

		hostText = (EditText) findViewById(R.id.channel_host_text);
		openChannelButton = (Button) findViewById(R.id.open_channel_button);
		payChannelButton = (Button) findViewById(R.id.pay_channel_button);
		closeChannelButton = (Button) findViewById(R.id.close_channel_button);

		openChannelButton.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				if (channel != null)
					return;
				attemptChannelOpen();
			}
		});

		payChannelButton.setOnClickListener(new OnClickListener() {
			final long amount = 1000;
			public void onClick(View v) {
				Futures.addCallback(channel.sendMoney(amount), new FutureCallback<Long>() {
					public void onSuccess(final Long nanoCoinsSent) {
						if (nanoCoinsSent != amount) {
							// A real app should queue the remaining value and resend it later
							SampleActivity.this.runOnUiThread(new Runnable() {
								public void run() {
									payChannelButton.setEnabled(false);
									Toast.makeText(SampleActivity.this, "Channel only sent " + nanoCoinsSent + ", waiting for reconnect", Toast.LENGTH_LONG).show();
								}
							});
						}
					}

					public void onFailure(final Throwable t) {
						SampleActivity.this.runOnUiThread(new Runnable() {
							public void run() {
								payChannelButton.setEnabled(false);
								Toast.makeText(SampleActivity.this, "Attempt to send threw " + t, Toast.LENGTH_LONG).show();
							}
						});
					}
				});
			}
		});
		payChannelButton.setEnabled(false);

		closeChannelButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				channel.closeChannel();
			}
		});
		closeChannelButton.setEnabled(false);
	}

	private void attemptChannelOpen() {
		final String host = hostText.getText().toString();
		final long minValue = 10000000;

		// We may have to call prepare multiple times, if the user has to step through some UI flows.
		BitcoinPaymentChannelManager.prepare(this, minValue, true, testNet, CHANNEL_REQUEST_CODE, new BitcoinPaymentChannelManager.PrepareCallback() {
			public void success() {
				// We got authorized!
					AbstractTCPPaymentChannel channelListener = new AbstractTCPPaymentChannel(new InetSocketAddress(host, 4242), 15 * 1000) {
						public void channelOpen(final byte[] contractHash) {
							if (contractHash.length != 32) {
								// A real app should securely contact the server and verify contractHash here
								channel.closeChannel();
								return;
							}
							SampleActivity.this.runOnUiThread(new Runnable() {
								public void run() {
									payChannelButton.setEnabled(true);
									closeChannelButton.setEnabled(true);
									openChannelButton.setEnabled(false);
									hostText.setEnabled(false);
									Toast.makeText(SampleActivity.this, "Channel opened " + hexEncodeHash(contractHash), Toast.LENGTH_SHORT).show();
								}
							});
						}

						public void channelInterruptedCalled() {
							SampleActivity.this.runOnUiThread(new Runnable() {
								public void run() {
									Toast.makeText(SampleActivity.this, "Channel interrupted, will reconnect on pay", Toast.LENGTH_LONG).show();
								}
							});
						}

						public void channelClosedOrNotOpenedCalled() {
							channel = null;
							SampleActivity.this.runOnUiThread(new Runnable() {
								public void run() {
									payChannelButton.setEnabled(false);
									closeChannelButton.setEnabled(false);
									openChannelButton.setEnabled(true);
									hostText.setEnabled(true);
									Toast.makeText(SampleActivity.this, "Channel closed", Toast.LENGTH_LONG).show();
								}
							});
							// A real app may wish to retry opening a new channel here
						}
					};
				channel = new BitcoinPaymentChannelManager(SampleActivity.this, host + 4242, testNet, channelListener);
				try {
					channelListener.connect(channel);
				} catch (final IOException e) {
					channel = null;
					SampleActivity.this.runOnUiThread(new Runnable() {
						public void run() {
							payChannelButton.setEnabled(false);
							closeChannelButton.setEnabled(false);
							openChannelButton.setEnabled(true);
							hostText.setEnabled(true);
							Toast.makeText(SampleActivity.this, "Failed to connect to server with exception " + e, Toast.LENGTH_LONG).show();
							Log.e(TAG, "Failed to connect to server", e);
						}
					});
					return;
				}
				channel.connect();
			}

			public void notifyUser(UIRequestReason reason) {
				// We're in the foreground so invokeUI is true and this callback is never used.
				// If we wanted to make payments from the background, this would be a place to pop up a notification
				// in the users notification bar, so they can fix our problem later.
			}
		});
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		if (requestCode == DONATION_REQUEST_CODE)
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

					donateMessage.setText(messageBuilder);
					donateMessage.setVisibility(View.VISIBLE);
				}

				donateButton.setEnabled(false);
				donateButton.setText("Already donated");

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
		else if (requestCode == CHANNEL_REQUEST_CODE)
		{
			if (resultCode != Activity.RESULT_CANCELED)
				attemptChannelOpen();
		}
	}
}
