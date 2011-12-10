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
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
public class TransactionFragment extends Fragment
{
	public static final String FRAGMENT_TAG = TransactionFragment.class.getName();

	private FragmentActivity activity;

	private DateFormat dateFormat;
	private DateFormat timeFormat;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (FragmentActivity) activity;

		dateFormat = android.text.format.DateFormat.getDateFormat(activity);
		timeFormat = android.text.format.DateFormat.getTimeFormat(activity);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.transaction_fragment, null);
	}

	public void update(final Transaction tx)
	{
		final Wallet wallet = ((Application) activity.getApplication()).getWallet();

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

		final View view = getView();

		final Date time = tx.getUpdateTime();
		view.findViewById(R.id.transaction_fragment_time_row).setVisibility(time != null ? View.VISIBLE : View.GONE);
		if (time != null)
		{
			final TextView viewDate = (TextView) view.findViewById(R.id.transaction_fragment_time);
			viewDate.setText((DateUtils.isToday(time.getTime()) ? getString(R.string.transaction_fragment_time_today) : dateFormat.format(time))
					+ ", " + timeFormat.format(time));
		}

		try
		{
			final BigInteger amountSent = tx.getValueSentFromMe(wallet);
			view.findViewById(R.id.transaction_fragment_amount_sent_row).setVisibility(amountSent.signum() != 0 ? View.VISIBLE : View.GONE);
			if (amountSent.signum() != 0)
			{
				final TextView viewAmountSent = (TextView) view.findViewById(R.id.transaction_fragment_amount_sent);
				viewAmountSent.setText("-\u2009" /* thin space */+ Utils.bitcoinValueToFriendlyString(amountSent));
			}
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		final BigInteger amountReceived = tx.getValueSentToMe(wallet);
		view.findViewById(R.id.transaction_fragment_amount_received_row).setVisibility(amountReceived.signum() != 0 ? View.VISIBLE : View.GONE);
		if (amountReceived.signum() != 0)
		{
			final TextView viewAmountReceived = (TextView) view.findViewById(R.id.transaction_fragment_amount_received);
			viewAmountReceived.setText("+\u2009" /* thin space */+ Utils.bitcoinValueToFriendlyString(amountReceived));
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
		else
		{
			viewFrom.setText(null);
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
		else
		{
			viewTo.setText(null);
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
			// encode transaction URI
			final ByteArrayOutputStream bos = new ByteArrayOutputStream(serializedTx.length);
			final GZIPOutputStream gos = new GZIPOutputStream(bos);
			gos.write(serializedTx);
			gos.close();

			final byte[] gzippedSerializedTx = bos.toByteArray();
			final boolean useCompressioon = gzippedSerializedTx.length < serializedTx.length;

			final StringBuilder txStr = new StringBuilder("btctx:");
			txStr.append(useCompressioon ? 'Z' : '-');
			txStr.append(Base43.encode(useCompressioon ? gzippedSerializedTx : serializedTx));

			final Bitmap qrCodeBitmap = WalletUtils.getQRCodeBitmap(txStr.toString().toUpperCase(), 512);
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
	}
}
