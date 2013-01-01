/*
 * Copyright 2011-2013 the original author or authors.
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

import android.app.Dialog;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class RequestCoinsActivity extends AbstractWalletActivity
{
	private static final int DIALOG_HELP = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.request_coins_content);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setTitle(R.string.request_coins_activity_title);
		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		getSupportMenuInflater().inflate(R.menu.request_coins_activity_options, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;

			case R.id.request_coins_options_help:
				showDialog(DIALOG_HELP);
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final WebView webView = new WebView(this);
		webView.loadUrl("file:///android_asset/help_request_coins" + languagePrefix() + ".html");

		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}
}
