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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class AmountCalculatorFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	public static interface Listener
	{
		void use(final BigInteger amount);
	}

	public static final String FRAGMENT_TAG = AmountCalculatorFragment.class.getName();

	private LayoutInflater inflater;
	private String exchangeCurrency;
	private Double exchangeRate;
	private boolean exchangeDirection = true;
	private CurrencyAmountView btcAmountView, localAmountView;
	private Listener listener;

	public AmountCalculatorFragment(final Listener listener)
	{
		this.listener = listener;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final FragmentActivity activity = getActivity();
		inflater = LayoutInflater.from(activity);
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);

		final Builder dialog = new AlertDialog.Builder(activity).setTitle(R.string.amount_calculator_dialog_title);

		final View view = inflater.inflate(R.layout.amount_calculator_dialog, null);

		btcAmountView = (CurrencyAmountView) view.findViewById(R.id.amount_calculator_row_btc);
		btcAmountView.setListener(new CurrencyAmountView.Listener()
		{
			public void changed()
			{
				if (btcAmountView.getAmount() != null)
				{
					exchangeDirection = true;

					updateAppearance();
				}
				else
				{
					localAmountView.setHint(null);
				}
			}

			public void done()
			{
				AmountCalculatorFragment.this.done();
			}
		});

		localAmountView = (CurrencyAmountView) view.findViewById(R.id.amount_calculator_row_local);
		localAmountView.setCurrencyCode(exchangeCurrency);
		localAmountView.setListener(new CurrencyAmountView.Listener()
		{
			public void changed()
			{
				if (localAmountView.getAmount() != null)
				{
					exchangeDirection = false;

					updateAppearance();
				}
				else
				{
					btcAmountView.setHint(null);
				}
			}

			public void done()
			{
				AmountCalculatorFragment.this.done();
			}
		});

		dialog.setView(view);

		dialog.setPositiveButton(R.string.amount_calculator_dialog_button_use, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int whichButton)
			{
				done();
			}
		});
		dialog.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int whichButton)
			{
				dismiss();
			}
		});

		updateAppearance();

		getLoaderManager().initLoader(0, null, this);

		return dialog.create();
	}

	private void updateAppearance()
	{
		if (exchangeRate != null)
		{
			localAmountView.setEnabled(true);

			final BigDecimal bdExchangeRate = new BigDecimal(exchangeRate);

			if (exchangeDirection)
			{
				final BigInteger btcAmount = btcAmountView.getAmount();
				if (btcAmount != null)
				{
					localAmountView.setAmount(null);
					localAmountView.setHint(new BigDecimal(btcAmount).multiply(bdExchangeRate).toBigInteger());
					btcAmountView.setHint(null);
				}
			}
			else
			{
				final BigInteger localAmount = localAmountView.getAmount();
				if (localAmount != null)
				{
					btcAmountView.setAmount(null);
					btcAmountView.setHint(new BigDecimal(localAmount).divide(bdExchangeRate, RoundingMode.HALF_UP).toBigInteger());
					localAmountView.setHint(null);
				}
			}
		}
		else
		{
			localAmountView.setEnabled(false);
		}
	}

	private void done()
	{
		final BigInteger amount = exchangeDirection ? btcAmountView.getAmount() : new BigDecimal(localAmountView.getAmount()).divide(
				new BigDecimal(exchangeRate), RoundingMode.HALF_UP).toBigInteger();

		listener.use(amount);

		dismiss();
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		return new CursorLoader(getActivity(), ExchangeRatesProvider.CONTENT_URI, null, ExchangeRatesProvider.KEY_CURRENCY_CODE,
				new String[] { exchangeCurrency }, null);
	}

	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		if (data != null)
		{
			data.moveToFirst();
			exchangeRate = data.getDouble(data.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_EXCHANGE_RATE));

			updateAppearance();
		}
	}

	public void onLoaderReset(final Loader<Cursor> loader)
	{
	}
}
