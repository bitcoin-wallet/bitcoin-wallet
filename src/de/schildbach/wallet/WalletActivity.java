/*
 * Copyright 2010 the original author or authors.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.UnknownHostException;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.ClipboardManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkConnection;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

/**
 * @author Andreas Schildbach
 */
public class WalletActivity extends Activity implements WalletEventListener
{
	private Application application;
	private Peer peer;

	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = (Application) getApplication();

		// background thread
		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		final Wallet wallet = application.getWallet();

		setContentView(R.layout.wallet_content);
		final TextView bitcoinAddressView = (TextView) findViewById(R.id.bitcoin_address);
		final ImageView bitcoinAddressQrView = (ImageView) findViewById(R.id.bitcoin_address_qr);

		((TextView) findViewById(R.id.bitcoin_network)).setText(Constants.TEST ? "testnet" : "prodnet");

		wallet.addEventListener(this);

		updateGUI();

		System.out.println(wallet.keychain.size() + " key(s) in keychain");
		final ECKey key = wallet.keychain.get(0);
		final Address address = key.toAddress(Constants.NETWORK_PARAMS);

		final String addressStr = address.toString();
		System.out.println("my bitcoin address: " + addressStr + (Constants.TEST ? " (testnet!)" : ""));
		bitcoinAddressView.setText(splitIntoLines(addressStr, 3));

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				try
				{
					final InetAddress inetAddress = Constants.TEST ? InetAddress.getByName(Constants.TEST_SEED_NODE)
							: inetAddressFromUnsignedInt(Constants.SEED_NODES[0]);
					final NetworkConnection connection = new NetworkConnection(inetAddress, Constants.NETWORK_PARAMS);
					final BlockChain chain = new BlockChain(Constants.NETWORK_PARAMS, wallet);
					peer = new Peer(Constants.NETWORK_PARAMS, connection, chain);
					peer.start();
					peer.startBlockChainDownload();

					runOnUiThread(new Runnable()
					{
						public void run()
						{
							((TextView) findViewById(R.id.peer_host)).setText(inetAddress.getHostAddress());
						}
					});
				}
				catch (final Exception x)
				{
					throw new RuntimeException(x);
				}
			}
		});

		// populate qrcode representation of bitcoin address
		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				final Bitmap qrCodeBitmap = getQRCodeBitmap("bitcoin:" + addressStr);

				runOnUiThread(new Runnable()
				{
					public void run()
					{
						bitcoinAddressQrView.setImageBitmap(qrCodeBitmap);
					}
				});
			}
		});

		bitcoinAddressView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				clipboardManager.setText(addressStr);
				Toast.makeText(WalletActivity.this, "bitcoin address pasted to clipboard", Toast.LENGTH_SHORT).show();
			}
		});

		bitcoinAddressQrView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final Dialog dialog = new Dialog(WalletActivity.this);
				dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
				dialog.setContentView(R.layout.bitcoin_address_qr_dialog);
				final ImageView imageView = (ImageView) dialog.findViewById(R.id.bitcoin_address_qr);
				imageView.setImageBitmap(getQRCodeBitmap("bitcoin:" + addressStr));
				dialog.setCanceledOnTouchOutside(true);
				dialog.show();
				imageView.setOnClickListener(new OnClickListener()
				{
					public void onClick(final View v)
					{
						dialog.dismiss();
					}
				});
			}
		});

		final Uri intentUri = getIntent().getData();
		if (intentUri != null && "bitcoin".equals(intentUri.getScheme()))
			openSendCoinsDialog(intentUri.getSchemeSpecificPart());
	}

	public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
	{
		try
		{
			final TransactionInput input = tx.getInputs().get(0);
			final Address from = input.getFromAddress();
			final BigInteger value = tx.getValueSentToMe(w);

			System.out.println("!!!!!!!!!!!!! got bitcoins: " + from + " " + value + " " + Thread.currentThread().getName());

			runOnUiThread(new Runnable()
			{
				public void run()
				{
					application.saveWallet();

					updateGUI();
				}
			});
		}
		catch (Exception x)
		{
			throw new RuntimeException(x);
		}
	}

	@Override
	protected void onDestroy()
	{
		if (peer != null)
		{
			peer.disconnect();
			peer = null;
		}

		// cancel background thread
		backgroundThread.getLooper().quit();

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.wallet_options, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_options_send_coins:
				openSendCoinsDialog(null);
				return true;

			case R.id.wallet_options_preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				return true;
		}

		return false;
	}

	private void updateGUI()
	{
		((TextView) findViewById(R.id.wallet_balance)).setText(Utils.bitcoinValueToFriendlyString(application.getWallet().getBalance()));
	}

	private void openSendCoinsDialog(final String receivingAddressStr)
	{
		final Dialog dialog = new Dialog(this, android.R.style.Theme_Light);
		dialog.setContentView(R.layout.send_coins_content);
		final TextView receivingAddressView = (TextView) dialog.findViewById(R.id.send_coins_receiving_address);
		if (receivingAddressStr != null)
			receivingAddressView.setText(receivingAddressStr);
		dialog.show();

		dialog.findViewById(R.id.send_coins_go).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				try
				{
					final Address receivingAddress = new Address(Constants.NETWORK_PARAMS, receivingAddressView.getText().toString());
					final BigInteger amount = Utils.toNanoCoins(((TextView) dialog.findViewById(R.id.send_coins_amount)).getText());
					System.out.println("about to send " + amount + " (BTC " + Utils.bitcoinValueToFriendlyString(amount) + ") to " + receivingAddress);

					backgroundHandler.post(new Runnable()
					{
						public void run()
						{
							try
							{
								final Transaction tx = application.getWallet().sendCoins(peer, receivingAddress, amount);

								if (tx != null)
								{
									runOnUiThread(new Runnable()
									{
										public void run()
										{
											application.saveWallet();

											updateGUI();

											dialog.dismiss();

											Toast.makeText(WalletActivity.this, Utils.bitcoinValueToFriendlyString(amount) + " BTC sent!",
													Toast.LENGTH_LONG).show();
										}
									});
								}
								else
								{
									runOnUiThread(new Runnable()
									{
										public void run()
										{
											Toast.makeText(WalletActivity.this, "problem sending coins!", Toast.LENGTH_LONG).show();
											dialog.dismiss();
										}
									});
								}
							}
							catch (final IOException x)
							{
								x.printStackTrace();

								runOnUiThread(new Runnable()
								{
									public void run()
									{
										Toast.makeText(WalletActivity.this, "problem sending coins: " + x.getMessage(), Toast.LENGTH_LONG).show();
										dialog.dismiss();
									}
								});
							}
						}
					});
				}
				catch (final AddressFormatException x)
				{
					x.printStackTrace();
				}
			}
		});

		dialog.findViewById(R.id.send_coins_cancel).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				dialog.dismiss();
			}
		});
	}

	private static InetAddress inetAddressFromUnsignedInt(final long l)
	{
		final byte[] bytes = { (byte) (l & 0x000000ff), (byte) ((l & 0x0000ff00) >> 8), (byte) ((l & 0x00ff0000) >> 16),
				(byte) ((l & 0xff000000) >> 24) };
		try
		{
			return InetAddress.getByAddress(bytes);
		}
		catch (final UnknownHostException x)
		{
			throw new RuntimeException(x);
		}
	}

	private Bitmap getQRCodeBitmap(final String url)
	{
		try
		{
			final URLConnection connection = new URL("http://chart.apis.google.com/chart?cht=qr&chs=256x256&chld=H|0&chl="
					+ URLEncoder.encode(url, "ISO-8859-1")).openConnection();
			connection.connect();
			final BufferedInputStream is = new BufferedInputStream(connection.getInputStream());
			final Bitmap bm = BitmapFactory.decodeStream(is);
			is.close();
			return bm;
		}
		catch (final IOException x)
		{
			x.printStackTrace();
			return null;
		}
	}

	private static String splitIntoLines(final String str, final int lines)
	{
		if (lines < 2)
			return str;

		final int len = (int) Math.ceil((float) str.length() / lines);
		final StringBuilder builder = new StringBuilder(str);
		for (int i = 0; i < lines - 1; i++)
			builder.insert(len + i * (len + 1), '\n');

		return builder.toString();
	}
}
