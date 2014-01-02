/*
 * Copyright 2011-2014 the original author or authors.
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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
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
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.Wallet.SendRequest;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.integration.android.BitcoinIntegration;
import de.schildbach.wallet.offline.SendBluetoothTask;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends SherlockFragment
{
	private AbstractBindServiceActivity activity;
	private WalletApplication application;
	private Wallet wallet;
	private ContentResolver contentResolver;
	private LoaderManager loaderManager;
	private SharedPreferences prefs;
	@CheckForNull
	private BluetoothAdapter bluetoothAdapter;

	private int btcPrecision;
	private int btcShift;

	private final Handler handler = new Handler();
	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private AutoCompleteTextView receivingAddressView;
	private View receivingStaticView;
	private TextView receivingStaticAddressView;
	private TextView receivingStaticLabelView;
	private CheckBox bluetoothEnableView;

	private TextView bluetoothMessageView;
	private ListView sentTransactionView;
	private TransactionsListAdapter sentTransactionListAdapter;
	private Button viewGo;
	private Button viewCancel;

	private TextView popupMessageView;
	private View popupAvailableView;
	private PopupWindow popupWindow;

	private CurrencyCalculatorLink amountCalculatorLink;

	private MenuItem scanAction;

	private AddressAndLabel validatedAddress = null;
	private boolean isValidAmounts = false;

	@CheckForNull
	private String bluetoothMac;
	private Boolean bluetoothAck = null;

	private State state = State.INPUT;
	private Transaction sentTransaction = null;

	private static final int ID_RATE_LOADER = 0;

	private static final int REQUEST_CODE_SCAN = 0;
	private static final int REQUEST_CODE_ENABLE_BLUETOOTH = 1;

	private static final Logger log = LoggerFactory.getLogger(SendCoinsFragment.class);

	private enum State
	{
		INPUT, PREPARATION, SENDING, SENT, FAILED
	}

	private final class ReceivingAddressListener implements OnFocusChangeListener, TextWatcher
	{
		@Override
		public void onFocusChange(final View v, final boolean hasFocus)
		{
			if (!hasFocus)
				validateReceivingAddress(true);
		}

		@Override
		public void afterTextChanged(final Editable s)
		{
			dismissPopup();

			validateReceivingAddress(false);
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
		}
	}

	private final ReceivingAddressListener receivingAddressListener = new ReceivingAddressListener();

	private final class ReceivingAddressActionMode implements ActionMode.Callback
	{
		@Override
		public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
		{
			final MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.send_coins_address_context, menu);

			return true;
		}

		@Override
		public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
		{
			return false;
		}

		@Override
		public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
		{
			switch (item.getItemId())
			{
				case R.id.send_coins_address_context_edit_address:
					handleEditAddress();

					mode.finish();
					return true;

				case R.id.send_coins_address_context_clear:
					handleClear();

					mode.finish();
					return true;
			}

			return false;
		}

		@Override
		public void onDestroyActionMode(final ActionMode mode)
		{
			if (receivingStaticView.hasFocus())
				amountCalculatorLink.requestFocus();
		}

		private void handleEditAddress()
		{
			EditAddressBookEntryFragment.edit(getFragmentManager(), validatedAddress.address.toString());
		}

		private void handleClear()
		{
			// switch from static to input
			validatedAddress = null;
			receivingAddressView.setText(null);
			receivingStaticAddressView.setText(null);

			updateView();

			receivingAddressView.requestFocus();
		}
	}

	private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener()
	{
		@Override
		public void changed()
		{
			dismissPopup();

			validateAmounts(false);
		}

		@Override
		public void done()
		{
			validateAmounts(true);

			viewGo.requestFocusFromTouch();
		}

		@Override
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
		@Override
		public void onConfidenceChanged(final Transaction tx, final TransactionConfidence.Listener.ChangeReason reason)
		{
			activity.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					sentTransactionListAdapter.notifyDataSetChanged();

					final TransactionConfidence confidence = sentTransaction.getConfidence();
					final ConfidenceType confidenceType = confidence.getConfidenceType();
					final int numBroadcastPeers = confidence.numBroadcastPeers();

					if (state == State.SENDING)
					{
						if (confidenceType == ConfidenceType.DEAD)
							state = State.FAILED;
						else if (numBroadcastPeers > 1 || confidenceType == ConfidenceType.BUILDING)
							state = State.SENT;

						updateView();
					}

					if (reason == ChangeReason.SEEN_PEERS && confidenceType == ConfidenceType.PENDING)
					{
						// play sound effect
						final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers, "raw",
								activity.getPackageName());
						if (soundResId > 0)
							RingtoneManager.getRingtone(activity, Uri.parse("android.resource://" + activity.getPackageName() + "/" + soundResId))
									.play();
					}
				}
			});
		}
	};

	private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		@Override
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			return new ExchangeRateLoader(activity);
		}

		@Override
		public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
		{
			if (data != null)
			{
				data.moveToFirst();
				final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);

				if (state == State.INPUT)
					amountCalculatorLink.setExchangeRate(exchangeRate);
			}
		}

		@Override
		public void onLoaderReset(final Loader<Cursor> loader)
		{
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractBindServiceActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.wallet = application.getWallet();
		this.contentResolver = activity.getContentResolver();
		this.loaderManager = getLoaderManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
		btcPrecision = precision.charAt(0) - '0';
		btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;
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

			@Override
			public void onFocusChange(final View v, final boolean hasFocus)
			{
				if (hasFocus)
					actionMode = activity.startActionMode(new ReceivingAddressActionMode());
				else
					actionMode.finish();
			}
		});

		final CurrencyAmountView btcAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_btc);
		btcAmountView.setCurrencySymbol(btcShift == 0 ? Constants.CURRENCY_CODE_BTC : Constants.CURRENCY_CODE_MBTC);
		btcAmountView.setInputPrecision(btcShift == 0 ? Constants.BTC_MAX_PRECISION : Constants.MBTC_MAX_PRECISION);
		btcAmountView.setHintPrecision(btcPrecision);
		btcAmountView.setShift(btcShift);

		final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_local);
		localAmountView.setInputPrecision(Constants.LOCAL_PRECISION);
		localAmountView.setHintPrecision(Constants.LOCAL_PRECISION);
		amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);

		bluetoothEnableView = (CheckBox) view.findViewById(R.id.send_coins_bluetooth_enable);
		bluetoothEnableView.setChecked(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
		bluetoothEnableView.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
			{
				if (isChecked && !bluetoothAdapter.isEnabled())
				{
					// try to enable bluetooth
					startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_ENABLE_BLUETOOTH);
				}
			}
		});

		bluetoothMessageView = (TextView) view.findViewById(R.id.send_coins_bluetooth_message);

		sentTransactionView = (ListView) view.findViewById(R.id.send_coins_sent_transaction);
		sentTransactionListAdapter = new TransactionsListAdapter(activity, wallet, application.maxConnectedPeers(), false);
		sentTransactionView.setAdapter(sentTransactionListAdapter);

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				validateReceivingAddress(true);
				validateAmounts(true);

				if (everythingValid())
					handleGo();
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (state == State.INPUT)
					activity.setResult(Activity.RESULT_CANCELED);

				activity.finish();
			}
		});

		popupMessageView = (TextView) inflater.inflate(R.layout.send_coins_popup_message, container);

		popupAvailableView = inflater.inflate(R.layout.send_coins_popup_available, container);

		if (savedInstanceState != null)
		{
			restoreInstanceState(savedInstanceState);
		}
		else
		{
			final Intent intent = activity.getIntent();
			final String action = intent.getAction();
			final Uri intentUri = intent.getData();
			final String scheme = intentUri != null ? intentUri.getScheme() : null;

			if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && intentUri != null
					&& "bitcoin".equals(scheme))
				initStateFromBitcoinUri(intentUri);
			else if (intent.hasExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS))
				initStateFromIntentExtras(intent.getExtras());
		}

		return view;
	}

	private void initStateFromIntentExtras(@Nonnull final Bundle extras)
	{
		final String address = extras.getString(SendCoinsActivity.INTENT_EXTRA_ADDRESS);
		final String addressLabel = extras.getString(SendCoinsActivity.INTENT_EXTRA_ADDRESS_LABEL);
		final BigInteger amount = (BigInteger) extras.getSerializable(SendCoinsActivity.INTENT_EXTRA_AMOUNT);
		final String bluetoothMac = extras.getString(SendCoinsActivity.INTENT_EXTRA_BLUETOOTH_MAC);

		update(address, addressLabel, amount, bluetoothMac);
	}

	private void initStateFromBitcoinUri(@Nonnull final Uri bitcoinUri)
	{
		final String input = bitcoinUri.toString();

		new StringInputParser(input)
		{
			@Override
			protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
			{
				update(address.toString(), addressLabel, amount, bluetoothMac);
			}

			@Override
			protected void directTransaction(final Transaction transaction)
			{
				cannotClassify(input);
			}

			@Override
			protected void error(final int messageResId, final Object... messageArgs)
			{
				dialog(activity, dismissListener, 0, messageResId, messageArgs);
			}

			private final DialogInterface.OnClickListener dismissListener = new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int which)
				{
					activity.finish();
				}
			};
		}.parse();
	}

	@Override
	public void onResume()
	{
		super.onResume();

		contentResolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true, contentObserver);

		amountCalculatorLink.setListener(amountsListener);

		loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

		updateView();
	}

	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_RATE_LOADER);

		amountCalculatorLink.setListener(null);

		contentResolver.unregisterContentObserver(contentObserver);

		super.onPause();
	}

	@Override
	public void onDetach()
	{
		handler.removeCallbacksAndMessages(null);

		super.onDetach();
	}

	@Override
	public void onDestroy()
	{
		backgroundThread.getLooper().quit();

		if (sentTransaction != null)
			sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);

		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);

		saveInstanceState(outState);
	}

	private void saveInstanceState(final Bundle outState)
	{
		outState.putSerializable("state", state);

		if (validatedAddress != null)
			outState.putParcelable("validated_address", validatedAddress);

		outState.putBoolean("is_valid_amounts", isValidAmounts);

		if (sentTransaction != null)
			outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());

		outState.putString("bluetooth_mac", bluetoothMac);

		if (bluetoothAck != null)
			outState.putBoolean("bluetooth_ack", bluetoothAck);
	}

	private void restoreInstanceState(final Bundle savedInstanceState)
	{
		state = (State) savedInstanceState.getSerializable("state");

		validatedAddress = savedInstanceState.getParcelable("validated_address");

		isValidAmounts = savedInstanceState.getBoolean("is_valid_amounts");

		if (savedInstanceState.containsKey("sent_transaction_hash"))
		{
			sentTransaction = wallet.getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
			sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
		}

		bluetoothMac = savedInstanceState.getString("bluetooth_mac");

		if (savedInstanceState.containsKey("bluetooth_ack"))
			bluetoothAck = savedInstanceState.getBoolean("bluetooth_ack");
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN)
		{
			if (resultCode == Activity.RESULT_OK)
			{
				final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

				new StringInputParser(input)
				{
					@Override
					protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
					{
						SendCoinsActivity.start(activity, address != null ? address.toString() : null, addressLabel, amount, bluetoothMac);
					}

					@Override
					protected void directTransaction(final Transaction transaction)
					{
						cannotClassify(input);
					}

					@Override
					protected void error(final int messageResId, final Object... messageArgs)
					{
						dialog(activity, null, R.string.button_scan, messageResId, messageArgs);
					}
				}.parse();
			}
		}
		else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH)
		{
			bluetoothEnableView.setChecked(resultCode == Activity.RESULT_OK);
		}
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.send_coins_fragment_options, menu);

		scanAction = menu.findItem(R.id.send_coins_options_scan);

		final PackageManager pm = activity.getPackageManager();
		scanAction.setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.send_coins_options_scan:
				handleScan();
				return true;

			case R.id.send_coins_options_empty:
				handleEmpty();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void validateReceivingAddress(final boolean popups)
	{
		try
		{
			final String addressStr = receivingAddressView.getText().toString().trim();
			if (!addressStr.isEmpty())
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
					final String label = AddressBookProvider.resolveLabel(activity, addressStr);
					validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, addressStr, label);
					receivingAddressView.setText(null);
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

		final BigInteger amount = amountCalculatorLink.getAmount();

		if (amount == null)
		{
			// empty amount
			if (popups)
				popupMessage(amountCalculatorLink.activeView(), getString(R.string.send_coins_fragment_amount_empty));
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
				// everything fine
				isValidAmounts = true;
			}
			else
			{
				// not enough funds for amount
				if (popups)
					popupAvailable(amountCalculatorLink.activeView(), available, pending);
			}
		}
		else
		{
			// invalid amount
			if (popups)
				popupMessage(amountCalculatorLink.activeView(), getString(R.string.send_coins_fragment_amount_error));
		}

		updateView();
	}

	private void popupMessage(@Nonnull final View anchor, @Nonnull final String message)
	{
		dismissPopup();

		popupMessageView.setText(message);
		popupMessageView.setMaxWidth(getView().getWidth());

		popup(anchor, popupMessageView);
	}

	private void popupAvailable(@Nonnull final View anchor, @Nonnull final BigInteger available, @Nonnull final BigInteger pending)
	{
		dismissPopup();

		final CurrencyTextView viewAvailable = (CurrencyTextView) popupAvailableView.findViewById(R.id.send_coins_popup_available_amount);
		viewAvailable.setPrefix(btcShift == 0 ? Constants.CURRENCY_CODE_BTC : Constants.CURRENCY_CODE_MBTC);
		viewAvailable.setPrecision(btcPrecision, btcShift);
		viewAvailable.setAmount(available);

		final TextView viewPending = (TextView) popupAvailableView.findViewById(R.id.send_coins_popup_available_pending);
		viewPending.setVisibility(pending.signum() > 0 ? View.VISIBLE : View.GONE);
		final int precision = btcShift == 0 ? Constants.BTC_MAX_PRECISION : Constants.MBTC_MAX_PRECISION;
		viewPending.setText(getString(R.string.send_coins_fragment_pending, GenericUtils.formatValue(pending, precision, btcShift)));

		popup(anchor, popupAvailableView);
	}

	private void popup(@Nonnull final View anchor, @Nonnull final View contentView)
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
		state = State.PREPARATION;
		updateView();

		// create spend
		final BigInteger amount = amountCalculatorLink.getAmount();
		final SendRequest sendRequest = SendRequest.to(validatedAddress.address, amount);
		sendRequest.changeAddress = WalletUtils.pickOldestKey(wallet).toAddress(Constants.NETWORK_PARAMETERS);
		sendRequest.emptyWallet = amount.equals(wallet.getBalance(BalanceType.AVAILABLE));

		new SendCoinsOfflineTask(wallet, backgroundHandler)
		{
			@Override
			protected void onSuccess(final Transaction transaction)
			{
				sentTransaction = transaction;

				state = State.SENDING;
				updateView();

				sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

				if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothMac != null && bluetoothEnableView.isChecked())
				{
					new SendBluetoothTask(bluetoothAdapter, backgroundHandler)
					{
						@Override
						protected void onResult(final boolean ack)
						{
							bluetoothAck = ack;

							if (state == State.SENDING)
								state = State.SENT;

							updateView();
						}
					}.send(bluetoothMac, transaction); // send asynchronously
				}

				application.broadcastTransaction(sentTransaction);

				final Intent result = new Intent();
				BitcoinIntegration.transactionHashToResult(result, sentTransaction.getHashAsString());
				activity.setResult(Activity.RESULT_OK, result);
			}

			@Override
			protected void onFailure()
			{
				state = State.FAILED;
				updateView();

				activity.longToast(R.string.send_coins_error_msg);
			}
		}.sendCoinsOffline(sendRequest); // send asynchronously
	}

	private void handleScan()
	{
		startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	private void handleEmpty()
	{
		final BigInteger available = wallet.getBalance(BalanceType.AVAILABLE);

		amountCalculatorLink.setBtcAmount(available);
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
			addressView.setText(WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
		}

		@Override
		public CharSequence convertToString(final Cursor cursor)
		{
			return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
		}

		@Override
		public Cursor runQueryOnBackgroundThread(final CharSequence constraint)
		{
			final Cursor cursor = activity.managedQuery(AddressBookProvider.contentUri(activity.getPackageName()), null,
					AddressBookProvider.SELECTION_QUERY, new String[] { constraint.toString() }, null);
			return cursor;
		}
	}

	private void updateView()
	{
		if (validatedAddress != null)
		{
			receivingAddressView.setVisibility(View.GONE);

			receivingStaticView.setVisibility(View.VISIBLE);
			receivingStaticAddressView.setText(WalletUtils.formatAddress(validatedAddress.address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
					Constants.ADDRESS_FORMAT_LINE_SIZE));
			final String addressBookLabel = AddressBookProvider.resolveLabel(activity, validatedAddress.address.toString());
			final String staticLabel;
			if (addressBookLabel != null)
				staticLabel = addressBookLabel;
			else if (validatedAddress.label != null)
				staticLabel = validatedAddress.label;
			else
				staticLabel = getString(R.string.address_unlabeled);
			receivingStaticLabelView.setText(staticLabel);
			receivingStaticLabelView.setTextColor(getResources().getColor(
					validatedAddress.label != null ? R.color.fg_significant : R.color.fg_insignificant));
		}
		else
		{
			receivingStaticView.setVisibility(View.GONE);

			receivingAddressView.setVisibility(View.VISIBLE);
		}

		receivingAddressView.setEnabled(state == State.INPUT);

		receivingStaticView.setEnabled(state == State.INPUT);

		amountCalculatorLink.setEnabled(state == State.INPUT);

		bluetoothEnableView.setVisibility(bluetoothAdapter != null && bluetoothMac != null ? View.VISIBLE : View.GONE);
		bluetoothEnableView.setEnabled(state == State.INPUT);

		if (sentTransaction != null)
		{
			final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
			final int btcPrecision = precision.charAt(0) - '0';
			final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

			sentTransactionView.setVisibility(View.VISIBLE);
			sentTransactionListAdapter.setPrecision(btcPrecision, btcShift);
			sentTransactionListAdapter.replace(sentTransaction);
		}
		else
		{
			sentTransactionView.setVisibility(View.GONE);
			sentTransactionListAdapter.clear();
		}

		if (bluetoothAck != null)
		{
			bluetoothMessageView.setVisibility(View.VISIBLE);
			bluetoothMessageView.setText(bluetoothAck ? R.string.send_coins_fragment_bluetooth_ack : R.string.send_coins_fragment_bluetooth_nack);
		}
		else
		{
			bluetoothMessageView.setVisibility(View.GONE);
		}

		viewCancel.setEnabled(state != State.PREPARATION);
		viewGo.setEnabled(everythingValid());

		if (state == State.INPUT)
		{
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.send_coins_fragment_button_send);
		}
		else if (state == State.PREPARATION)
		{
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.send_coins_preparation_msg);
		}
		else if (state == State.SENDING)
		{
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_sending_msg);
		}
		else if (state == State.SENT)
		{
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_sent_msg);
		}
		else if (state == State.FAILED)
		{
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_failed_msg);
		}

		if (scanAction != null)
			scanAction.setEnabled(state == State.INPUT);
	}

	private boolean everythingValid()
	{
		return state == State.INPUT && validatedAddress != null && isValidAmounts;
	}

	public void update(final String receivingAddress, final String receivingLabel, @Nullable final BigInteger amount,
			@Nullable final String bluetoothMac)
	{
		try
		{
			validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, receivingAddress, receivingLabel);
			receivingAddressView.setText(null);
		}
		catch (final Exception x)
		{
			receivingAddressView.setText(receivingAddress);
			validatedAddress = null;
			log.info("problem parsing address: '" + receivingAddress + "'", x);
		}

		if (amount != null)
			amountCalculatorLink.setBtcAmount(amount);

		// focus
		if (receivingAddress != null && amount == null)
			amountCalculatorLink.requestFocus();
		else if (receivingAddress != null && amount != null)
			viewGo.requestFocus();

		this.bluetoothMac = bluetoothMac;

		bluetoothAck = null;

		updateView();

		handler.postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				validateReceivingAddress(true);
				validateAmounts(true);
			}
		}, 500);
	}
}
