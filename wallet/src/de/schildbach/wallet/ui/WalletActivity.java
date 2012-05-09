/*
 * Copyright 2011-2012 the original author or authors.
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Window;
import android.webkit.WebView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.util.ErrorReporter;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractWalletActivity
{
	public static final int DIALOG_SAFETY = 1;
	private static final int DIALOG_HELP = 0;

	private BlockchainService service;

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((BlockchainServiceImpl.LocalBinder) binder).getService();

			service.cancelCoinsReceived();
		}

		public void onServiceDisconnected(final ComponentName name)
		{
		}
	};

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		ErrorReporter.getInstance().check(this);

		setContentView(R.layout.wallet_content);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setTitle(R.string.app_name);

		final boolean twoPanes = findViewById(R.id.wallet_main_twopanes) != null;
		if (!twoPanes)
		{
			final FragmentManager fm = getSupportFragmentManager();
			final FragmentTransaction ft = fm.beginTransaction();
			ft.hide(fm.findFragmentById(R.id.exchange_rates_fragment));
			ft.commit();
		}

		checkVersionAndTimeskewAlert();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		bindService(new Intent(this, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);

		checkLowStorageAlert();
	}

	@Override
	protected void onPause()
	{
		unbindService(serviceConnection);

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getSupportMenuInflater().inflate(R.menu.wallet_options, menu);
		menu.findItem(R.id.wallet_options_donate).setVisible(!Constants.TEST);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_options_request:
				startActivity(new Intent(this, RequestCoinsActivity.class));
				return true;

			case R.id.wallet_options_send:
				startActivity(new Intent(this, SendCoinsActivity.class));
				return true;

			case R.id.wallet_options_address_book:
				AddressBookActivity.start(this, true);
				return true;

			case R.id.wallet_options_peer_monitor:
				startActivity(new Intent(this, PeerMonitorActivity.class));
				return true;

			case R.id.wallet_options_preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				return true;

			case R.id.wallet_options_about:
				startActivity(new Intent(this, AboutActivity.class));
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

		return super.onOptionsItemSelected(item);
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
			builder.setNegativeButton(R.string.button_dismiss, null);
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
					final int versionCode = getWalletApplication().applicationVersionCode();
					final URLConnection connection = new URL(Constants.VERSION_URL + "?current=" + versionCode).openConnection();
					connection.connect();
					final long serverTime = connection.getHeaderFieldDate("Date", 0);
					final InputStream is = connection.getInputStream();
					final BufferedReader reader = new BufferedReader(new InputStreamReader(is), 64);
					final int serverVersionCode = Integer.parseInt(reader.readLine().trim().split("\\s+")[0]);
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

							return;
						}
					}

					if (serverVersionCode > versionCode)
					{
						runOnUiThread(new Runnable()
						{
							public void run()
							{
								if (!isFinishing())
									versionAlert(serverVersionCode);
							}
						});

						return;
					}
				}
				catch (final Exception x)
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
		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	private void versionAlert(final int serverVersionCode)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_version_dialog_title);
		builder.setMessage(getString(R.string.wallet_version_dialog_msg));
		builder.setPositiveButton(R.string.wallet_version_dialog_button_market, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int id)
			{
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName()))));
				finish();
			}
		});
		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}
}
