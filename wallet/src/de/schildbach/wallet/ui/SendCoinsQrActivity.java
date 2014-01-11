/*
 * Copyright 2013-2014 the original author or authors.
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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.InputParser.StringInputParser;

import javax.annotation.Nonnull;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class SendCoinsQrActivity extends AbstractOnDemandServiceActivity
{
	private static final int REQUEST_CODE_SCAN = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK)
		{
			final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

			new StringInputParser(input)
			{
				@Override
				protected void bitcoinRequest(@Nonnull final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
				{
					SendCoinsActivity
							.start(SendCoinsQrActivity.this, address.toString(), addressLabel, amount, bluetoothMac);

					SendCoinsQrActivity.this.finish();
				}

				@Override
				protected void directTransaction(@Nonnull final Transaction transaction)
				{
					processDirectTransaction(transaction);

					SendCoinsQrActivity.this.finish();
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(SendCoinsQrActivity.this, dismissListener, 0, messageResId, messageArgs);
				}

                @Override
                protected void handlePrivateKey(@Nonnull ECKey key) {
                    final Address address = new Address(Constants.NETWORK_PARAMETERS, key.getPubKeyHash());
                    bitcoinRequest(address, null, null, null);
                }

                private final OnClickListener dismissListener = new OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						SendCoinsQrActivity.this.finish();
					}
				};
			}.parse();
		}
		else
		{
			finish();
		}
	}
}
