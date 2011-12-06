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

import java.text.DateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.util.Base64;
import de.schildbach.wallet.util.QrDialog;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class TransactionFragment extends DialogFragment
{
	public static final String FRAGMENT_TAG = TransactionFragment.class.getName();

	private FragmentActivity activity;
	private Transaction tx;

	private DateFormat dateFormat;
	private DateFormat timeFormat;

	private final static String KEY_TRANSACTION = "transaction";

	public static TransactionFragment instance(final Transaction tx)
	{
		final TransactionFragment fragment = new TransactionFragment();

		final Bundle args = new Bundle();
		args.putSerializable(KEY_TRANSACTION, tx);
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (FragmentActivity) activity;

		dateFormat = android.text.format.DateFormat.getDateFormat(activity);
		timeFormat = android.text.format.DateFormat.getTimeFormat(activity);
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Wallet wallet = ((Application) activity.getApplication()).getWallet();

		tx = (Transaction) getArguments().getSerializable(KEY_TRANSACTION);

		Address from = null;
		try
		{
			from = tx.getOutputs().get(0).getScriptPubKey().getToAddress();
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		Address to = null;
		try
		{
			to = tx.getInputs().get(0).getFromAddress();
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		final boolean sent = tx.sent(wallet);
		final boolean pending = wallet.isPending(tx);
		final boolean dead = wallet.isDead(tx);

		final ContentResolver contentResolver = activity.getContentResolver();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final Builder dialog = new AlertDialog.Builder(activity);

		final View view = inflater.inflate(R.layout.transaction_fragment, null);

		final TextView viewDate = (TextView) view.findViewById(R.id.transaction_fragment_time);
		final Date time = tx.getUpdateTime();
		if (time != null)
			viewDate.setText((DateUtils.isToday(time.getTime()) ? getString(R.string.transaction_fragment_time_today) : dateFormat.format(time))
					+ ", " + timeFormat.format(time));
		else
			view.findViewById(R.id.transaction_fragment_time_row).setVisibility(View.GONE);

		final TextView viewAmountLabel = (TextView) view.findViewById(R.id.transaction_fragment_amount_label);
		viewAmountLabel.setText(getString(sent ? R.string.transaction_fragment_amount_label_sent
				: R.string.transaction_fragment_amount_label_received));

		final TextView viewAmount = (TextView) view.findViewById(R.id.transaction_fragment_amount);
		try
		{
			viewAmount.setText((sent ? "-" : "+") + "\u2009" /* thin space */+ Utils.bitcoinValueToFriendlyString(tx.amount(wallet)));
		}
		catch (final ScriptException x)
		{
			throw new RuntimeException(x);
		}

		final TextView viewFrom = (TextView) view.findViewById(R.id.transaction_fragment_from);
		if (from != null)
		{
			final String label = AddressBookProvider.resolveLabel(contentResolver, from.toString());
			if (label != null)
			{
				viewFrom.setText(label);
			}
			else
			{
				viewFrom.setText(from.toString());
				viewFrom.setTypeface(Typeface.MONOSPACE);
			}
		}

		final TextView viewTo = (TextView) view.findViewById(R.id.transaction_fragment_to);
		if (to != null)
		{
			final String label = AddressBookProvider.resolveLabel(contentResolver, to.toString());
			if (label != null)
			{
				viewTo.setText(label);
			}
			else
			{
				viewTo.setText(to.toString());
				viewTo.setTypeface(Typeface.MONOSPACE);
			}
		}

		final TextView viewStatus = (TextView) view.findViewById(R.id.transaction_fragment_status);
		if (dead)
			viewStatus.setText(R.string.transaction_fragment_status_dead);
		else if (pending)
			viewStatus.setText(R.string.transaction_fragment_status_pending);
		else
			viewStatus.setText(R.string.transaction_fragment_status_confirmed);

		final TextView viewHash = (TextView) view.findViewById(R.id.transaction_fragment_hash);
		viewHash.setText(tx.getHash().toString());

		final ImageView viewQr = (ImageView) view.findViewById(R.id.transaction_fragment_qr);
		final byte[] serializedTx = tx.unsafeBitcoinSerialize();
		final String txStr = "btctx:" + Base64.encodeToString(serializedTx, Base64.NO_WRAP | Base64.NO_PADDING);
		final Bitmap qrCodeBitmap = WalletUtils.getQRCodeBitmap(txStr, 512);
		viewQr.setImageBitmap(qrCodeBitmap);
		viewQr.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				new QrDialog(activity, qrCodeBitmap).show();
			}
		});

		dialog.setView(view);

		return dialog.create();
	}
}
