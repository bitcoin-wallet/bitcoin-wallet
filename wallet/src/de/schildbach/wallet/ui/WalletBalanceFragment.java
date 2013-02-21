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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

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
public final class WalletBalanceFragment extends Fragment
{
	private WalletApplication application;
	private AbstractWalletActivity activity;
	private Wallet wallet;
	private LoaderManager loaderManager;

	private CurrencyAmountView viewBalance;
	private CurrencyAmountView viewBalanceLocal;

	private boolean showLocalBalance;

	private BigInteger balance = null;
	private ExchangeRate exchangeRate = null;

	private static final int ID_BALANCE_LOADER = 0;
	private static final int ID_RATE_LOADER = 1;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		application = (WalletApplication) activity.getApplication();
		loaderManager = getLoaderManager();

		wallet = application.getWallet();

		showLocalBalance = getResources().getBoolean(R.bool.show_local_balance);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_balance_fragment, container, false);

		final boolean showExchangeRatesOption = getResources().getBoolean(R.bool.show_exchange_rates_option);

		if (showExchangeRatesOption)
		{
			view.setOnClickListener(new OnClickListener()
			{
				public void onClick(final View v)
				{
					startActivity(new Intent(getActivity(), ExchangeRatesActivity.class));
				}
			});
		}
		else
		{
			view.setEnabled(false);
		}

		return view;
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		viewBalance = (CurrencyAmountView) view.findViewById(R.id.wallet_balance);

		viewBalanceLocal = (CurrencyAmountView) view.findViewById(R.id.wallet_balance_local);
		viewBalanceLocal.setPrecision(Constants.LOCAL_PRECISION);
		viewBalanceLocal.setSmallerInsignificant(false);
		viewBalanceLocal.setStrikeThru(Constants.TEST);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
		loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

		updateView();
	}

	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_RATE_LOADER);
		loaderManager.destroyLoader(ID_BALANCE_LOADER);

		super.onPause();
	}

	private void updateView()
	{
		if (!showLocalBalance)
			viewBalanceLocal.setVisibility(View.GONE);

		if (balance != null)
		{
			viewBalance.setVisibility(View.VISIBLE);
			viewBalance.setAmount(balance);

			if (showLocalBalance)
			{
				if (exchangeRate != null)
				{
					final BigInteger balance = wallet.getBalance(BalanceType.ESTIMATED);
					final BigInteger localValue = WalletUtils.localValue(balance, exchangeRate.rate);
					viewBalanceLocal.setVisibility(View.VISIBLE);
					viewBalanceLocal.setCurrencyCode(Constants.PREFIX_ALMOST_EQUAL_TO + exchangeRate.currencyCode);
					viewBalanceLocal.setAmount(localValue);
					viewBalanceLocal.setTextColor(getResources().getColor(R.color.fg_less_significant));
				}
				else
				{
					viewBalanceLocal.setVisibility(View.INVISIBLE);
				}
			}
		}
		else
		{
			viewBalance.setVisibility(View.INVISIBLE);
		}
	}

	private static class BalanceLoader extends AsyncTaskLoader<BigInteger>
	{
		private final Wallet wallet;

		private BalanceLoader(final Context context, final Wallet wallet)
		{
			super(context);

			this.wallet = wallet;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			wallet.addEventListener(walletChangeListener);

			forceLoad();
		}

		@Override
		protected void onStopLoading()
		{
			wallet.removeEventListener(walletChangeListener);
			walletChangeListener.removeCallbacks();

			super.onStopLoading();
		}

		@Override
		public BigInteger loadInBackground()
		{
			return wallet.getBalance(BalanceType.ESTIMATED);
		}

		private final ThrottelingWalletChangeListener walletChangeListener = new ThrottelingWalletChangeListener()
		{
			@Override
			public void onThrotteledWalletChanged()
			{
				forceLoad();
			}
		};
	}

	private final LoaderCallbacks<BigInteger> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<BigInteger>()
	{
		public Loader<BigInteger> onCreateLoader(final int id, final Bundle args)
		{
			return new BalanceLoader(activity, wallet);
		}

		public void onLoadFinished(final Loader<BigInteger> loader, final BigInteger balance)
		{
			WalletBalanceFragment.this.balance = balance;
			updateView();
		}

		public void onLoaderReset(final Loader<BigInteger> loader)
		{
		}
	};

	private static class RateLoader extends CursorLoader implements OnSharedPreferenceChangeListener
	{
		private final SharedPreferences prefs;

		public RateLoader(final Context context)
		{
			super(context, ExchangeRatesProvider.CONTENT_URI, null, ExchangeRatesProvider.KEY_CURRENCY_CODE, null, null);

			prefs = PreferenceManager.getDefaultSharedPreferences(context);

			onCurrencyChange();
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			prefs.registerOnSharedPreferenceChangeListener(this);

			forceLoad();
		}

		@Override
		protected void onStopLoading()
		{
			prefs.unregisterOnSharedPreferenceChangeListener(this);

			super.onStopLoading();
		}

		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (Constants.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
			{
				cancelLoad();
				onCurrencyChange();
				forceLoad();
			}
		}

		private void onCurrencyChange()
		{
			final String exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);

			setSelectionArgs(new String[] { exchangeCurrency });
		}
	}

	private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			return new RateLoader(activity);
		}

		public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
		{
			if (data != null)
			{
				data.moveToFirst();
				exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
				updateView();
			}
		}

		public void onLoaderReset(final Loader<Cursor> loader)
		{
		}
	};
}
