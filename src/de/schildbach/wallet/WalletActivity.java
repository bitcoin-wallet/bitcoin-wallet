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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;

import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Transaction;

import de.schildbach.wallet.util.ActionBarFragment;
import de.schildbach.wallet.util.Base43;
import de.schildbach.wallet.util.ErrorReporter;
import de.schildbach.wallet.util.NfcTools;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletActivity extends AbstractWalletActivity
{
	public static final int DIALOG_SAFETY = 1;
	private static final int DIALOG_HELP = 0;

	private static final int GINGERBREAD_MR1 = 10; // from API level 10
	private static final String EXTRA_NDEF_MESSAGES = "android.nfc.extra.NDEF_MESSAGES"; // from API level 10

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		ErrorReporter.getInstance().check(this);

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

		final FragmentManager fm = getSupportFragmentManager();
		fm.beginTransaction().hide(fm.findFragmentById(R.id.wallet_addresses_fragment)).hide(fm.findFragmentById(R.id.exchange_rates_fragment))
				.commit();

		checkVersionAndTimeskewAlert();

		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		checkLowStorageAlert();
	}

	private void handleIntent(final Intent intent)
	{
		final Uri intentUri = intent.getData();
		final String scheme = intentUri != null ? intentUri.getScheme() : null;

		if (intentUri != null && "btctx".equals(scheme))
		{
			try
			{
				// decode transaction URI
				final String part = intentUri.getSchemeSpecificPart();
				final boolean useCompression = part.charAt(0) == 'Z';
				final byte[] bytes = Base43.decode(part.substring(1));

				InputStream is = new ByteArrayInputStream(bytes);
				if (useCompression)
					is = new GZIPInputStream(is);
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();

				final byte[] buf = new byte[4096];
				int read;
				while (-1 != (read = is.read(buf)))
					baos.write(buf, 0, read);
				baos.close();
				is.close();

				final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, baos.toByteArray());

				TransactionFragment.show(getSupportFragmentManager(), tx);
			}
			catch (final IOException x)
			{
				throw new RuntimeException(x);
			}
			catch (final ProtocolException x)
			{
				throw new RuntimeException(x);
			}
		}
		else if (Build.VERSION.SDK_INT >= GINGERBREAD_MR1 && Constants.MIMETYPE_TRANSACTION.equals(intent.getType()))
		{
			final Object ndefMessage = intent.getParcelableArrayExtra(EXTRA_NDEF_MESSAGES)[0];
			final byte[] payload = NfcTools.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

			try
			{
				final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, payload);

				TransactionFragment.show(getSupportFragmentManager(), tx);
			}
			catch (final ProtocolException x)
			{
				throw new RuntimeException(x);
			}
		}
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
					final int versionCode = ((de.schildbach.wallet.Application) getApplication()).versionCode();
					final URLConnection connection = new URL(Constants.VERSION_URL + "?current=" + versionCode).openConnection();
					connection.connect();
					final long serverTime = connection.getHeaderFieldDate("Date", 0);
					final InputStream is = connection.getInputStream();
					final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
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
}
