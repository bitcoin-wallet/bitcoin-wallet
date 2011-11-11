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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import android.widget.TextView;
import de.schildbach.wallet.util.ActionBarFragment;
import de.schildbach.wallet.util.ErrorReporter;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletActivity extends AbstractWalletActivity
{
	private static final int DIALOG_SAFETY = 1;
	private static final int DIALOG_HELP = 0;

	private int download;
	private Date chainheadDate;

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
		}

		public void onServiceDisconnected(final ComponentName name)
		{
		}
	};

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			download = intent.getIntExtra(Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD, Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
			chainheadDate = (Date) intent.getSerializableExtra(Service.ACTION_BLOCKCHAIN_STATE_CHAINHEAD_DATE);

			updateView();
		}
	};

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		ErrorReporter.getInstance().check(this);

		bindService(new Intent(this, Service.class), serviceConnection, Context.BIND_AUTO_CREATE);

		setContentView(R.layout.wallet_content);

		final ActionBarFragment actionBar = getActionBar();
		actionBar.setPrimaryTitle(R.string.app_name);

		actionBar.addButton(R.drawable.ic_menu_send).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(new Intent(WalletActivity.this, SendCoinsActivity.class));
			}
		});
		actionBar.addButton(R.drawable.ic_menu_request).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(new Intent(WalletActivity.this, RequestCoinsActivity.class));
			}
		});
		actionBar.addButton(R.drawable.ic_menu_address_book).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(new Intent(WalletActivity.this, AddressBookActivity.class));
			}
		});
		actionBar.addButton(R.drawable.ic_menu_help).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showDialog(DIALOG_HELP);
			}
		});

		findViewById(R.id.wallet_disclaimer).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showDialog(DIALOG_SAFETY);
			}
		});

		final FragmentManager fm = getSupportFragmentManager();
		fm.beginTransaction().hide(fm.findFragmentById(R.id.wallet_addresses_fragment)).hide(fm.findFragmentById(R.id.exchange_rates_fragment))
				.commit();

		checkVersionAndTimeskewAlert();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		registerReceiver(broadcastReceiver, new IntentFilter(Service.ACTION_BLOCKCHAIN_STATE));

		updateView();

		checkLowStorageAlert();
	}

	@Override
	protected void onPause()
	{
		unregisterReceiver(broadcastReceiver);

		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		unbindService(serviceConnection);

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.wallet_options, menu);
		menu.findItem(R.id.wallet_options_donate).setVisible(!Constants.TEST);
		menu.findItem(R.id.wallet_options_help).setVisible(Constants.TEST);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_options_send_coins:
				startActivity(new Intent(this, SendCoinsActivity.class));
				return true;

			case R.id.wallet_options_request_coins:
				startActivity(new Intent(WalletActivity.this, RequestCoinsActivity.class));
				return true;

			case R.id.wallet_options_preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				return true;

			case R.id.wallet_options_safety:
				showDialog(DIALOG_SAFETY);
				return true;

			case R.id.wallet_options_donate:
				final Intent intent = new Intent(this, SendCoinsActivity.class);
				intent.putExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS, Constants.DONATION_ADDRESS);
				startActivity(intent);
				return true;

			case R.id.wallet_options_help:
				showDialog(DIALOG_HELP);
				return true;
		}

		return false;
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final WebView webView = new WebView(this);
		if (id == DIALOG_HELP)
			webView.loadUrl("file:///android_asset/help" + languagePrefix() + ".html");
		else
			webView.loadUrl("file:///android_asset/safety" + languagePrefix() + ".html");

		final Dialog dialog = new Dialog(WalletActivity.this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	private void updateView()
	{
		final TextView messageView = (TextView) findViewById(R.id.wallet_message);
		final TextView disclaimerView = (TextView) findViewById(R.id.wallet_disclaimer);

		if (download != Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK)
		{
			messageView.setVisibility(View.VISIBLE);
			disclaimerView.setVisibility(View.INVISIBLE);

			if ((download & Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM) != 0)
				messageView.setText(R.string.wallet_message_blockchain_problem_storage);
			else if ((download & Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM) != 0)
				messageView.setText(R.string.wallet_message_blockchain_problem_power);
			else if ((download & Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM) != 0)
				messageView.setText(R.string.wallet_message_blockchain_problem_network);
		}
		else if (chainheadDate != null)
		{
			final long spanHours = (System.currentTimeMillis() - chainheadDate.getTime()) / 1000 / 60 / 60;

			messageView.setVisibility(spanHours < 2 ? View.INVISIBLE : View.VISIBLE);
			disclaimerView.setVisibility(spanHours < 2 ? View.VISIBLE : View.INVISIBLE);

			if (spanHours < 48)
				messageView.setText(getString(R.string.wallet_message_blockchain_hours, spanHours));
			else if (spanHours < 24 * 14)
				messageView.setText(getString(R.string.wallet_message_blockchain_days, spanHours / 24));
			else
				messageView.setText(getString(R.string.wallet_message_blockchain_weeks, spanHours / 24 / 7));
		}
		else
		{
			messageView.setVisibility(View.INVISIBLE);
			disclaimerView.setVisibility(View.VISIBLE);
		}
	}

	private void checkLowStorageAlert()
	{
		final Intent stickyIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
		if (stickyIntent != null)
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setTitle(R.string.wallet_low_storage_dialog_title);
			builder.setMessage(R.string.wallet_low_storage_dialog_msg);
			builder.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
					finish();
				}
			});
			builder.setNegativeButton(R.string.wallet_low_storage_dialog_button_dismiss, null);
			builder.show();
		}
	}

	private void checkVersionAndTimeskewAlert()
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					final int versionCode = ((de.schildbach.wallet.Application) getApplication()).versionCode();
					final URLConnection connection = new URL(Constants.VERSION_URL + "?current=" + versionCode).openConnection();
					connection.connect();
					final long serverTime = connection.getHeaderFieldDate("Date", 0);
					final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
					// final String version = reader.readLine();
					reader.close();

					if (serverTime > 0)
					{
						final long diffMinutes = Math.abs((System.currentTimeMillis() - serverTime) / 1000 / 60);

						if (diffMinutes >= 60)
						{
							runOnUiThread(new Runnable()
							{
								public void run()
								{
									if (!isFinishing())
										timeskewAlert(diffMinutes);
								}
							});
						}
					}
				}
				catch (final IOException x)
				{
					x.printStackTrace();
				}
			}
		}.start();
	}

	private void timeskewAlert(final long diffMinutes)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_timeskew_dialog_title);
		builder.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));
		builder.setPositiveButton(R.string.wallet_timeskew_dialog_button_settings, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int id)
			{
				startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS));
				finish();
			}
		});
		builder.setNegativeButton(R.string.wallet_timeskew_dialog_button_dismiss, null);
		builder.show();
	}
}
