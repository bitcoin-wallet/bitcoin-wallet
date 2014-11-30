/*
 * Copyright 2014 the original author or authors.
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

package biz.wiz.android.wallet.ui.send;

import javax.annotation.Nonnull;

import org.bitcoinj.core.VersionedChecksummedBytes;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import biz.wiz.android.wallet.ui.AbstractBindServiceActivity;
import biz.wiz.android.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class SweepWalletActivity extends AbstractBindServiceActivity
{
	public static final String INTENT_EXTRA_KEY = "sweep_key";

	public static void start(final Context context)
	{
		context.startActivity(new Intent(context, SweepWalletActivity.class));
	}

	public static void start(final Context context, @Nonnull final VersionedChecksummedBytes key)
	{
		final Intent intent = new Intent(context, SweepWalletActivity.class);
		intent.putExtra(INTENT_EXTRA_KEY, key);
		context.startActivity(intent);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.sweep_wallet_content);

		getWalletApplication().startBlockchainService(false);

		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}
}
