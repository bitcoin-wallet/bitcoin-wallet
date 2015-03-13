/*
 * Copyright 2015 the original author or authors.
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

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.protocols.payments.PaymentProtocol;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class FetchPaymentRequestActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_SENDER_NAME = "sender_name";
	public static final String INTENT_EXTRA_PAYMENT_REQUEST = "payment_request";

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Intent intent = getIntent();
		final String senderName = intent.getStringExtra(INTENT_EXTRA_SENDER_NAME);

		FetchPaymentRequestDialogFragment.showDialog(getFragmentManager(), senderName);
	}

	private CharSequence getCallingAppLabel()
	{
		final PackageManager packageManager = getPackageManager();
		final String callingPackage = getCallingPackage();

		try
		{
			return packageManager.getApplicationLabel(packageManager.getApplicationInfo(callingPackage, 0));
		}
		catch (PackageManager.NameNotFoundException x)
		{
			return null;
		}
	}

	private static class FetchPaymentRequestDialogFragment extends DialogFragment
	{
		private static final String FRAGMENT_TAG = FetchPaymentRequestDialogFragment.class.getName();

		public static void showDialog(final FragmentManager fm, final @Nullable String senderName)
		{
			final DialogFragment newFragment = new FetchPaymentRequestDialogFragment();

			final Bundle args = new Bundle();
			args.putString(INTENT_EXTRA_SENDER_NAME, senderName);
			newFragment.setArguments(args);

			newFragment.show(fm, FRAGMENT_TAG);
		}

		private FetchPaymentRequestActivity activity;
		private WalletApplication application;
		private Wallet wallet;

		@Override
		public void onAttach(final Activity activity)
		{
			super.onAttach(activity);

			this.activity = (FetchPaymentRequestActivity) activity;
			this.application = (WalletApplication) activity.getApplication();
			this.wallet = application.getWallet();
		}

		@Override
		public Dialog onCreateDialog(final Bundle savedInstanceState)
		{
			final Bundle args = getArguments();
			final String senderName = args.getString(INTENT_EXTRA_SENDER_NAME);

			final StringBuilder message = new StringBuilder(senderName != null ? senderName : "Someone");
			message.append(" wants to pay you. Do you want to disclose payment details?");
			final CharSequence callingAppLabel = activity.getCallingAppLabel();
			if (callingAppLabel != null)
				message.append("\n\nRequesting app: " + callingAppLabel);

			final DialogBuilder dialog = new DialogBuilder(activity);
			dialog.setMessage(message);
			dialog.setPositiveButton("Disclose", new OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int which)
				{
					final Address address = wallet.freshReceiveAddress();
					final String memo = senderName != null ? "Payment for " + senderName : "Anonymous payment";
					final byte[] paymentRequest = PaymentProtocol.createPaymentRequest(Constants.NETWORK_PARAMETERS, null, address, memo, null, null)
							.build().toByteArray();

					final Intent result = new Intent();
					result.putExtra(INTENT_EXTRA_PAYMENT_REQUEST, paymentRequest);
					activity.setResult(Activity.RESULT_OK, result);
					activity.finish();

					application.startBlockchainService(false);
				}
			});
			dialog.setNegativeButton(R.string.button_dismiss, new OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int which)
				{
					activity.setResult(Activity.RESULT_CANCELED);
					activity.finish();
				}
			});

			return dialog.create();
		}
	}
}
