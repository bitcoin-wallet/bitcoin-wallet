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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
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

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet.BalanceType;

import de.schildbach.wallet.BtcAmountView.Listener;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class SendCoinsFragment extends Fragment
{
	private Application application;

	private Service service;
	private final Handler handler = new Handler();

	private AutoCompleteTextView receivingAddressView;
	private View receivingAddressErrorView;
	private BtcAmountView amountView;
	private BtcAmountView feeView;
	private Button viewGo;

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

	private final TextWatcher textWatcher = new TextWatcher()
	{
		public void afterTextChanged(final Editable s)
		{
			updateView();
		}

		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
		}
	};

	private final Listener listener = new Listener()
	{
		public void changed()
		{
			updateView();
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
		final float density = getResources().getDisplayMetrics().density;

		final BigInteger estimated = application.getWallet().getBalance(BalanceType.ESTIMATED);
		final BigInteger available = application.getWallet().getBalance(BalanceType.AVAILABLE);
		final BigInteger pending = estimated.subtract(available);
		// TODO subscribe to wallet changes

		receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
		receivingAddressView.setAdapter(new AutoCompleteAdapter(getActivity(), null));
		receivingAddressView.addTextChangedListener(textWatcher);

		receivingAddressErrorView = view.findViewById(R.id.send_coins_receiving_address_error);

		final TextView availableView = (TextView) view.findViewById(R.id.send_coins_available);
		availableView.setCompoundDrawablesWithIntrinsicBounds(new BtcDrawable(24f * density, 10.5f * density), null, null, null);
		availableView.setText(Utils.bitcoinValueToFriendlyString(available));

		final TextView pendingView = (TextView) view.findViewById(R.id.send_coins_pending);
		pendingView.setVisibility(pending.signum() > 0 ? View.VISIBLE : View.GONE);
		pendingView.setText(getString(R.string.send_coins_fragment_pending, Utils.bitcoinValueToFriendlyString(pending)));

		amountView = (BtcAmountView) view.findViewById(R.id.send_coins_amount);
		amountView.setListener(listener);

		feeView = (BtcAmountView) view.findViewById(R.id.send_coins_fee);
		feeView.setAmount(Constants.DEFAULT_TX_FEE);
		feeView.setListener(listener);

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				try
				{
					final Address receivingAddress = new Address(application.getNetworkParameters(), receivingAddressView.getText().toString().trim());
					final BigInteger amount = amountView.getAmount();
					final BigInteger fee = feeView.getAmount();

					System.out.println("about to send " + amount + " (BTC " + Utils.bitcoinValueToFriendlyString(amount) + ") to " + receivingAddress);

					final Transaction transaction = application.getWallet().createSend(receivingAddress, amount, fee);

					if (transaction != null)
					{
						service.sendTransaction(transaction);

						final WalletBalanceFragment balanceFragment = (WalletBalanceFragment) getActivity().getSupportFragmentManager()
								.findFragmentById(R.id.wallet_balance_fragment);
						if (balanceFragment != null)
							balanceFragment.updateView();

						viewGo.setEnabled(false);
						viewGo.setText(R.string.send_coins_sending_msg);

						handler.postDelayed(new Runnable()
						{
							public void run()
							{
								final Uri uri = AddressBookProvider.CONTENT_URI.buildUpon().appendPath(receivingAddress.toString()).build();
								final Cursor cursor = getActivity().managedQuery(uri, null, null, null, null);
								if (cursor.getCount() == 0)
								{
									final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
									builder.setMessage(R.string.send_coins_add_address_dialog_title);
									builder.setPositiveButton(R.string.send_coins_add_address_dialog_button_add,
											new DialogInterface.OnClickListener()
											{
												public void onClick(final DialogInterface dialog, final int id)
												{
													final FragmentTransaction ft = getFragmentManager().beginTransaction();
													final Fragment prev = getFragmentManager().findFragmentByTag(
															EditAddressBookEntryFragment.FRAGMENT_TAG);
													if (prev != null)
														ft.remove(prev);
													ft.addToBackStack(null);
													final DialogFragment newFragment = new EditAddressBookEntryFragment(getLayoutInflater(null),
															receivingAddress.toString())
													{
														@Override
														public void onDestroyView()
														{
															super.onDestroyView();

															getActivity().finish();
														}
													};
													newFragment.show(ft, EditAddressBookEntryFragment.FRAGMENT_TAG);
												}
											});
									builder.setNegativeButton(R.string.send_coins_add_address_dialog_button_dismiss,
											new DialogInterface.OnClickListener()
											{
												public void onClick(final DialogInterface dialog, final int id)
												{
													getActivity().finish();
												}
											});
									builder.show();
								}
								else
								{
									getActivity().finish();
								}
							}
						}, 5000);

						((AbstractWalletActivity) getActivity()).longToast(R.string.send_coins_success_msg,
								Utils.bitcoinValueToFriendlyString(amount));
					}
					else
					{
						((AbstractWalletActivity) getActivity()).longToast(R.string.send_coins_error_msg);
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

		updateView();

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
			final Cursor cursor = getActivity()
					.managedQuery(AddressBookProvider.CONTENT_URI, null, "q", new String[] { constraint.toString() }, null);
			return cursor;
		}
	}

	public void update(final String receivingAddress, final String amount)
	{
		receivingAddressView.setText(receivingAddress);
		amountView.setAmount(Utils.toNanoCoins(amount));

		if (receivingAddress != null && amount == null)
			amountView.requestFocus();

		updateView();
	}

	private void updateView()
	{
		boolean validAddress = false;
		try
		{
			final String address = receivingAddressView.getText().toString().trim();
			if (address.length() > 0)
			{
				new Address(application.getNetworkParameters(), address);
				validAddress = true;
			}
			receivingAddressErrorView.setVisibility(View.GONE);
		}
		catch (final Exception x)
		{
			receivingAddressErrorView.setVisibility(View.VISIBLE);
		}

		final BigInteger amount = amountView.getAmount();
		boolean validAmount = amount != null && amount.signum() > 0;

		final BigInteger fee = feeView.getAmount();
		boolean validFee = fee != null && fee.signum() >= 0;

		viewGo.setEnabled(validAddress && validAmount && validFee);
	}
}
