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

import java.math.BigDecimal;
import java.math.BigInteger;

import android.app.Activity;
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

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class ExchangeRatesFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private SharedPreferences prefs;
	private SimpleCursorAdapter adapter;
	private BigInteger balance;

	private final WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{
		@Override
		public void onTransactionConfidenceChanged(final Wallet wallet, final Transaction tx)
		{
			// swallow
		}

		@Override
		public void onChange()
		{
			activity.runOnUiThread(new Runnable()
			{
				public void run()
				{
					updateView();
				}
			});
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		application = (WalletApplication) activity.getApplication();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Wallet wallet = application.getWallet();
		wallet.addEventListener(walletEventListener);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(activity);

		setEmptyText(getString(R.string.exchange_rates_fragment_empty_text));

		adapter = new SimpleCursorAdapter(activity, R.layout.exchange_rate_row, null, new String[] { ExchangeRatesProvider.KEY_CURRENCY_CODE,
				ExchangeRatesProvider.KEY_EXCHANGE_RATE }, new int[] { R.id.exchange_rate_currency_code, R.id.exchange_rate_value }, 0);
		adapter.setViewBinder(new ViewBinder()
		{
			public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex)
			{
				if (!ExchangeRatesProvider.KEY_EXCHANGE_RATE.equals(cursor.getColumnName(columnIndex)))
					return false;

				final BigDecimal exchangeRate = new BigDecimal(cursor.getDouble(columnIndex));

				final CurrencyAmountView valueView = (CurrencyAmountView) view;
				valueView.setCurrencyCode(null);
				final BigInteger localValue = WalletUtils.localValue(balance, exchangeRate);
				valueView.setAmount(localValue);

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

		activity.startActionMode(new ActionMode.Callback()
		{
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.exchange_rates_context, menu);

				return true;
			}

			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				mode.setTitle(currencyCode);

				return true;
			}

			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.exchange_rates_context_set_as_default:
						handleSetAsDefault(currencyCode);

						mode.finish();
						return true;
				}

				return false;
			}

			public void onDestroyActionMode(final ActionMode mode)
			{
			}

			private void handleSetAsDefault(final String currencyCode)
			{
				prefs.edit().putString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, currencyCode).commit();

				final WalletBalanceFragment walletBalanceFragment = (WalletBalanceFragment) getFragmentManager().findFragmentById(
						R.id.wallet_balance_fragment);
				if (walletBalanceFragment != null)
				{
					walletBalanceFragment.updateView();
					walletBalanceFragment.flashLocal();
				}
			}
		});
	}

	private void updateView()
	{
		balance = application.getWallet().getBalance(BalanceType.ESTIMATED);

		final ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		return new CursorLoader(activity, ExchangeRatesProvider.CONTENT_URI, null, null, null, null);
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
