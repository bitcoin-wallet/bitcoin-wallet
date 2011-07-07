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

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
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
	private Application application;

	private Service service;
	private final Handler handler = new Handler();

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

		application = (Application) getActivity().getApplication();

		getActivity().bindService(new Intent(getActivity(), Service.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.send_coins_fragment, container);

		final View receivingAddressErrorView = view.findViewById(R.id.send_coins_receiving_address_error);

		final AutoCompleteTextView receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
		receivingAddressView.setAdapter(new AutoCompleteAdapter(getActivity(), null));
		receivingAddressView.addTextChangedListener(new TextWatcher()
		{
			public void afterTextChanged(final Editable s)
			{
				try
				{
					final String address = s.toString().trim();
					if (address.length() > 0)
						new Address(Constants.NETWORK_PARAMS, address);
					receivingAddressErrorView.setVisibility(View.GONE);
				}
				catch (AddressFormatException e)
				{
					receivingAddressErrorView.setVisibility(View.VISIBLE);
				}
			}

			public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
			{
			}

			public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
			{
			}
		});

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

		final Button viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				try
				{
					final Address receivingAddress = new Address(Constants.NETWORK_PARAMS, receivingAddressView.getText().toString().trim());
					final BigInteger amount = Utils.toNanoCoins(amountView.getText().toString());

					System.out.println("about to send " + amount + " (BTC " + Utils.bitcoinValueToFriendlyString(amount) + ") to " + receivingAddress);

					final Transaction transaction = application.getWallet().createSend(receivingAddress, amount);

					if (transaction != null)
					{
						service.sendTransaction(transaction);

						final WalletBalanceFragment balanceFragment = (WalletBalanceFragment) getActivity().getSupportFragmentManager()
								.findFragmentById(R.id.wallet_balance_fragment);
						if (balanceFragment != null)
							balanceFragment.updateView();

						viewGo.setEnabled(false);
						viewGo.setText("Sending...");
						handler.postDelayed(new Runnable()
						{
							public void run()
							{
								getActivity().finish();
							}
						}, 5000);

						Toast.makeText(getActivity(), Utils.bitcoinValueToFriendlyString(amount) + " BTC sent!", Toast.LENGTH_LONG).show();
					}
					else
					{
						Toast.makeText(getActivity(), "problem sending coins!", Toast.LENGTH_LONG).show();
						getActivity().finish();
					}
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
	public void onDestroy()
	{
		getActivity().unbindService(serviceConnection);

		super.onDestroy();
	}

	public class AutoCompleteAdapter extends CursorAdapter
	{
		public AutoCompleteAdapter(final Context context, final Cursor c)
		{
			super(context, c);
		}

		@Override
		public View newView(final Context context, final Cursor cursor, final ViewGroup parent)
		{
			final LayoutInflater inflater = LayoutInflater.from(context);
			return inflater.inflate(R.layout.simple_dropdown_item_2line, parent, false);
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor)
		{
			final ViewGroup viewGroup = (ViewGroup) view;
			((TextView) viewGroup.findViewById(android.R.id.text1)).setText(cursor.getString(cursor
					.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL)));
			((TextView) viewGroup.findViewById(android.R.id.text2)).setText(cursor.getString(cursor
					.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS)));
		}

		@Override
		public CharSequence convertToString(final Cursor cursor)
		{
			return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
		}

		@Override
		public Cursor runQueryOnBackgroundThread(final CharSequence constraint)
		{
			System.out.println("runQuery: " + constraint);
			final Cursor cursor = getActivity()
					.managedQuery(AddressBookProvider.CONTENT_URI, null, "q", new String[] { constraint.toString() }, null);
			return cursor;
		}
	}
}
