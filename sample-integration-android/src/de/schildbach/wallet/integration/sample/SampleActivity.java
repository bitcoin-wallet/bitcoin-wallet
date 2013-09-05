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
import de.schildbach.wallet.integration.android.ChannelListener;

import java.io.IOException;
import java.net.InetSocketAddress;

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

	// Amounts in satoshis
	public static final int COIN = 10000000;
	public static final int CENT = COIN / 100;

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
			// Pay at least the min output value to avoid the case where not enough value is sent to actually close the
			// channel (eg dust output would be created). In a real app of course, you would just avoid getting
			// into a situation where the payments are so small the transaction can't ever be confirmed.
			final long amount = 6000;
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
                channel = null;
			}
		});
		closeChannelButton.setEnabled(false);
	}

	private void attemptChannelOpen() {
		final String host = hostText.getText().toString();

        openChannelButton.setEnabled(false);

		// The minimum amount we're going to ask the user to authorize us for. In this case, 10 millibits. The server
		// is allowed to have a minimum channel size it's willing to tolerate - if here we ask the user for less than
		// what the server allows, the channel can fail to build entirely, so it's best if we're a bit pushy here and
		// ask for more than we might really need. The user can of course choose to give us more on the permissions
		// screen that will pop up.
		//
		// The ExamplePaymentChannelServer app in the bitcoinj distribution shows how to receive these micropayments,
		// and it has a minimum required channel size of 1 millibit, so we're going to ask for 10x the min amount.
		// This is NOT the minimum payment granularity - once the channel is established, we can make payments of a
		// single satoshi if the server is willing, but because we must eventually settle on the block chain the total
		// amount of value put into the channel must be at least the minimum allowed value for a transaction.
		final long minValue = CENT;

		// We may have to call prepare multiple times, if the user has to step through some UI flows.
		BitcoinPaymentChannelManager.prepare(this, minValue, true, testNet, CHANNEL_REQUEST_CODE, new BitcoinPaymentChannelManager.PrepareCallback() {
			public void success() {
				// We got authorized! Use the helper library to set up a simple TCP messaging socket.
				AbstractTCPPaymentChannel channelListener = new AbstractTCPPaymentChannel(new InetSocketAddress(host, 4242), 15 * 1000) {
                    boolean wasOpened = false;

					public void channelOpen(final byte[] contractHash) {
						if (contractHash.length != 32) {
							// A real app should securely contact the server and verify contractHash here
							channel.closeChannel();
							return;
						}
                        wasOpened = true;
						SampleActivity.this.runOnUiThread(new Runnable() {
							public void run() {
								payChannelButton.setEnabled(true);
								closeChannelButton.setEnabled(true);
								hostText.setEnabled(false);
								Toast.makeText(SampleActivity.this, "Channel opened " + hexEncodeHash(contractHash), Toast.LENGTH_SHORT).show();
							}
						});
					}

                    @Override
					public void channelInterrupted() {
                        super.channelInterrupted();
						SampleActivity.this.runOnUiThread(new Runnable() {
							public void run() {
								Toast.makeText(SampleActivity.this, "Channel interrupted, will reconnect on pay", Toast.LENGTH_LONG).show();
							}
						});
					}

                    @Override
					public void channelClosedOrNotOpened(final ChannelListener.CloseReason reason) {
                        super.channelClosedOrNotOpened(reason);
						channel = null;
						SampleActivity.this.runOnUiThread(new Runnable() {
							public void run() {
								payChannelButton.setEnabled(false);
								closeChannelButton.setEnabled(false);
								openChannelButton.setEnabled(true);
								hostText.setEnabled(true);
                                if (wasOpened)
								    Toast.makeText(SampleActivity.this, "Channel closed: " + reason, Toast.LENGTH_LONG).show();
                                else
                                    Toast.makeText(SampleActivity.this, "Channel failed to open: " + reason, Toast.LENGTH_LONG).show();
							}
						});
						// A real app may wish to retry opening a new channel here
					}
				};
				// And now make a payments manager that uses it. hostID is some arbitrary string that identifies the
				// entity you're paying - normally just host+port suffices to identify the server, but it could be
				// other things too.
				final String hostId = host + 4242;
				channel = new BitcoinPaymentChannelManager(SampleActivity.this, hostId, testNet, channelListener);
				// Connect the TCP socket to the server.
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
				// And now we have a working socket, start the process of talking to Bitcoin Wallet and getting the
				// stuff we need to process micropayments. This line starts the logical communication between the
				// different parts of the protocol, with the channelListener tying it all to the network (this is why
				// there are two connect calls, which may seem confusing otherwise).
				channel.connect();
			}

			public void notifyUser(UIRequestReason reason) {
				// We're in the foreground so invokeUI is true and this callback is never used.
				// If we wanted to make payments from the background, this would be a place to pop up a notification
				// in the users notification bar, so they can fix our problem later.
				switch (reason) {
					case NEED_AUTH:
						// We ran out of money and need to ask the user for more.
						break;
					case NEED_WALLET_APP:
						// The wallet app vanished (i.e. the user uninstalled or disabled it).
						break;
				}
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
