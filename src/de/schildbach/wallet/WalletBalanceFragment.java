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
import java.util.Map;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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

/**
 * @author Andreas Schildbach
 */
public class WalletBalanceFragment extends Fragment
{
	private Application application;

	private HandlerThread backgroundThread;
	private Handler backgroundHandler;
	private final Handler handler = new Handler();

	private TextView viewBalance;
	private TextView viewBalanceLocal;

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
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_balance_fragment, container, false);
		viewBalance = (TextView) view.findViewById(R.id.wallet_balance);
		viewBalanceLocal = (TextView) view.findViewById(R.id.wallet_balance_local);

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
								updateView();
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
	public void onDestroyView()
	{
		application.getWallet().removeEventListener(walletEventListener);

		// cancel background thread
		backgroundThread.getLooper().quit();

		super.onDestroyView();
	}

	public void updateView()
	{
		final BigInteger balance = application.getWallet().getBalance(BalanceType.ESTIMATED);
		viewBalance.setText(Utils.bitcoinValueToFriendlyString(balance));

		if (balance.equals(BigInteger.ZERO))
		{
			viewBalanceLocal.setText(null);
		}
		else if (exchangeRates != null)
		{
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			final String exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, "USD");
			final Double exchangeRate = exchangeRates.get(exchangeCurrency);
			if (exchangeRate != null)
			{
				final BigInteger valueLocal = new BigDecimal(balance).multiply(new BigDecimal(exchangeRate)).toBigInteger();
				viewBalanceLocal.setText(String.format("worth about %s %s", exchangeCurrency, Utils.bitcoinValueToFriendlyString(valueLocal)));
				if (application.isTest())
					viewBalanceLocal.setPaintFlags(viewBalanceLocal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}
		}
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
}
