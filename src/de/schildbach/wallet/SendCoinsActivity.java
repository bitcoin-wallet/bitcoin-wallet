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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class SendCoinsActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_ADDRESS = "address";

	private static final int DIALOG_HELP = 0;

	private static final Intent zxingIntent = new Intent().setClassName("com.google.zxing.client.android",
			"com.google.zxing.client.android.CaptureActivity");
	private static final Intent gogglesIntent = new Intent().setClassName("com.google.android.apps.unveil",
			"com.google.android.apps.unveil.CaptureActivity");

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.send_coins_content);

		final ActionBarFragment actionBar = (ActionBarFragment) getSupportFragmentManager().findFragmentById(R.id.action_bar_fragment);
		actionBar.setIcon(Constants.APP_ICON_RESID);
		actionBar.setPrimaryTitle(R.string.send_coins_activity_title);
		actionBar.setSecondaryTitle(Constants.TEST ? "[testnet!]" : null);

		actionBar.addButton(R.drawable.ic_menu_qr).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final PackageManager pm = getPackageManager();
				if (pm.resolveActivity(zxingIntent, 0) != null)
					startActivity(zxingIntent);
				else if (pm.resolveActivity(gogglesIntent, 0) != null)
					startActivity(gogglesIntent);
				else
					longToast(R.string.send_coins_install_qr_scanner_msg);
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
		webView.loadUrl("file:///android_asset/help_send_coins.html");

		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	private Pattern P_BITCOIN_URI = Pattern.compile("([a-zA-Z0-9]*)(?:\\?amount=(.*))?");

	private void handleIntent(final Intent intent)
	{
		final SendCoinsFragment sendCoinsFragment = (SendCoinsFragment) getSupportFragmentManager().findFragmentById(R.id.send_coins_fragment);

		final Uri intentUri = intent.getData();
		if (intentUri != null && "bitcoin".equals(intentUri.getScheme()))
		{
			final Matcher m = P_BITCOIN_URI.matcher(intentUri.getSchemeSpecificPart());
			if (m.matches())
			{
				sendCoinsFragment.update(m.group(1), m.group(2));
			}
		}
		else if (intent.hasExtra(INTENT_EXTRA_ADDRESS))
		{
			final String address = intent.getExtras().getString(INTENT_EXTRA_ADDRESS);
			sendCoinsFragment.update(address, null);
		}
	}
}
