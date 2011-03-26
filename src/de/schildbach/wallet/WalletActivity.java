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
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.view.View.OnLongClickListener;
import android.view.Window;
import android.webkit.WebView;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * @author Andreas Schildbach
 */
public class WalletActivity extends Activity
{
	private Application application;
	private Peer peer;
	private Bitmap qrCodeBitmap;
	private Float exchangeRate;

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
		final ActionBar actionBar = (ActionBar) findViewById(R.id.action_bar);
		final TextView bitcoinAddressView = (TextView) findViewById(R.id.bitcoin_address);
		final ImageView bitcoinAddressQrView = (ImageView) findViewById(R.id.bitcoin_address_qr);

		actionBar.setIcon(R.drawable.app_icon);
		actionBar.setPrimaryTitle(R.string.app_name);
		actionBar.getButton().setImageResource(R.drawable.ic_menu_send);
		actionBar.getButton().setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				openSendCoinsDialog(null);
			}
		});
		actionBar.getProgressButton().setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				if (actionBar.getProgressCount() == 0)
				{
					try
					{
						final CountDownLatch latch = peer.startBlockChainDownload();

						new Thread()
						{
							@Override
							public void run()
							{
								try
								{
									latch.await();

									runOnUiThread(new Runnable()
									{
										public void run()
										{
											actionBar.stopProgress();
										}
									});
								}
								catch (final Exception x)
								{
									x.printStackTrace();
								}
							}
						}.start();

						actionBar.startProgress();
					}
					catch (final Exception x)
					{
						throw new RuntimeException(x);
					}
				}
			}
		});

		((TextView) findViewById(R.id.bitcoin_network)).setText(Constants.TEST ? "testnet" : "prodnet");

		wallet.addEventListener(new WalletEventListener()
		{
			@Override
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
			public void onReorganize()
			{
				System.out.println("!!!! reorganize");

				runOnUiThread(new Runnable()
				{
					public void run()
					{
						application.saveWallet();

						updateGUI();
					}
				});
			}
		});

		updateGUI();

		System.out.println(wallet.keychain.size() + " key(s) in keychain");
		final ECKey key = wallet.keychain.get(0);
		final Address address = key.toAddress(Constants.NETWORK_PARAMS);

		bitcoinAddressView.setText(splitIntoLines(address.toString(), 3));

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				try
				{
					final InetAddress inetAddress = Constants.TEST ? InetAddress.getByName(Constants.TEST_SEED_NODE)
							: inetAddressFromUnsignedInt(Constants.SEED_NODES[0]);
					final NetworkConnection connection = new NetworkConnection(inetAddress, Constants.NETWORK_PARAMS);
					final BlockChain chain = new BlockChain(Constants.NETWORK_PARAMS, wallet, application.getBlockStore());
					peer = new Peer(Constants.NETWORK_PARAMS, connection, chain);
					peer.start();

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

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				try
				{
					final Float newExchangeRate = getExchangeRate();

					if (newExchangeRate != null)
					{
						runOnUiThread(new Runnable()
						{
							public void run()
							{
								exchangeRate = newExchangeRate;
								updateGUI();
							}
						});
					}
				}
				catch (final Exception x)
				{
					throw new RuntimeException(x);
				}
			}
		});

		// populate qrcode representation of bitcoin address
		qrCodeBitmap = getQRCodeBitmap("bitcoin:" + address.toString());
		bitcoinAddressQrView.setImageBitmap(qrCodeBitmap);

		bitcoinAddressView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				clipboardManager.setText(address.toString());
				Toast.makeText(WalletActivity.this, "bitcoin address pasted to clipboard", Toast.LENGTH_SHORT).show();

				System.out.println("my bitcoin address: " + address + (Constants.TEST ? " (testnet!)" : ""));
			}
		});

		bitcoinAddressView.setOnLongClickListener(new OnLongClickListener()
		{
			public boolean onLongClick(final View v)
			{
				startActivity(Intent.createChooser(
						new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "bitcoin:" + address).setType("text/plain"),
						"Share your bitcoin address..."));
				return false;
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
				imageView.setImageBitmap(qrCodeBitmap);
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

	@Override
	protected void onDestroy()
	{
		if (qrCodeBitmap != null)
		{
			qrCodeBitmap.recycle();
			qrCodeBitmap = null;
		}

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

			case R.id.wallet_options_help:
				showDialog(0);
				return true;
		}

		return false;
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final WebView webView = new WebView(this);
		webView.loadUrl("file:///android_asset/help.html");

		final Dialog dialog = new Dialog(WalletActivity.this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	private void updateGUI()
	{
		final BigInteger balance = application.getWallet().getBalance();
		((TextView) findViewById(R.id.wallet_balance)).setText(Utils.bitcoinValueToFriendlyString(balance));

		final TextView walletBalanceInDollarsView = (TextView) findViewById(R.id.wallet_balance_in_dollars);
		if (balance.equals(BigInteger.ZERO))
		{
			walletBalanceInDollarsView.setText(null);
		}
		else if (exchangeRate != null)
		{
			final double dollars = Utils.bitcoinValueToDouble(balance) * exchangeRate;
			walletBalanceInDollarsView.setText(String.format("worth about US$ %.2f" + (Constants.TEST ? "\nif it were real bitcoins" : ""), dollars));
		}
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

	public final static QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();

	private static Bitmap getQRCodeBitmap(final String url)
	{
		final int SIZE = 256;

		try
		{
			final Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
			final BitMatrix result = QR_CODE_WRITER.encode(url, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);

			final int width = result.getWidth();
			final int height = result.getHeight();
			final int[] pixels = new int[width * height];

			for (int y = 0; y < height; y++)
			{
				final int offset = y * width;
				for (int x = 0; x < width; x++)
				{
					pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
				}
			}

			final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
			return bitmap;
		}
		catch (final WriterException x)
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

	private static final Pattern P_EXCHANGE_RATE = Pattern.compile("\"last\":(\\d*\\.\\d*)[^\\d]");

	private static Float getExchangeRate()
	{
		try
		{
			final URLConnection connection = new URL("http://mtgox.com/code/data/ticker.php").openConnection();
			connection.connect();
			final Reader is = new InputStreamReader(new BufferedInputStream(connection.getInputStream()));
			final StringBuilder content = new StringBuilder();
			copy(is, content);
			is.close();

			final Matcher m = P_EXCHANGE_RATE.matcher(content);
			if (m.find())
				return Float.parseFloat(m.group(1));
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}

		return null;
	}

	private static final long copy(final Reader reader, final StringBuilder builder) throws IOException
	{
		final char[] buffer = new char[256];
		long count = 0;
		int n = 0;
		while (-1 != (n = reader.read(buffer)))
		{
			builder.append(buffer, 0, n);
			count += n;
		}
		return count;
	}
}
