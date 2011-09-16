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

import java.math.BigDecimal;
import java.math.BigInteger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletBalanceFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	private Application application;
	private Wallet wallet;
	private SharedPreferences prefs;
	private final Handler handler = new Handler();

	private CurrencyAmountView viewBalance;
	private TextView viewBalanceLocal;

	private final WalletEventListener walletEventListener = new WalletEventListener()
	{
		@Override
		public void onPendingCoinsReceived(final Wallet wallet, final Transaction tx)
		{
			onEverything();
		}

		@Override
		public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			onEverything();
		}

		@Override
		public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			onEverything();
		}

		@Override
		public void onReorganize()
		{
			onEverything();
		}

		@Override
		public void onDeadTransaction(final Transaction deadTx, final Transaction replacementTx)
		{
			onEverything();
		}

		private void onEverything()
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

		final Activity activity = getActivity();
		application = (Application) activity.getApplication();
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);

		wallet = application.getWallet();
		wallet.addEventListener(walletEventListener);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_balance_fragment, container, false);
		viewBalance = (CurrencyAmountView) view.findViewById(R.id.wallet_balance);
		viewBalanceLocal = (TextView) view.findViewById(R.id.wallet_balance_local);

		view.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final FragmentManager fm = getFragmentManager();
				final FragmentTransaction ft = fm.beginTransaction();
				ft.hide(fm.findFragmentById(R.id.wallet_address_fragment));
				ft.hide(fm.findFragmentById(R.id.wallet_transactions_fragment));
				ft.show(fm.findFragmentById(R.id.exchange_rates_fragment));
				ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
				ft.addToBackStack(null);
				ft.commit();
			}
		});

		return view;
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
		wallet.removeEventListener(walletEventListener);

		super.onDestroy();
	}

	public void updateView()
	{
		viewBalance.setAmount(wallet.getBalance(BalanceType.ESTIMATED));

		getLoaderManager().restartLoader(0, null, this);
	}

	private Runnable resetColorRunnable = new Runnable()
	{
		public void run()
		{
			viewBalanceLocal.setTextColor(Color.parseColor("#888888"));
		}
	};

	public void flashLocal()
	{
		viewBalanceLocal.setTextColor(Color.parseColor("#cc5500"));
		handler.removeCallbacks(resetColorRunnable);
		handler.postDelayed(resetColorRunnable, 500);
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		final String exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);
		return new CursorLoader(getActivity(), ExchangeRatesProvider.CONTENT_URI, null, ExchangeRatesProvider.KEY_CURRENCY_CODE,
				new String[] { exchangeCurrency }, null);
	}

	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		if (data != null)
		{
			data.moveToFirst();
			final Double exchangeRate = data.getDouble(data.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_EXCHANGE_RATE));

			viewBalanceLocal.setVisibility(View.GONE);
			if (application.getWallet().getBalance(BalanceType.ESTIMATED).signum() > 0 && exchangeRate != null)
			{
				final String exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);
				final BigInteger balance = wallet.getBalance(BalanceType.ESTIMATED);
				final BigInteger valueLocal = new BigDecimal(balance).multiply(new BigDecimal(exchangeRate)).toBigInteger();
				viewBalanceLocal.setVisibility(View.VISIBLE);
				viewBalanceLocal.setText(getString(R.string.wallet_balance_fragment_local_value, exchangeCurrency,
						Utils.bitcoinValueToFriendlyString(valueLocal)));
				if (Constants.TEST)
					viewBalanceLocal.setPaintFlags(viewBalanceLocal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}
		}
	}

	public void onLoaderReset(final Loader<Cursor> loader)
	{
	}
}
