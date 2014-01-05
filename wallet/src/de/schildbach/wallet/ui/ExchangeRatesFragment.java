/*
 * Copyright 2011-2014 the original author or authors.
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.ResourceCursorAdapter;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class ExchangeRatesFragment extends SherlockListFragment implements OnSharedPreferenceChangeListener
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Wallet wallet;
	private SharedPreferences prefs;
	private LoaderManager loaderManager;

	private ExchangeRatesAdapter adapter;

	private BigInteger balance = null;
	private boolean replaying = false;
	private String defaultCurrency = null;

	private static final int ID_BALANCE_LOADER = 0;
	private static final int ID_RATE_LOADER = 1;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.loaderManager = getLoaderManager();
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		setEmptyText(getString(R.string.exchange_rates_fragment_empty_text));

		adapter = new ExchangeRatesAdapter(activity);
		setListAdapter(adapter);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));

		loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
		loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

		defaultCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, null);
		prefs.registerOnSharedPreferenceChangeListener(this);

		updateView();
	}

	@Override
	public void onPause()
	{
		prefs.unregisterOnSharedPreferenceChangeListener(this);

		loaderManager.destroyLoader(ID_RATE_LOADER);
		loaderManager.destroyLoader(ID_BALANCE_LOADER);

		activity.unregisterReceiver(broadcastReceiver);

		super.onPause();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final Cursor cursor = (Cursor) adapter.getItem(position);
		final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);

		activity.startActionMode(new ActionMode.Callback()
		{
			@Override
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.exchange_rates_context, menu);

				return true;
			}

			@Override
			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				mode.setTitle(exchangeRate.currencyCode);
				mode.setSubtitle(getString(R.string.exchange_rates_fragment_source, exchangeRate.source));

				return true;
			}

			@Override
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

			@Override
			public void onDestroyActionMode(final ActionMode mode)
			{
			}

			private void handleSetAsDefault(final String currencyCode)
			{
				prefs.edit().putString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, currencyCode).commit();
			}
		});
	}

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	{
		if (Constants.PREFS_KEY_EXCHANGE_CURRENCY.equals(key) || Constants.PREFS_KEY_BTC_PRECISION.equals(key))
		{
			defaultCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, null);

			updateView();
		}
	}

	private void updateView()
	{
		balance = application.getWallet().getBalance(BalanceType.ESTIMATED);

		if (adapter != null)
		{
			final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
			final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

			final BigInteger base = btcShift == 0 ? GenericUtils.ONE_BTC : GenericUtils.ONE_MBTC;

			adapter.setRateBase(base);
		}
	}

	private final BlockchainBroadcastReceiver broadcastReceiver = new BlockchainBroadcastReceiver();

	private final class BlockchainBroadcastReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			replaying = intent.getBooleanExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_REPLAYING, false);

			updateView();
		}
	}

	private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		@Override
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			return new CursorLoader(activity, ExchangeRatesProvider.contentUri(activity.getPackageName()), null, null, null, null);
		}

		@Override
		public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
		{
			adapter.swapCursor(data);
		}

		@Override
		public void onLoaderReset(final Loader<Cursor> loader)
		{
			adapter.swapCursor(null);
		}
	};

	private final LoaderCallbacks<BigInteger> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<BigInteger>()
	{
		@Override
		public Loader<BigInteger> onCreateLoader(final int id, final Bundle args)
		{
			return new WalletBalanceLoader(activity, wallet);
		}

		@Override
		public void onLoadFinished(final Loader<BigInteger> loader, final BigInteger balance)
		{
			ExchangeRatesFragment.this.balance = balance;

			updateView();
		}

		@Override
		public void onLoaderReset(final Loader<BigInteger> loader)
		{
		}
	};

	private final class ExchangeRatesAdapter extends ResourceCursorAdapter
	{
		private BigInteger rateBase = GenericUtils.ONE_BTC;

		private ExchangeRatesAdapter(final Context context)
		{
			super(context, R.layout.exchange_rate_row, null, true);
		}

		public void setRateBase(final BigInteger rateBase)
		{
			this.rateBase = rateBase;

			notifyDataSetChanged();
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor)
		{
			final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);
			final boolean isDefaultCurrency = exchangeRate.currencyCode.equals(defaultCurrency);

			view.setBackgroundResource(isDefaultCurrency ? R.color.bg_list_selected : R.color.bg_list);

			final View defaultView = view.findViewById(R.id.exchange_rate_row_default);
			defaultView.setVisibility(isDefaultCurrency ? View.VISIBLE : View.INVISIBLE);

			final TextView currencyCodeView = (TextView) view.findViewById(R.id.exchange_rate_row_currency_code);
			currencyCodeView.setText(exchangeRate.currencyCode);

			final CurrencyTextView rateView = (CurrencyTextView) view.findViewById(R.id.exchange_rate_row_rate);
			rateView.setPrecision(Constants.LOCAL_PRECISION, 0);
			rateView.setAmount(WalletUtils.localValue(rateBase, exchangeRate.rate));

			final CurrencyTextView walletView = (CurrencyTextView) view.findViewById(R.id.exchange_rate_row_balance);
			walletView.setPrecision(Constants.LOCAL_PRECISION, 0);
			if (!replaying)
			{
				walletView.setAmount(WalletUtils.localValue(balance, exchangeRate.rate));
				walletView.setStrikeThru(Constants.TEST);
			}
			else
			{
				walletView.setText("n/a");
				walletView.setStrikeThru(false);
			}
			walletView.setTextColor(getResources().getColor(R.color.fg_less_significant));
		}
	}
}
