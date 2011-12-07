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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DateFormat;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

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

import de.schildbach.wallet.util.Base43;
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
		final byte[] serializedTx = tx.unsafeBitcoinSerialize();

		Address from = null;
		boolean fromMine = false;
		try
		{
			from = tx.getInputs().get(0).getFromAddress();
			fromMine = wallet.isPubKeyHashMine(from.getHash160());
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		Address to = null;
		boolean toMine = false;
		try
		{
			to = tx.getOutputs().get(0).getScriptPubKey().getToAddress();
			toMine = wallet.isPubKeyHashMine(to.getHash160());
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		final boolean pending = wallet.isPending(tx);
		final boolean dead = wallet.isDead(tx);

		final ContentResolver contentResolver = activity.getContentResolver();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final Builder dialog = new AlertDialog.Builder(activity);

		final View view = inflater.inflate(R.layout.transaction_fragment, null);

		final Date time = tx.getUpdateTime();
		if (time != null)
		{
			final TextView viewDate = (TextView) view.findViewById(R.id.transaction_fragment_time);
			viewDate.setText((DateUtils.isToday(time.getTime()) ? getString(R.string.transaction_fragment_time_today) : dateFormat.format(time))
					+ ", " + timeFormat.format(time));
		}
		else
		{
			view.findViewById(R.id.transaction_fragment_time_row).setVisibility(View.GONE);
		}

		try
		{
			final BigInteger amountSent = tx.getValueSentFromMe(wallet);
			if (!amountSent.equals(BigInteger.ZERO))
			{
				final TextView viewAmountSent = (TextView) view.findViewById(R.id.transaction_fragment_amount_sent);
				viewAmountSent.setText("-\u2009" /* thin space */+ Utils.bitcoinValueToFriendlyString(amountSent));
			}
			else
			{
				view.findViewById(R.id.transaction_fragment_amount_sent_row).setVisibility(View.GONE);
			}
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		final BigInteger amountReceived = tx.getValueSentToMe(wallet);
		if (!amountReceived.equals(BigInteger.ZERO))
		{
			final TextView viewAmountReceived = (TextView) view.findViewById(R.id.transaction_fragment_amount_received);
			viewAmountReceived.setText("+\u2009" /* thin space */+ Utils.bitcoinValueToFriendlyString(amountReceived));
		}
		else
		{
			view.findViewById(R.id.transaction_fragment_amount_received_row).setVisibility(View.GONE);
		}

		final TextView viewFrom = (TextView) view.findViewById(R.id.transaction_fragment_from);
		if (from != null)
		{
			final String label = AddressBookProvider.resolveLabel(contentResolver, from.toString());
			final StringBuilder builder = new StringBuilder();

			if (fromMine)
				builder.append(getString(R.string.transaction_fragment_you)).append(", ");

			if (label != null)
			{
				builder.append(label);
			}
			else
			{
				builder.append(from.toString());
				viewFrom.setTypeface(Typeface.MONOSPACE);
			}

			viewFrom.setText(builder.toString());
		}

		final TextView viewTo = (TextView) view.findViewById(R.id.transaction_fragment_to);
		if (to != null)
		{
			final String label = AddressBookProvider.resolveLabel(contentResolver, to.toString());
			final StringBuilder builder = new StringBuilder();

			if (toMine)
				builder.append(getString(R.string.transaction_fragment_you)).append(", ");

			if (label != null)
			{
				builder.append(label);
			}
			else
			{
				builder.append(to.toString());
				viewTo.setTypeface(Typeface.MONOSPACE);
			}

			viewTo.setText(builder.toString());
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

		final TextView viewLength = (TextView) view.findViewById(R.id.transaction_fragment_length);
		viewLength.setText(Integer.toString(serializedTx.length));

		final ImageView viewQr = (ImageView) view.findViewById(R.id.transaction_fragment_qr);

		try
		{
			final ByteArrayOutputStream bos = new ByteArrayOutputStream(serializedTx.length);
			final GZIPOutputStream gos = new GZIPOutputStream(bos);
			gos.write(serializedTx);
			gos.close();

			final byte[] gzippedSerializedTx = bos.toByteArray();

			final String txStr = ("btctx:" + Base43.encode(gzippedSerializedTx)).toUpperCase();
			final Bitmap qrCodeBitmap = WalletUtils.getQRCodeBitmap(txStr, 512);
			viewQr.setImageBitmap(qrCodeBitmap);
			viewQr.setOnClickListener(new OnClickListener()
			{
				public void onClick(final View v)
				{
					new QrDialog(activity, qrCodeBitmap).show();
				}
			});
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}

		dialog.setView(view);

		return dialog.create();
	}
}
