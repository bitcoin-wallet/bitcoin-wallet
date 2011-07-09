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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class SendCoinsActivity extends AbstractWalletActivity
{
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
		actionBar.setPrimaryTitle("Send Bitcoins");
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
					longToast("Please install Goggles or Zxing QR-code scanner!");
			}
		});

		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	private Pattern P_BITCOIN_URI = Pattern.compile("([a-zA-Z0-9]*)(?:\\?amount=(.*))?");

	private void handleIntent(final Intent intent)
	{
		final Uri intentUri = intent.getData();
		if (intentUri != null && "bitcoin".equals(intentUri.getScheme()))
		{
			final Matcher m = P_BITCOIN_URI.matcher(intentUri.getSchemeSpecificPart());
			if (m.matches())
			{
				((SendCoinsFragment) getSupportFragmentManager().findFragmentById(R.id.send_coins_fragment)).update(m.group(1), m.group(2));
			}
		}
	}
}
