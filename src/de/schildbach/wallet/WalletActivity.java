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

import java.io.IOException;
import java.math.BigInteger;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;

/**
 * @author Andreas Schildbach
 */
public class WalletActivity extends FragmentActivity
{
	private Application application;
	private Service service;

	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((Service.LocalBinder) binder).getService();
			onServiceBound();
		}

		public void onServiceDisconnected(final ComponentName name)
		{
			service = null;
			onServiceUnbound();
		}
	};

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = (Application) getApplication();

		bindService(new Intent(this, Service.class), serviceConnection, Context.BIND_AUTO_CREATE);

		// background thread
		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		setContentView(R.layout.wallet_content);
		final ActionBarFragment actionBar = (ActionBarFragment) getSupportFragmentManager().findFragmentById(R.id.action_bar_fragment);

		actionBar.setIcon(R.drawable.app_icon);
		actionBar.setPrimaryTitle(R.string.app_name);
		actionBar.setSecondaryTitle(Constants.TEST ? "[testnet!]" : null);
		actionBar.getButton().setImageResource(R.drawable.ic_menu_send);
		actionBar.getButton().setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				openSendCoinsDialog(null);
			}
		});

		final Uri intentUri = getIntent().getData();
		if (intentUri != null && "bitcoin".equals(intentUri.getScheme()))
			openSendCoinsDialog(intentUri.getSchemeSpecificPart());
	}

	protected void onServiceBound()
	{
		System.out.println("service bound");
	}

	protected void onServiceUnbound()
	{
		System.out.println("service unbound");
	}

	@Override
	protected void onDestroy()
	{
		// cancel background thread
		backgroundThread.getLooper().quit();

		unbindService(serviceConnection);

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
					final BigInteger amount = Utils.toNanoCoins(((TextView) dialog.findViewById(R.id.send_coins_amount)).getText().toString());

					backgroundHandler.post(new Runnable()
					{
						public void run()
						{
							try
							{
								final Transaction tx = service.sendCoins(receivingAddress, amount);

								if (tx != null)
								{
									runOnUiThread(new Runnable()
									{
										public void run()
										{
											application.saveWallet();

											((WalletBalanceFragment) getSupportFragmentManager().findFragmentById(R.id.wallet_balance_fragment))
													.updateView();

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
}
