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

import java.io.IOException;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;

/**
 * @author Andreas Schildbach
 */
public class SendCoinsFragment extends Fragment
{
	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private Service service;

	private Pattern P_BITCOIN_URI = Pattern.compile("([a-zA-Z0-9]*)(?:\\?amount=(.*))?");

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((Service.LocalBinder) binder).getService();
			onServiceBound();
		}

		public void onServiceDisconnected(final ComponentName name)
		{
			service = null;
			onServiceUnbound();
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		getActivity().bindService(new Intent(getActivity(), Service.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		// background thread
		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		final View view = inflater.inflate(R.layout.send_coins_fragment, container);
		final TextView receivingAddressView = (TextView) view.findViewById(R.id.send_coins_receiving_address);
		final TextView amountView = (TextView) view.findViewById(R.id.send_coins_amount);

		final Uri intentUri = getActivity().getIntent().getData();
		if (intentUri != null && "bitcoin".equals(intentUri.getScheme()))
		{
			final Matcher m = P_BITCOIN_URI.matcher(intentUri.getSchemeSpecificPart());
			if (m.matches())
			{
				receivingAddressView.setText(m.group(1));
				if (m.group(2) != null)
					amountView.setText(m.group(2));
			}
		}

		view.findViewById(R.id.send_coins_go).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				try
				{
					final Address receivingAddress = new Address(Constants.NETWORK_PARAMS, receivingAddressView.getText().toString());
					final BigInteger amount = Utils.toNanoCoins(amountView.getText().toString());

					backgroundHandler.post(new Runnable()
					{
						public void run()
						{
							try
							{
								final Transaction tx = service.sendCoins(receivingAddress, amount);

								if (tx != null)
								{
									getActivity().runOnUiThread(new Runnable()
									{
										public void run()
										{
											final Application application = (Application) getActivity().getApplication();
											application.saveWallet();

											final WalletBalanceFragment balanceFragment = (WalletBalanceFragment) getActivity()
													.getSupportFragmentManager().findFragmentById(R.id.wallet_balance_fragment);
											if (balanceFragment != null)
												balanceFragment.updateView();

											getActivity().finish();

											Toast.makeText(getActivity(), Utils.bitcoinValueToFriendlyString(amount) + " BTC sent!",
													Toast.LENGTH_LONG).show();
										}
									});
								}
								else
								{
									getActivity().runOnUiThread(new Runnable()
									{
										public void run()
										{
											Toast.makeText(getActivity(), "problem sending coins!", Toast.LENGTH_LONG).show();
											getActivity().finish();
										}
									});
								}
							}
							catch (final IOException x)
							{
								x.printStackTrace();

								getActivity().runOnUiThread(new Runnable()
								{
									public void run()
									{
										Toast.makeText(getActivity(), "problem sending coins: " + x.getMessage(), Toast.LENGTH_LONG).show();
										getActivity().finish();
									}
								});
							}
						}
					});
				}
				catch (final AddressFormatException x)
				{
					x.printStackTrace();
				}
			}
		});

		view.findViewById(R.id.send_coins_cancel).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				getActivity().finish();
			}
		});

		return view;
	}

	protected void onServiceBound()
	{
		System.out.println("service bound");
	}

	protected void onServiceUnbound()
	{
		System.out.println("service unbound");
	}

	@Override
	public void onDestroyView()
	{
		// cancel background thread
		backgroundThread.getLooper().quit();

		super.onDestroyView();
	}

	@Override
	public void onDestroy()
	{
		getActivity().unbindService(serviceConnection);

		super.onDestroy();
	}
}
