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

import java.io.File;

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletActivity extends AbstractWalletActivity
{
	private static final int DIALOG_SAFETY = 1;
	private static final int DIALOG_HELP = 0;

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
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

		bindService(new Intent(this, Service.class), serviceConnection, Context.BIND_AUTO_CREATE);

		setContentView(R.layout.wallet_content);

		final ActionBarFragment actionBar = (ActionBarFragment) getSupportFragmentManager().findFragmentById(R.id.action_bar_fragment);
		actionBar.setIcon(Constants.APP_ICON_RESID);
		actionBar.setPrimaryTitle(R.string.app_name);
		actionBar.setSecondaryTitle(Constants.TEST ? "[testnet!]" : null);
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

		checkTestnetProdnetMigrationAlert();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		checkLowStorageAlert();
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
		menu.findItem(R.id.wallet_options_switch_network).setTitle("→ " + (Constants.TEST ? "Prodnet" : "Testnet"));
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

			case R.id.wallet_options_switch_network:
				switchNetwork(!Constants.TEST);
				return true;

			case R.id.wallet_options_safety:
				showDialog(DIALOG_SAFETY);
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

	private void checkTestnetProdnetMigrationAlert()
	{
		final File testBlockchain = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE),
				Constants.BLOCKCHAIN_FILENAME_TEST);
		if (!Constants.TEST && testBlockchain.exists())
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setTitle(R.string.wallet_migration_dialog_title);
			builder.setMessage(R.string.wallet_migration_dialog_msg);
			builder.setPositiveButton(R.string.wallet_migration_dialog_button_safety, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					testBlockchain.delete();

					showDialog(DIALOG_SAFETY);
				}
			});
			builder.setNegativeButton(R.string.wallet_migration_dialog_button_testnet, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					switchNetwork(true);
				}
			});
			builder.show();
		}
	}

	private void switchNetwork(final boolean test)
	{
		final String packageName = test ? Constants.PACKAGE_NAME_TEST : Constants.PACKAGE_NAME_PROD;
		final String className = getClass().getName();
		final Intent intent = new Intent().setClassName(packageName, className);
		if (getPackageManager().resolveActivity(intent, 0) != null)
			startActivity(intent);
		else
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.MARKET_APP_URL, packageName))));
		finish();
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
}
