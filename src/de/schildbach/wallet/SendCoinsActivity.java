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

import java.math.BigInteger;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;

import com.google.bitcoin.core.Address;

import de.schildbach.wallet.util.ActionBarFragment;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class SendCoinsActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_ADDRESS = "address";

	private static final int DIALOG_HELP = 0;

	private static final Intent zxingIntent = new Intent("com.google.zxing.client.android.SCAN").putExtra("SCAN_MODE", "QR_CODE_MODE");
	private static final Intent gogglesIntent = new Intent().setClassName("com.google.android.apps.unveil",
			"com.google.android.apps.unveil.CaptureActivity");

	private static final int REQUEST_CODE_SCAN = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.send_coins_content);

		final ActionBarFragment actionBar = getActionBar();
		actionBar.setPrimaryTitle(R.string.send_coins_activity_title);

		actionBar.addButton(R.drawable.ic_menu_qr).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final PackageManager pm = getPackageManager();
				if (pm.resolveActivity(zxingIntent, 0) != null)
				{
					startActivityForResult(zxingIntent, REQUEST_CODE_SCAN);
				}
				else if (pm.resolveActivity(gogglesIntent, 0) != null)
				{
					startActivity(gogglesIntent);
				}
				else
				{
					showMarketPage(Constants.PACKAGE_NAME_ZXING);
					longToast(R.string.send_coins_install_qr_scanner_msg);
				}
			}
		});
		actionBar.addButton(R.drawable.ic_menu_help).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showDialog(DIALOG_HELP);
			}
		});

		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final WebView webView = new WebView(this);
		webView.loadUrl("file:///android_asset/help_send_coins" + languagePrefix() + ".html");

		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK && "QR_CODE".equals(intent.getStringExtra("SCAN_RESULT_FORMAT")))
		{
			final String contents = intent.getStringExtra("SCAN_RESULT");
			if (contents.matches("[a-zA-Z0-9]*"))
			{
				updateSendCoinsFragment(contents, null);
			}
			else
			{
				try
				{
					final BitcoinURI bitcoinUri = new BitcoinURI(contents);
					final Address address = bitcoinUri.getAddress();
					updateSendCoinsFragment(address != null ? address.toString() : null, bitcoinUri.getAmount());
				}
				catch (final BitcoinURI.ParseException x)
				{
					parseErrorDialog(contents);
				}
			}
		}
	}

	private void handleIntent(final Intent intent)
	{
		final Uri intentUri = intent.getData();
		final String scheme = intentUri != null ? intentUri.getScheme() : null;

		if (intentUri != null && "bitcoin".equals(scheme))
		{
			try
			{
				final BitcoinURI bitcoinUri = new BitcoinURI(intentUri);
				final Address address = bitcoinUri.getAddress();
				updateSendCoinsFragment(address != null ? address.toString() : null, bitcoinUri.getAmount());
			}
			catch (final BitcoinURI.ParseException x)
			{
				parseErrorDialog(intentUri.toString());
			}
		}
		else if (intent.hasExtra(INTENT_EXTRA_ADDRESS))
		{
			final String address = intent.getExtras().getString(INTENT_EXTRA_ADDRESS);
			updateSendCoinsFragment(address, null);
		}
	}

	private void updateSendCoinsFragment(final String address, final BigInteger amount)
	{
		final SendCoinsFragment sendCoinsFragment = (SendCoinsFragment) getSupportFragmentManager().findFragmentById(R.id.send_coins_fragment);

		sendCoinsFragment.update(address, amount);
	}

	private void parseErrorDialog(final String uri)
	{
		final Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle(R.string.send_coins_uri_parse_error_title);
		dialog.setMessage(uri);
		dialog.setNeutralButton(R.string.send_coins_uri_parse_error_dismiss, null);
		dialog.show();
	}
}
