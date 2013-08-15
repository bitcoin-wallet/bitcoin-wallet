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

import java.math.BigInteger;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsActivity extends AbstractBindServiceActivity
{
	private static final String INTENT_EXTRA_ADDRESS = "address";
	private static final String INTENT_EXTRA_ADDRESS_LABEL = "address_label";
	private static final String INTENT_EXTRA_AMOUNT = "amount";

	public static void start(final Context context, final String address, final String addressLabel, final BigInteger amount)
	{
		final Intent intent = new Intent(context, SendCoinsActivity.class);
		intent.putExtra(INTENT_EXTRA_ADDRESS, address);
		intent.putExtra(INTENT_EXTRA_ADDRESS_LABEL, addressLabel);
		intent.putExtra(INTENT_EXTRA_AMOUNT, amount);
		context.startActivity(intent);
	}

	public static void start(final Context context, final String uri)
	{
		if (uri.matches("[a-zA-Z0-9]*"))
		{
			start(context, uri, null, null);
		}
		else
		{
			try
			{
				final BitcoinURI bitcoinUri = new BitcoinURI(null, uri);
				final Address address = bitcoinUri.getAddress();
				final String addressLabel = bitcoinUri.getLabel();
				final BigInteger amount = bitcoinUri.getAmount();

				start(context, address != null ? address.toString() : null, addressLabel, amount);
			}
			catch (final BitcoinURIParseException x)
			{
				final Builder dialog = new AlertDialog.Builder(context);
				dialog.setTitle(R.string.send_coins_uri_parse_error_title);
				dialog.setMessage(uri);
				dialog.setNeutralButton(R.string.button_dismiss, null);
				dialog.show();
			}
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.send_coins_content);

		getWalletApplication().startBlockchainService(false);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		getSupportMenuInflater().inflate(R.menu.send_coins_activity_options, menu);

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

			case R.id.send_coins_options_help:
				HelpDialogFragment.page(getSupportFragmentManager(), "help_send_coins");
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleIntent(final Intent intent)
	{
		final String action = intent.getAction();
		final Uri intentUri = intent.getData();
		final String scheme = intentUri != null ? intentUri.getScheme() : null;

		final String address;
		final String addressLabel;
		final BigInteger amount;

		if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && intentUri != null && "bitcoin".equals(scheme))
		{
			try
			{
				final BitcoinURI bitcoinUri = new BitcoinURI(null, intentUri.toString());
				address = bitcoinUri.getAddress().toString();
				addressLabel = bitcoinUri.getLabel();
				amount = bitcoinUri.getAmount();
			}
			catch (final BitcoinURIParseException x)
			{
				parseErrorDialog(intentUri.toString());
				return;
			}
		}
		else if (intent.hasExtra(INTENT_EXTRA_ADDRESS))
		{
			address = intent.getStringExtra(INTENT_EXTRA_ADDRESS);
			addressLabel = intent.getStringExtra(INTENT_EXTRA_ADDRESS_LABEL);
			amount = (BigInteger) intent.getSerializableExtra(INTENT_EXTRA_AMOUNT);
		}
		else
		{
			return;
		}

		if (address != null || amount != null)
			updateSendCoinsFragment(address, addressLabel, amount);
		else
			longToast(R.string.send_coins_parse_address_error_msg);
	}

	private void updateSendCoinsFragment(final String receivingAddress, final String receivingLabel, final BigInteger amount)
	{
		final SendCoinsFragment sendCoinsFragment = (SendCoinsFragment) getSupportFragmentManager().findFragmentById(R.id.send_coins_fragment);

		sendCoinsFragment.update(receivingAddress, receivingLabel, amount);
	}
}
