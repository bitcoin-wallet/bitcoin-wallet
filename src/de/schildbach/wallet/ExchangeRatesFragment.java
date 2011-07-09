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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.WalletEventListener;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesFragment extends ListFragment
{
	private Application application;

	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private List<String> currencyCodes;
	private Map<String, Double> exchangeRates;

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

		application = (Application) getActivity().getApplication();
		final Wallet wallet = application.getWallet();

		// background thread
		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		wallet.addEventListener(walletEventListener);

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				try
				{
					final Map<String, Double> newExchangeRates = application.getExchangeRates();

					if (newExchangeRates != null)
					{
						getActivity().runOnUiThread(new Runnable()
						{
							public void run()
							{
								exchangeRates = newExchangeRates;
								currencyCodes = new LinkedList<String>(exchangeRates.keySet());
								Collections.sort(currencyCodes);

								setListAdapter(new Adapter());

								getListView().setOnItemClickListener(new OnItemClickListener()
								{
									public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id)
									{
										final String currencyCode = currencyCodes.get(position);

										final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
										prefs.edit().putString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, currencyCode).commit();

										final WalletBalanceFragment walletBalanceFragment = (WalletBalanceFragment) getFragmentManager()
												.findFragmentById(R.id.wallet_balance_fragment);
										if (walletBalanceFragment != null)
										{
											walletBalanceFragment.updateView();
											walletBalanceFragment.flashLocal();
										}
									}
								});
							}
						});
					}
				}
				catch (final Exception x)
				{
					// if something happens here, don't care. it's not important.
					x.printStackTrace();
				}
			}
		});
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = super.onCreateView(inflater, container, savedInstanceState);

		view.setBackgroundColor(Color.WHITE);

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
		application.getWallet().removeEventListener(walletEventListener);

		// cancel background thread
		backgroundThread.getLooper().quit();

		super.onDestroy();
	}

	private void updateView()
	{
		final ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

	private class Adapter extends BaseAdapter
	{
		public int getCount()
		{
			return currencyCodes.size();
		}

		public Object getItem(final int position)
		{
			return exchangeRates.get(currencyCodes.get(position));
		}

		public long getItemId(final int position)
		{
			return currencyCodes.get(position).hashCode();
		}

		public View getView(final int position, View row, final ViewGroup parent)
		{
			final String currencyCode = currencyCodes.get(position);
			final Double exchangeRate = exchangeRates.get(currencyCode);

			if (row == null)
				row = getLayoutInflater(null).inflate(R.layout.exchange_rate_row, null);

			((TextView) row.findViewById(R.id.exchange_rate_currency_code)).setText(currencyCode);

			final TextView valueView = ((TextView) row.findViewById(R.id.exchange_rate_value));
			final BigInteger value = new BigDecimal(application.getWallet().getBalance(BalanceType.ESTIMATED)).multiply(new BigDecimal(exchangeRate))
					.toBigInteger();
			valueView.setText(Utils.bitcoinValueToFriendlyString(value));

			return row;
		}
	}
}
