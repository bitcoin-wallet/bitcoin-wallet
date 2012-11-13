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
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.integration.android.BitcoinIntegration;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends SherlockFragment implements AmountCalculatorFragment.Listener
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private ContentResolver contentResolver;
	private Wallet wallet;

	private BlockchainService service;
	private final Handler handler = new Handler();

	private AutoCompleteTextView receivingAddressView;
	private View receivingStaticView;
	private TextView receivingStaticAddressView;
	private TextView receivingStaticLabelView;
	private CurrencyAmountView amountView;
	private CurrencyAmountView feeView;

	private ListView sentTransactionView;
	private TransactionsListAdapter sentTransactionListAdapter;
	private Button viewGo;
	private Button viewCancel;

	private TextView popupMessageView;
	private View popupAvailableView;
	private PopupWindow popupWindow;

	private Address validatedAddress;
	private String receivingLabel;
	private boolean isValidAmounts;

	private State state = State.INPUT;
	private Transaction sentTransaction;

	private enum State
	{
		INPUT, SENDING, SENT
	}

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			service = ((BlockchainServiceImpl.LocalBinder) binder).getService();
		}

		public void onServiceDisconnected(final ComponentName name)
		{
			service = null;
		}
	};

	private final class ReceivingAddressListener implements OnFocusChangeListener, TextWatcher
	{
		public void onFocusChange(final View v, final boolean hasFocus)
		{
			if (!hasFocus)
				validateReceivingAddress(true);
		}

		public void afterTextChanged(final Editable s)
		{
			dismissPopup();

			receivingLabel = null;

			validateReceivingAddress(false);
		}

		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
		}
	}

	private final ReceivingAddressListener receivingAddressListener = new ReceivingAddressListener();

	private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener()
	{
		public void changed()
		{
			dismissPopup();

			validateAmounts(false);
		}

		public void done()
		{
			validateAmounts(true);

			viewGo.requestFocusFromTouch();
		}

		public void focusChanged(final boolean hasFocus)
		{
			if (!hasFocus)
			{
				validateAmounts(true);
			}
		}
	};

	private final ContentObserver contentObserver = new ContentObserver(handler)
	{
		@Override
		public void onChange(final boolean selfChange)
		{
			updateView();
		}
	};

	private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener()
	{
		public void onConfidenceChanged(final Transaction tx)
		{
			activity.runOnUiThread(new Runnable()
			{
				public void run()
				{
					sentTransactionListAdapter.notifyDataSetChanged();

					if (state == State.SENDING && sentTransaction.getConfidence().numBroadcastPeers() > 0)
					{
						state = State.SENT;
						updateView();
					}
				}
			});
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		application = (WalletApplication) activity.getApplication();
		contentResolver = activity.getContentResolver();
		wallet = application.getWallet();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null)
		{
			state = (State) savedInstanceState.getSerializable("state");
			if (savedInstanceState.containsKey("validated_address_bytes"))
				validatedAddress = new Address((NetworkParameters) savedInstanceState.getSerializable("validated_address_params"),
						savedInstanceState.getByteArray("validated_address_bytes"));
			else
				validatedAddress = null;
			receivingLabel = savedInstanceState.getString("receiving_label");
			isValidAmounts = savedInstanceState.getBoolean("is_valid_amounts");
			sentTransaction = (Transaction) savedInstanceState.getSerializable("sent_transaction");
		}

		activity.bindService(new Intent(activity, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.send_coins_fragment, container);

		receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
		receivingAddressView.setAdapter(new AutoCompleteAddressAdapter(activity, null));
		receivingAddressView.setOnFocusChangeListener(receivingAddressListener);
		receivingAddressView.addTextChangedListener(receivingAddressListener);

		receivingStaticView = view.findViewById(R.id.send_coins_receiving_static);
		receivingStaticAddressView = (TextView) view.findViewById(R.id.send_coins_receiving_static_address);
		receivingStaticLabelView = (TextView) view.findViewById(R.id.send_coins_receiving_static_label);

		receivingStaticView.setOnFocusChangeListener(new OnFocusChangeListener()
		{
			private ActionMode actionMode;

			public void onFocusChange(final View v, final boolean hasFocus)
			{
				if (hasFocus)
				{
					actionMode = activity.startActionMode(new ActionMode.Callback()
					{
						public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
						{
							final MenuInflater inflater = mode.getMenuInflater();
							inflater.inflate(R.menu.send_coins_address_context, menu);

							return true;
						}

						public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
						{
							return false;
						}

						public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
						{
							switch (item.getItemId())
							{
								case R.id.send_coins_address_context_clear:

									// switch from static to input
									validatedAddress = null;
									receivingLabel = null;
									receivingAddressView.setText(null);
									receivingStaticAddressView.setText(null);

									updateView();

									receivingAddressView.requestFocus();

									mode.finish();
									return true;
							}

							return false;
						}

						public void onDestroyActionMode(final ActionMode mode)
						{
							if (receivingStaticView.hasFocus())
								amountView.requestFocus();
						}
					});
				}
				else
				{
					actionMode.finish();
				}
			}
		});

		amountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount);
		amountView.setContextButton(R.drawable.ic_input_calculator, new OnClickListener()
		{
			public void onClick(final View v)
			{
				AmountCalculatorFragment.calculate(getFragmentManager(), SendCoinsFragment.this);
			}
		});

		feeView = (CurrencyAmountView) view.findViewById(R.id.send_coins_fee);
		feeView.setAmount(Constants.DEFAULT_TX_FEE);

		sentTransactionView = (ListView) view.findViewById(R.id.send_coins_sent_transaction);
		sentTransactionListAdapter = new TransactionsListAdapter(activity, wallet);
		sentTransactionView.setAdapter(sentTransactionListAdapter);

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				validateReceivingAddress(true);
				validateAmounts(true);

				if (everythingValid())
				{
					final BigInteger fee = feeView.getAmount();

					if (fee.compareTo(Constants.DEFAULT_TX_FEE) >= 0)
						handleGo();
					else
						feeDialog();
				}
			}

			private void feeDialog()
			{
				final AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
				dialog.setMessage(getString(R.string.send_coins_dialog_fee_message,
						Constants.CURRENCY_CODE_BITCOIN + " " + WalletUtils.formatValue(Constants.DEFAULT_TX_FEE)));
				dialog.setPositiveButton(R.string.send_coins_dialog_fee_positive, new DialogInterface.OnClickListener()
				{
					public void onClick(final DialogInterface dialog, final int which)
					{
						handleGo();
					}
				});
				dialog.setNegativeButton(R.string.send_coins_dialog_fee_negative, null);
				dialog.show();
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				if (state == State.INPUT)
					activity.setResult(Activity.RESULT_CANCELED);

				activity.finish();
			}
		});

		popupMessageView = (TextView) inflater.inflate(R.layout.send_coins_popup_message, container);

		popupAvailableView = inflater.inflate(R.layout.send_coins_popup_available, container);

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		contentResolver.registerContentObserver(AddressBookProvider.CONTENT_URI, true, contentObserver);

		amountView.setListener(amountsListener);

		feeView.setListener(amountsListener);

		updateView();
	}

	@Override
	public void onPause()
	{
		feeView.setListener(null);

		amountView.setListener(null);

		contentResolver.unregisterContentObserver(contentObserver);

		super.onPause();
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);

		outState.putSerializable("state", state);
		if (validatedAddress != null)
		{
			outState.putSerializable("validated_address_params", validatedAddress.getParameters());
			outState.putByteArray("validated_address_bytes", validatedAddress.getHash160());
			outState.putBoolean("is_valid_amounts", isValidAmounts);
		}
		outState.putString("receiving_label", receivingLabel);

		outState.putSerializable("sent_transaction", sentTransaction);
	}

	@Override
	public void onDestroy()
	{
		activity.unbindService(serviceConnection);

		if (sentTransaction != null)
			sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);

		super.onDestroy();
	}

	private void validateReceivingAddress(final boolean popups)
	{
		try
		{
			final String addressStr = receivingAddressView.getText().toString().trim();
			if (addressStr.length() > 0)
			{
				final NetworkParameters addressParams = Address.getParametersFromAddress(addressStr);
				if (addressParams != null && !addressParams.equals(Constants.NETWORK_PARAMETERS))
				{
					// address is valid, but from different known network
					if (popups)
						popupMessage(receivingAddressView,
								getString(R.string.send_coins_fragment_receiving_address_error_cross_network, addressParams.getId()));
				}
				else if (addressParams == null)
				{
					// address is valid, but from different unknown network
					if (popups)
						popupMessage(receivingAddressView, getString(R.string.send_coins_fragment_receiving_address_error_cross_network_unknown));
				}
				else
				{
					// valid address
					validatedAddress = new Address(Constants.NETWORK_PARAMETERS, addressStr);
				}
			}
			else
			{
				// empty field should not raise error message
			}
		}
		catch (final AddressFormatException x)
		{
			// could not decode address at all
			if (popups)
				popupMessage(receivingAddressView, getString(R.string.send_coins_fragment_receiving_address_error));
		}

		updateView();
	}

	private void validateAmounts(final boolean popups)
	{
		isValidAmounts = false;

		final BigInteger amount = amountView.getAmount();

		if (amount == null)
		{
			// empty amount
			if (popups)
				popupMessage(amountView, getString(R.string.send_coins_fragment_amount_empty));
		}
		else if (amount.signum() > 0)
		{
			final BigInteger estimated = wallet.getBalance(BalanceType.ESTIMATED);
			final BigInteger available = wallet.getBalance(BalanceType.AVAILABLE);
			final BigInteger pending = estimated.subtract(available);
			// TODO subscribe to wallet changes

			final BigInteger availableAfterAmount = available.subtract(amount);
			final boolean enoughFundsForAmount = availableAfterAmount.signum() >= 0;

			if (enoughFundsForAmount)
			{
				final BigInteger fee = feeView.getAmount();
				final boolean validFee = fee != null && fee.signum() >= 0;

				if (validFee)
				{
					final boolean enoughFunds = availableAfterAmount.subtract(fee).signum() >= 0;

					if (enoughFunds)
					{
						// everything fine
						isValidAmounts = true;
					}
					else
					{
						// not enough funds for fee
						if (popups)
							popupAvailable(feeView, availableAfterAmount, pending);
					}
				}
				else
				{
					// invalid fee
					if (popups)
						popupMessage(feeView, getString(R.string.send_coins_fragment_amount_error));
				}
			}
			else
			{
				// not enough funds for amount
				if (popups)
					popupAvailable(amountView, available, pending);
			}
		}
		else
		{
			// invalid amount
			if (popups)
				popupMessage(amountView, getString(R.string.send_coins_fragment_amount_error));
		}

		updateView();
	}

	private void popupMessage(final View anchor, final String message)
	{
		dismissPopup();

		popupMessageView.setText(message);
		popupMessageView.setMaxWidth(getView().getWidth());

		popup(anchor, popupMessageView);
	}

	private void popupAvailable(final View anchor, final BigInteger available, final BigInteger pending)
	{
		dismissPopup();

		final CurrencyAmountView viewAvailable = (CurrencyAmountView) popupAvailableView.findViewById(R.id.send_coins_popup_available_amount);
		viewAvailable.setAmount(available);

		final TextView viewPending = (TextView) popupAvailableView.findViewById(R.id.send_coins_popup_available_pending);
		viewPending.setVisibility(pending.signum() > 0 ? View.VISIBLE : View.GONE);
		viewPending.setText(getString(R.string.send_coins_fragment_pending, WalletUtils.formatValue(pending)));

		popup(anchor, popupAvailableView);
	}

	private void popup(final View anchor, final View contentView)
	{
		contentView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0), MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0));

		popupWindow = new PopupWindow(contentView, contentView.getMeasuredWidth(), contentView.getMeasuredHeight(), false);
		popupWindow.showAsDropDown(anchor);

		// hack
		contentView.setBackgroundResource(popupWindow.isAboveAnchor() ? R.drawable.popup_frame_above : R.drawable.popup_frame_below);
	}

	private void dismissPopup()
	{
		if (popupWindow != null)
		{
			popupWindow.dismiss();
			popupWindow = null;
		}
	}

	private void handleGo()
	{
		final BigInteger amount = amountView.getAmount();
		final BigInteger fee = feeView.getAmount();

		System.out.println("about to send " + amount + " (" + Constants.CURRENCY_CODE_BITCOIN + " " + WalletUtils.formatValue(amount) + ") to "
				+ validatedAddress);

		sentTransaction = service.sendCoins(validatedAddress, amount, fee);

		if (sentTransaction != null)
		{
			state = State.SENDING;
			updateView();

			final WalletBalanceFragment balanceFragment = (WalletBalanceFragment) activity.getSupportFragmentManager().findFragmentById(
					R.id.wallet_balance_fragment);
			if (balanceFragment != null)
				balanceFragment.updateView();

			final Intent result = new Intent();
			BitcoinIntegration.transactionHashToResult(result, sentTransaction.getHashAsString());
			activity.setResult(Activity.RESULT_OK, result);

			sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

			// final String label = AddressBookProvider.resolveLabel(contentResolver, validatedAddress.toString());
			// if (label == null)
			// showAddAddressDialog(validatedAddress.toString(), receivingLabel);
		}
		else
		{
			activity.longToast(R.string.send_coins_error_msg);
		}
	}

	public class AutoCompleteAddressAdapter extends CursorAdapter
	{
		public AutoCompleteAddressAdapter(final Context context, final Cursor c)
		{
			super(context, c);
		}

		@Override
		public View newView(final Context context, final Cursor cursor, final ViewGroup parent)
		{
			final LayoutInflater inflater = LayoutInflater.from(context);
			return inflater.inflate(R.layout.address_book_row, parent, false);
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor)
		{
			final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
			final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));

			final ViewGroup viewGroup = (ViewGroup) view;
			final TextView labelView = (TextView) viewGroup.findViewById(R.id.address_book_row_label);
			labelView.setText(label);
			final TextView addressView = (TextView) viewGroup.findViewById(R.id.address_book_row_address);
			addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
		}

		@Override
		public CharSequence convertToString(final Cursor cursor)
		{
			return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
		}

		@Override
		public Cursor runQueryOnBackgroundThread(final CharSequence constraint)
		{
			final Cursor cursor = activity.managedQuery(AddressBookProvider.CONTENT_URI, null, AddressBookProvider.SELECTION_QUERY,
					new String[] { constraint.toString() }, null);
			return cursor;
		}
	}

	private void updateView()
	{
		if (validatedAddress != null)
		{
			receivingAddressView.setVisibility(View.GONE);

			receivingStaticView.setVisibility(View.VISIBLE);
			receivingStaticAddressView.setText(WalletUtils.formatAddress(validatedAddress, Constants.ADDRESS_FORMAT_GROUP_SIZE,
					Constants.ADDRESS_FORMAT_LINE_SIZE));
			final String label = AddressBookProvider.resolveLabel(contentResolver, validatedAddress.toString());
			receivingStaticLabelView.setText(label != null ? label
					: (receivingLabel != null ? receivingLabel : getString(R.string.address_unlabeled)));
			receivingStaticLabelView.setTextColor(label != null ? R.color.fg_significant : R.color.fg_insignificant);
		}
		else
		{
			receivingStaticView.setVisibility(View.GONE);

			receivingAddressView.setVisibility(View.VISIBLE);
		}

		receivingAddressView.setEnabled(state == State.INPUT);

		receivingStaticView.setEnabled(state == State.INPUT);

		amountView.setEnabled(state == State.INPUT);

		feeView.setEnabled(state == State.INPUT);

		sentTransactionListAdapter.clear();
		if (sentTransaction != null)
			sentTransactionListAdapter.add(sentTransaction);

		viewGo.setEnabled(everythingValid());
		if (state == State.INPUT)
			viewGo.setText(R.string.send_coins_fragment_button_send);
		else if (state == State.SENDING)
			viewGo.setText(R.string.send_coins_sending_msg);
		else if (state == State.SENT)
			viewGo.setText(R.string.send_coins_sent_msg);

		viewCancel.setEnabled(state != State.SENDING);
		viewCancel.setText(state != State.SENT ? R.string.button_cancel : R.string.send_coins_fragment_button_back);
	}

	private boolean everythingValid()
	{
		return state == State.INPUT && validatedAddress != null && isValidAmounts;
	}

	public void update(final String receivingAddress, final String receivingLabel, final BigInteger amount)
	{
		try
		{
			validatedAddress = new Address(Constants.NETWORK_PARAMETERS, receivingAddress);
			this.receivingLabel = receivingLabel;
		}
		catch (final Exception x)
		{
			receivingAddressView.setText(receivingAddress);
			x.printStackTrace();
		}

		if (amount != null)
			amountView.setAmount(amount);

		// focus
		if (receivingAddress != null && amount == null)
			amountView.requestFocus();
		else if (receivingAddress != null && amount != null)
			feeView.requestFocus();

		updateView();

		handler.postDelayed(new Runnable()
		{
			public void run()
			{
				validateReceivingAddress(true);
				validateAmounts(true);
			}
		}, 500);
	}

	private void showAddAddressDialog(final String address, final String suggestedAddressLabel)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(R.string.send_coins_add_address_dialog_title);
		builder.setPositiveButton(R.string.send_coins_add_address_dialog_button_add, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int id)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address, suggestedAddressLabel);
			}
		});
		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	public void useCalculatedAmount(final BigInteger amount)
	{
		amountView.setAmount(amount);
	}
}
