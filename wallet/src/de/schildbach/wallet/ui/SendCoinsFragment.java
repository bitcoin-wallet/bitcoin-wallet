/*
 * Copyright 2011-2012 the original author or authors.
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
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Color;
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
import com.google.bitcoin.core.Wallet.BalanceType;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.ui.CurrencyAmountView.Listener;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends Fragment
{
	private WalletApplication application;

	private BlockchainService service;
	private final Handler handler = new Handler();
	private Runnable sentRunnable;

	private AutoCompleteTextView receivingAddressView;
	private View receivingAddressErrorView;
	private CurrencyAmountView amountView;
	private CurrencyAmountView feeView;
	private Button viewGo;
	private Button viewCancel;

	private State state = State.INPUT;

	private enum State
	{
		INPUT, SENDING, SENT
	}

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((BlockchainService.LocalBinder) binder).getService();
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

		public void done()
		{
			viewGo.requestFocusFromTouch();
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Activity activity = getActivity();

		application = (WalletApplication) activity.getApplication();

		activity.bindService(new Intent(activity, BlockchainService.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final AbstractWalletActivity activity = (AbstractWalletActivity) getActivity();

		final View view = inflater.inflate(R.layout.send_coins_fragment, container);

		final BigInteger estimated = application.getWallet().getBalance(BalanceType.ESTIMATED);
		final BigInteger available = application.getWallet().getBalance(BalanceType.AVAILABLE);
		final BigInteger pending = estimated.subtract(available);
		// TODO subscribe to wallet changes

		receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
		receivingAddressView.setAdapter(new AutoCompleteAdapter(activity, null));
		receivingAddressView.addTextChangedListener(textWatcher);

		receivingAddressErrorView = view.findViewById(R.id.send_coins_receiving_address_error);

		final CurrencyAmountView availableView = (CurrencyAmountView) view.findViewById(R.id.send_coins_available);
		availableView.setAmount(available);

		final TextView pendingView = (TextView) view.findViewById(R.id.send_coins_pending);
		pendingView.setVisibility(pending.signum() > 0 ? View.VISIBLE : View.GONE);
		pendingView.setText(getString(R.string.send_coins_fragment_pending, WalletUtils.formatValue(pending)));

		amountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount);
		amountView.setListener(listener);
		amountView.setContextButton(R.drawable.ic_input_calculator, new OnClickListener()
		{
			public void onClick(final View v)
			{
				final FragmentTransaction ft = getFragmentManager().beginTransaction();
				final Fragment prev = getFragmentManager().findFragmentByTag(AmountCalculatorFragment.FRAGMENT_TAG);
				if (prev != null)
					ft.remove(prev);
				ft.addToBackStack(null);
				final DialogFragment newFragment = new AmountCalculatorFragment(new AmountCalculatorFragment.Listener()
				{
					public void use(final BigInteger amount)
					{
						amountView.setAmount(amount);
					}
				});
				newFragment.show(ft, AmountCalculatorFragment.FRAGMENT_TAG);
			}
		});

		feeView = (CurrencyAmountView) view.findViewById(R.id.send_coins_fee);
		feeView.setAmount(Constants.DEFAULT_TX_FEE);
		feeView.setListener(listener);

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				try
				{
					final Address receivingAddress = new Address(Constants.NETWORK_PARAMETERS, receivingAddressView.getText().toString().trim());
					final BigInteger amount = amountView.getAmount();
					final BigInteger fee = feeView.getAmount();

					System.out.println("about to send " + amount + " (" + Constants.CURRENCY_CODE_BITCOIN + " " + WalletUtils.formatValue(amount)
							+ ") to " + receivingAddress);

					final Transaction tx = service.sendCoins(receivingAddress, amount, fee);

					if (tx != null)
					{
						state = State.SENDING;
						updateView();

						final WalletBalanceFragment balanceFragment = (WalletBalanceFragment) activity.getSupportFragmentManager().findFragmentById(
								R.id.wallet_balance_fragment);
						if (balanceFragment != null)
							balanceFragment.updateView();

						sentRunnable = new Runnable()
						{
							public void run()
							{
								state = State.SENT;
								updateView();

								final String label = AddressBookProvider.resolveLabel(activity.getContentResolver(), receivingAddress.toString());
								if (label == null)
									showAddAddressDialog(receivingAddress.toString());

								// TransactionActivity.show(activity, tx);
							}
						};
						handler.postDelayed(sentRunnable, 3000);

						activity.longToast(R.string.send_coins_success_msg, WalletUtils.formatValue(amount));

						activity.setResult(Activity.RESULT_OK);
					}
					else
					{
						activity.longToast(R.string.send_coins_error_msg);
					}
				}
				catch (final AddressFormatException x)
				{
					x.printStackTrace();
				}
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				activity.setResult(Activity.RESULT_CANCELED);

				activity.finish();
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
	public void onDestroyView()
	{
		handler.removeCallbacks(sentRunnable);

		super.onDestroyView();
	}

	@Override
	public void onDestroy()
	{
		final Activity activity = getActivity();

		activity.unbindService(serviceConnection);

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
			final Cursor cursor = getActivity().managedQuery(AddressBookProvider.CONTENT_URI, null, AddressBookProvider.SELECTION_QUERY,
					new String[] { constraint.toString() }, null);
			return cursor;
		}
	}

	private void updateView()
	{
		boolean validAddress = false;
		try
		{
			final String address = receivingAddressView.getText().toString().trim();
			if (address.length() > 0)
			{
				new Address(Constants.NETWORK_PARAMETERS, address);
				validAddress = true;
			}
			receivingAddressErrorView.setVisibility(View.GONE);
		}
		catch (final Exception x)
		{
			receivingAddressErrorView.setVisibility(View.VISIBLE);
		}

		final BigInteger amount = amountView.getAmount();
		final boolean validAmount = amount != null && amount.signum() > 0;

		final BigInteger fee = feeView.getAmount();
		final boolean validFee = fee != null && fee.signum() >= 0;

		receivingAddressView.setEnabled(state == State.INPUT);

		amountView.setEnabled(state == State.INPUT);

		feeView.setEnabled(state == State.INPUT);

		viewGo.setEnabled(state == State.INPUT && validAddress && validAmount && validFee);
		if (state == State.INPUT)
			viewGo.setText(R.string.send_coins_fragment_button_send);
		else if (state == State.SENDING)
			viewGo.setText(R.string.send_coins_sending_msg);
		else if (state == State.SENT)
			viewGo.setText(R.string.send_coins_sent_msg);

		viewCancel.setEnabled(state != State.SENDING);
		viewCancel.setText(state != State.SENT ? R.string.button_cancel : R.string.send_coins_fragment_button_back);
	}

	public void update(final String receivingAddress, final BigInteger amount)
	{
		receivingAddressView.setText(receivingAddress);
		flashReceivingAddress();

		if (amount != null)
			amountView.setAmount(amount);

		if (receivingAddress != null && amount == null)
			amountView.requestFocus();

		updateView();
	}

	private void showAddAddressDialog(final String address)
	{
		final Activity activity = getActivity();
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(R.string.send_coins_add_address_dialog_title);
		builder.setPositiveButton(R.string.send_coins_add_address_dialog_button_add, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int id)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}
		});
		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	private Runnable resetColorRunnable = new Runnable()
	{
		public void run()
		{
			receivingAddressView.setTextColor(Color.parseColor("#888888"));
		}
	};

	public void flashReceivingAddress()
	{
		receivingAddressView.setTextColor(Color.parseColor("#cc5500"));
		handler.removeCallbacks(resetColorRunnable);
		handler.postDelayed(resetColorRunnable, 500);
	}
}
