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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.*;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Base43;
import de.schildbach.wallet_ltc.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class TransactionActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_TRANSACTION_HASH = "transaction_hash";

	private static final String EXTRA_NDEF_MESSAGES = "android.nfc.extra.NDEF_MESSAGES"; // API level 10

	private Object nfcManager;
	private Transaction tx;

	public static void show(final Context context, final Transaction tx)
	{
		final Intent intent = new Intent(context, TransactionActivity.class);
		intent.putExtra(TransactionActivity.INTENT_EXTRA_TRANSACTION_HASH, tx.getHash());
		context.startActivity(intent);
	}

	@SuppressLint("InlinedApi")
	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		nfcManager = getSystemService(Context.NFC_SERVICE);

		setContentView(R.layout.transaction_content);


		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		handleIntent(getIntent());
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	private void handleIntent(final Intent intent)
	{
		final Uri intentUri = intent.getData();
		final String scheme = intentUri != null ? intentUri.getScheme() : null;

		if (intent.hasExtra(INTENT_EXTRA_TRANSACTION_HASH))
		{
			final Wallet wallet = ((WalletApplication) getApplication()).getWallet();
			tx = wallet.getTransaction((Sha256Hash) intent.getSerializableExtra(INTENT_EXTRA_TRANSACTION_HASH));
		}
		else if (intentUri != null && "ltctx".equals(scheme))
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

				tx = new Transaction(Constants.NETWORK_PARAMETERS, baos.toByteArray());

				processPendingTransaction(tx);
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

		if (tx == null)
			throw new IllegalArgumentException("no tx");
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

	private void processPendingTransaction(final Transaction tx)
	{
		final Wallet wallet = ((WalletApplication) getApplication()).getWallet();

		try
		{
			if (wallet.isTransactionRelevant(tx))
				// TODO dependent transactions
				wallet.receivePending(tx, null);
		}
		catch (final VerificationException x)
		{
			throw new RuntimeException(x);
		}
	}
}
