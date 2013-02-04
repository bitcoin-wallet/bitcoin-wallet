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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.ResourceCursorAdapter;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

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
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class ExchangeRatesFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>, OnSharedPreferenceChangeListener
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private SharedPreferences prefs;
	private CursorAdapter adapter;
	private BigInteger balance;
	private String defaultCurrency;

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
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);
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

		setEmptyText(getString(R.string.exchange_rates_fragment_empty_text));

		adapter = new ResourceCursorAdapter(activity, R.layout.exchange_rate_row, null, true)
		{
			@Override
			public void bindView(final View view, final Context context, final Cursor cursor)
			{
				final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);

				final TextView currencyCodeView = (TextView) view.findViewById(R.id.exchange_rate_currency_code);
				final boolean isDefaultCurrency = exchangeRate.currencyCode.equals(defaultCurrency);
				currencyCodeView.setText(exchangeRate.currencyCode
						+ (isDefaultCurrency ? " (" + getText(R.string.exchange_rates_fragment_default) + ")" : ""));
				currencyCodeView.setTypeface(isDefaultCurrency ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

				final CurrencyAmountView valueView = (CurrencyAmountView) view.findViewById(R.id.exchange_rate_value);
				final BigDecimal bdRate = new BigDecimal(exchangeRate.rate);
				valueView.setCurrencyCode(null);
				final BigInteger localValue = WalletUtils.localValue(balance, bdRate);
				valueView.setAmount(localValue);
			}
		};
		setListAdapter(adapter);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		defaultCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);
		prefs.registerOnSharedPreferenceChangeListener(this);

		updateView();
	}

	@Override
	public void onPause()
	{
		prefs.unregisterOnSharedPreferenceChangeListener(this);

		super.onPause();
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
		final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);

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
				mode.setTitle(exchangeRate.currencyCode);
				mode.setSubtitle(getString(R.string.exchange_rates_fragment_source, exchangeRate.source));

				return true;
			}

			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.exchange_rates_context_set_as_default:
						handleSetAsDefault(exchangeRate.currencyCode);

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

	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	{
		if (Constants.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
		{
			defaultCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);
			updateView();
		}
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
