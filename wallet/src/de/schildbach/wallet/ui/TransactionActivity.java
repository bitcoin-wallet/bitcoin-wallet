/*
 * Copyright 2011-2012 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.ActionBarFragment;
import de.schildbach.wallet.util.Base43;
import de.schildbach.wallet.util.NfcTools;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class TransactionActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_TRANSACTION = "transaction";

	private static final int GINGERBREAD_MR1 = 10; // API level 10
	private static final String EXTRA_NDEF_MESSAGES = "android.nfc.extra.NDEF_MESSAGES"; // API level 10

	private Object nfcManager;
	private Transaction tx;

	public static void show(final Context context, final Transaction tx)
	{
		final Intent intent = new Intent(context, TransactionActivity.class);
		// use Bitcoin serialization, because Java serialization runs out of stack on some transactions
		intent.putExtra(TransactionActivity.INTENT_EXTRA_TRANSACTION, tx.unsafeBitcoinSerialize());
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		nfcManager = getSystemService(Context.NFC_SERVICE);

		setContentView(R.layout.transaction_content);

		final ActionBarFragment actionBar = getActionBar();

		actionBar.setPrimaryTitle(R.string.transaction_activity_title);

		actionBar.setBack(new OnClickListener()
		{
			public void onClick(final View v)
			{
				finish();
			}
		});

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
		if (nfcManager != null)
			NfcTools.unpublish(nfcManager, this);

		super.onPause();
	}

	private void handleIntent(final Intent intent)
	{
		final Uri intentUri = intent.getData();
		final String scheme = intentUri != null ? intentUri.getScheme() : null;

		if (intent.hasExtra(INTENT_EXTRA_TRANSACTION))
		{
			try
			{
				tx = new Transaction(Constants.NETWORK_PARAMETERS, getIntent().getByteArrayExtra(INTENT_EXTRA_TRANSACTION));
			}
			catch (final ProtocolException x)
			{
				throw new RuntimeException(x);
			}
		}
		else if (intentUri != null && "btctx".equals(scheme))
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
		else if (Build.VERSION.SDK_INT >= GINGERBREAD_MR1 && Constants.MIMETYPE_TRANSACTION.equals(intent.getType()))
		{
			final Object ndefMessage = intent.getParcelableArrayExtra(EXTRA_NDEF_MESSAGES)[0];
			final byte[] payload = NfcTools.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

			try
			{
				tx = new Transaction(Constants.NETWORK_PARAMETERS, payload);

				processPendingTransaction(tx);
			}
			catch (final ProtocolException x)
			{
				throw new RuntimeException(x);
			}
		}

		if (tx == null)
			throw new IllegalArgumentException("no tx");
	}

	private void updateView()
	{
		final TransactionFragment transactionFragment = (TransactionFragment) getSupportFragmentManager().findFragmentById(R.id.transaction_fragment);

		transactionFragment.update(tx);

		if (nfcManager != null)
			NfcTools.publishMimeObject(nfcManager, this, Constants.MIMETYPE_TRANSACTION, tx.unsafeBitcoinSerialize(), false);
	}

	private void processPendingTransaction(final Transaction tx)
	{
		final Wallet wallet = ((WalletApplication) getApplication()).getWallet();

		try
		{
			wallet.receivePending(tx);
		}
		catch (final VerificationException x)
		{
			throw new RuntimeException(x);
		}
		catch (final ScriptException x)
		{
			throw new RuntimeException(x);
		}
	}
}
