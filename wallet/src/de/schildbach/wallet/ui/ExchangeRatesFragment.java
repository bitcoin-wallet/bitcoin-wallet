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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
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
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.ThrottelingWalletChangeListener;
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
	private LoaderManager loaderManager;

	private CursorAdapter adapter;
	private BigInteger balance;
	private String defaultCurrency;

	private final ThrottelingWalletChangeListener walletChangeListener = new ThrottelingWalletChangeListener()
	{
		@Override
		public void onThrotteledWalletChanged()
		{
			updateView();
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.loaderManager = getLoaderManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Wallet wallet = application.getWallet();
		wallet.addEventListener(walletChangeListener);
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
				final boolean isDefaultCurrency = exchangeRate.currencyCode.equals(defaultCurrency);

				view.setBackgroundResource(isDefaultCurrency ? R.color.bg_less_bright : R.color.bg_bright);

				final View defaultView = view.findViewById(R.id.exchange_rate_row_default);
				defaultView.setVisibility(isDefaultCurrency ? View.VISIBLE : View.INVISIBLE);

				final TextView currencyCodeView = (TextView) view.findViewById(R.id.exchange_rate_row_currency_code);
				currencyCodeView.setText(exchangeRate.currencyCode);

				final CurrencyTextView rateView = (CurrencyTextView) view.findViewById(R.id.exchange_rate_row_rate);
				rateView.setPrecision(Constants.LOCAL_PRECISION);
				rateView.setAmount(WalletUtils.localValue(Utils.COIN, exchangeRate.rate));

				final CurrencyTextView walletView = (CurrencyTextView) view.findViewById(R.id.exchange_rate_row_balance);
				walletView.setPrecision(Constants.LOCAL_PRECISION);
				walletView.setAmount(WalletUtils.localValue(balance, exchangeRate.rate));
				walletView.setStrikeThru(Constants.TEST);
				walletView.setTextColor(getResources().getColor(R.color.fg_less_significant));
			}
		};
		setListAdapter(adapter);

		loaderManager.initLoader(0, null, this);
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
		application.getWallet().removeEventListener(walletChangeListener);
		walletChangeListener.removeCallbacks();

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
		return new CursorLoader(activity, ExchangeRatesProvider.contentUri(activity.getPackageName()), null, null, null, null);
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
