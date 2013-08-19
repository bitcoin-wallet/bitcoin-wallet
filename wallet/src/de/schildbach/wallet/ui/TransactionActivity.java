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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class TransactionActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_TRANSACTION_HASH = "transaction_hash";

	private Transaction tx;

	public static void show(final Context context, final Transaction tx)
	{
		final Intent intent = new Intent(context, TransactionActivity.class);
		intent.putExtra(TransactionActivity.INTENT_EXTRA_TRANSACTION_HASH, tx.getHash());
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.transaction_content);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		final Wallet wallet = ((WalletApplication) getApplication()).getWallet();
		tx = wallet.getTransaction((Sha256Hash) getIntent().getSerializableExtra(INTENT_EXTRA_TRANSACTION_HASH));
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		updateView();
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

	private void updateView()
	{
		final TransactionFragment transactionFragment = (TransactionFragment) getSupportFragmentManager().findFragmentById(R.id.transaction_fragment);

		transactionFragment.update(tx);
	}
}
