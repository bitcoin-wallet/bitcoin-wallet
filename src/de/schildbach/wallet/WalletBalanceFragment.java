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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
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

	private TextView viewBalance;
	private TextView viewBalanceInDollars;

	private Float exchangeRate;

	private final WalletEventListener walletEventListener = new WalletEventListener()
	{
		@Override
		public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			getActivity().runOnUiThread(new Runnable()
			{
				public void run()
				{
					updateView();
				}
			});
		}

		@Override
		public void onReorganize()
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
		viewBalanceInDollars = (TextView) view.findViewById(R.id.wallet_balance_in_dollars);

		application = (Application) getActivity().getApplication();
		final Wallet wallet = application.getWallet();

		// background thread
		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		wallet.addEventListener(walletEventListener);

		updateView();

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				try
				{
					final Float newExchangeRate = getExchangeRate();

					if (newExchangeRate != null)
					{
						getActivity().runOnUiThread(new Runnable()
						{
							public void run()
							{
								exchangeRate = newExchangeRate;
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

		return view;
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
			viewBalanceInDollars.setText(null);
		}
		else if (exchangeRate != null)
		{
			final BigInteger dollars = new BigDecimal(balance).multiply(new BigDecimal(exchangeRate)).toBigInteger();
			viewBalanceInDollars.setText(String.format("worth about US$ %s" + (application.isTest() ? "\nif it were real bitcoins" : ""),
					Utils.bitcoinValueToFriendlyString(dollars)));
		}
	}

	private static final Pattern P_EXCHANGE_RATE = Pattern.compile("\"last\":(\\d*\\.\\d*)[^\\d]");

	private static Float getExchangeRate()
	{
		try
		{
			final URLConnection connection = new URL("https://mtgox.com/code/data/ticker.php").openConnection(); // https://bitmarket.eu/api/ticker
			connection.connect();
			final Reader is = new InputStreamReader(new BufferedInputStream(connection.getInputStream()));
			final StringBuilder content = new StringBuilder();
			copy(is, content);
			is.close();

			final Matcher m = P_EXCHANGE_RATE.matcher(content);
			if (m.find())
				return Float.parseFloat(m.group(1));
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}

		return null;
	}

	private static final long copy(final Reader reader, final StringBuilder builder) throws IOException
	{
		final char[] buffer = new char[256];
		long count = 0;
		int n = 0;
		while (-1 != (n = reader.read(buffer)))
		{
			builder.append(buffer, 0, n);
			count += n;
		}
		return count;
	}
}
