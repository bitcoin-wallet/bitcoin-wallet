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

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class ExchangeRatesFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	private WalletApplication application;
	private SharedPreferences prefs;
	private SimpleCursorAdapter adapter;

	private final WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{
		@Override
		public void onChange()
		{
			getActivity().runOnUiThread(new Runnable()
			{
				public void run()
				{
					updateView();
				}
			});
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = (WalletApplication) getActivity().getApplication();
		final Wallet wallet = application.getWallet();

		wallet.addEventListener(walletEventListener);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		setEmptyText(getString(R.string.exchange_rates_fragment_empty_text));

		adapter = new SimpleCursorAdapter(getActivity(), R.layout.exchange_rate_row, null, new String[] { ExchangeRatesProvider.KEY_CURRENCY_CODE,
				ExchangeRatesProvider.KEY_EXCHANGE_RATE }, new int[] { R.id.exchange_rate_currency_code, R.id.exchange_rate_value }, 0);
		adapter.setViewBinder(new ViewBinder()
		{
			public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex)
			{
				if (!ExchangeRatesProvider.KEY_EXCHANGE_RATE.equals(cursor.getColumnName(columnIndex)))
					return false;

				final BigInteger value = new BigDecimal(application.getWallet().getBalance(BalanceType.ESTIMATED)).multiply(
						new BigDecimal(cursor.getDouble(columnIndex))).toBigInteger();
				final CurrencyAmountView valueView = (CurrencyAmountView) view;
				valueView.setCurrencyCode(null);
				valueView.setAmount(value);

				return true;
			}
		});
		setListAdapter(adapter);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onDestroy()
	{
		application.getWallet().removeEventListener(walletEventListener);

		super.onDestroy();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final Cursor cursor = (Cursor) adapter.getItem(position);
		final String currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE));

		prefs.edit().putString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, currencyCode).commit();

		final WalletBalanceFragment walletBalanceFragment = (WalletBalanceFragment) getFragmentManager().findFragmentById(
				R.id.wallet_balance_fragment);
		if (walletBalanceFragment != null)
		{
			walletBalanceFragment.updateView();
			walletBalanceFragment.flashLocal();
		}
	}

	private void updateView()
	{
		final ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		return new CursorLoader(getActivity(), ExchangeRatesProvider.CONTENT_URI, null, null, null, null);
	}

	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		adapter.swapCursor(data);
	}

	public void onLoaderReset(final Loader<Cursor> loader)
	{
		adapter.swapCursor(null);
	}
}
