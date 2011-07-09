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
		actionBar.setIcon(getWalletApplication().isTest() ? R.drawable.app_icon_test : R.drawable.app_icon);
		actionBar.setPrimaryTitle(R.string.app_name);
		actionBar.setSecondaryTitle(getWalletApplication().isTest() ? "[testnet!]" : null);
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
		actionBar.addButton(R.drawable.ic_menu_help).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showDialog(DIALOG_HELP);
			}
		});

		final FragmentManager fm = getSupportFragmentManager();
		fm.beginTransaction().hide(fm.findFragmentById(R.id.exchange_rates_fragment)).commit();

		checkTestnetProdnetMigrationAlert();
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
		menu.findItem(R.id.wallet_options_switch_network).setTitle("→ " + (getWalletApplication().isTest() ? "prodnet" : "testnet"));
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
				toast("coming soon");
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
		webView.loadUrl("file:///android_asset/" + (id == DIALOG_HELP ? "help.html" : "safety.html"));

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
		if (!getWalletApplication().isTest() && testBlockchain.exists())
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setTitle("Important notice!");
			builder.setMessage("Bitcoin Wallet has been switched from Testnet to Prodnet. This means that you can now pay for real! Please read the safety hints.\n\nIf you don't want to take the risk and stay on Testnet, please install Bitcoin Wallet for Testnet.");
			builder.setPositiveButton("Read Safety Hints", new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					testBlockchain.delete();

					showDialog(DIALOG_SAFETY);
				}
			});
			builder.setNegativeButton("Install Testnet", new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					toast("coming soon");
				}
			});
			builder.show();
		}
	}
}
