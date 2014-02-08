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

package de.schildbach.wallet.ui.send;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigInteger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bitcoin.protocols.payments.Protos.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.Wallet.SendRequest;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.data.PaymentIntent.Standard;
import de.schildbach.wallet.integration.android.BitcoinIntegration;
import de.schildbach.wallet.offline.DirectPaymentTask;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.AddressAndLabel;
import de.schildbach.wallet.ui.CurrencyAmountView;
import de.schildbach.wallet.ui.CurrencyCalculatorLink;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.EditAddressBookEntryFragment;
import de.schildbach.wallet.ui.ExchangeRateLoader;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.InputParser.StreamInputParser;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.ProgressDialogFragment;
import de.schildbach.wallet.ui.ScanActivity;
import de.schildbach.wallet.ui.TransactionsListAdapter;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.PaymentProtocol;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends Fragment
{
	private AbstractBindServiceActivity activity;
	private WalletApplication application;
	private Configuration config;
	private Wallet wallet;
	private ContentResolver contentResolver;
	private LoaderManager loaderManager;
	private FragmentManager fragmentManager;
	@CheckForNull
	private BluetoothAdapter bluetoothAdapter;

	private final Handler handler = new Handler();
	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private TextView payeeNameView;
	private TextView payeeOrganizationView;
	private TextView payeeVerifiedByView;
	private AutoCompleteTextView receivingAddressView;
	private View receivingStaticView;
	private TextView receivingStaticAddressView;
	private TextView receivingStaticLabelView;
	private CheckBox directPaymentEnableView;

	private TextView directPaymentMessageView;
	private ListView sentTransactionView;
	private TransactionsListAdapter sentTransactionListAdapter;
	private Button viewGo;
	private Button viewCancel;

	private TextView popupMessageView;
	private PopupWindow popupWindow;

	private CurrencyCalculatorLink amountCalculatorLink;

	private MenuItem scanAction;
	private MenuItem emptyAction;

	private PaymentIntent paymentIntent;

	private AddressAndLabel validatedAddress = null;

	private Boolean directPaymentAck = null;

	private State state = State.INPUT;
	private Transaction sentTransaction = null;

	private static final int ID_RATE_LOADER = 0;

	private static final int REQUEST_CODE_SCAN = 0;
	private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST = 1;
	private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT = 2;

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
		private final Address address;

		public ReceivingAddressActionMode(final Address address)
		{
			this.address = address;
		}

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
			menu.findItem(R.id.send_coins_address_context_clear).setVisible(paymentIntent.mayEditAddress());

			return true;
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
				requestFocusFirst();
		}

		private void handleEditAddress()
		{
			EditAddressBookEntryFragment.edit(fragmentManager, address.toString());
		}

		private void handleClear()
		{
			// switch from static to input
			validatedAddress = null;
			receivingAddressView.setText(null);
			receivingStaticAddressView.setText(null);

			updateView();

			requestFocusFirst();
		}
	}

	private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener()
	{
		@Override
		public void changed()
		{
			updateView();
		}

		@Override
		public void focusChanged(final boolean hasFocus)
		{
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
			return new ExchangeRateLoader(activity, config);
		}

		@Override
		public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
		{
			if (data != null && data.getCount() > 0)
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

	private final DialogInterface.OnClickListener activityDismissListener = new DialogInterface.OnClickListener()
	{
		@Override
		public void onClick(final DialogInterface dialog, final int which)
		{
			activity.finish();
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractBindServiceActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.config = application.getConfiguration();
		this.wallet = application.getWallet();
		this.contentResolver = activity.getContentResolver();
		this.loaderManager = getLoaderManager();
		this.fragmentManager = getFragmentManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
		setHasOptionsMenu(true);

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

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
			final String mimeType = intent.getType();

			if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && intentUri != null
					&& "bitcoin".equals(scheme))
			{
				initStateFromBitcoinUri(intentUri);
			}
			else if ((NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType))
			{
				final NdefMessage ndefMessage = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
				final byte[] ndefMessagePayload = Nfc.extractMimePayload(PaymentProtocol.MIMETYPE_PAYMENTREQUEST, ndefMessage);
				initStateFromPaymentRequest(mimeType, ndefMessagePayload);
			}
			else if ((Intent.ACTION_VIEW.equals(action)) && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType))
			{
				final byte[] paymentRequest = BitcoinIntegration.paymentRequestFromIntent(intent);

				if (intentUri != null)
					initStateFromIntentUri(mimeType, intentUri);
				else if (paymentRequest != null)
					initStateFromPaymentRequest(mimeType, paymentRequest);
				else
					throw new IllegalArgumentException();
			}
			else if (intent.hasExtra(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT))
			{
				initStateFromIntentExtras(intent.getExtras());
			}
			else
			{
				updateStateFrom(PaymentIntent.blank());
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.send_coins_fragment, container);

		payeeNameView = (TextView) view.findViewById(R.id.send_coins_payee_name);
		payeeOrganizationView = (TextView) view.findViewById(R.id.send_coins_payee_organization);
		payeeVerifiedByView = (TextView) view.findViewById(R.id.send_coins_payee_verified_by);

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
				{
					final Address address = paymentIntent.hasAddress() ? paymentIntent.getAddress()
							: (validatedAddress != null ? validatedAddress.address : null);
					if (address != null)
						actionMode = activity.startActionMode(new ReceivingAddressActionMode(address));
				}
				else
				{
					actionMode.finish();
				}
			}
		});

		final CurrencyAmountView btcAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_btc);
		btcAmountView.setCurrencySymbol(config.getBtcPrefix());
		btcAmountView.setInputPrecision(config.getBtcMaxPrecision());
		btcAmountView.setHintPrecision(config.getBtcPrecision());
		btcAmountView.setShift(config.getBtcShift());

		final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_local);
		localAmountView.setInputPrecision(Constants.LOCAL_PRECISION);
		localAmountView.setHintPrecision(Constants.LOCAL_PRECISION);
		amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);
		amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());

		directPaymentEnableView = (CheckBox) view.findViewById(R.id.send_coins_direct_payment_enable);
		directPaymentEnableView.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
			{
				if (paymentIntent.isBluetoothPaymentUrl() && isChecked && !bluetoothAdapter.isEnabled())
				{
					// ask for permission to enable bluetooth
					startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT);
				}
			}
		});

		directPaymentMessageView = (TextView) view.findViewById(R.id.send_coins_direct_payment_message);

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
				isAmountValid();

				if (everythingValid())
					handleGo();
				else
					requestFocusFirst();
			}
		});

		amountCalculatorLink.setNextFocusId(viewGo.getId());

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

		return view;
	}

	@Override
	public void onDestroyView()
	{
		super.onDestroyView();

		config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
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
		outState.putParcelable("payment_intent", paymentIntent);

		outState.putSerializable("state", state);

		if (validatedAddress != null)
			outState.putParcelable("validated_address", validatedAddress);

		if (sentTransaction != null)
			outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());

		if (directPaymentAck != null)
			outState.putBoolean("direct_payment_ack", directPaymentAck);
	}

	private void restoreInstanceState(final Bundle savedInstanceState)
	{
		paymentIntent = (PaymentIntent) savedInstanceState.getParcelable("payment_intent");

		state = (State) savedInstanceState.getSerializable("state");

		validatedAddress = savedInstanceState.getParcelable("validated_address");

		if (savedInstanceState.containsKey("sent_transaction_hash"))
		{
			sentTransaction = wallet.getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
			sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
		}

		if (savedInstanceState.containsKey("direct_payment_ack"))
			directPaymentAck = savedInstanceState.getBoolean("direct_payment_ack");
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
					protected void handlePaymentIntent(final PaymentIntent paymentIntent)
					{
						updateStateFrom(paymentIntent);
					}

					@Override
					protected void handleDirectTransaction(final Transaction transaction) throws VerificationException
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
		else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST)
		{
			if (paymentIntent.isBluetoothPaymentRequestUrl())
				requestPaymentRequest(paymentIntent.paymentRequestUrl);
		}
		else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT)
		{
			if (paymentIntent.isBluetoothPaymentUrl())
				directPaymentEnableView.setChecked(resultCode == Activity.RESULT_OK);
		}
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.send_coins_fragment_options, menu);

		scanAction = menu.findItem(R.id.send_coins_options_scan);
		emptyAction = menu.findItem(R.id.send_coins_options_empty);

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

	private boolean isOutputsValid()
	{
		if (paymentIntent.hasOutputs())
			return true;

		if (validatedAddress != null)
			return true;

		return false;
	}

	private boolean isAmountValid()
	{
		final BigInteger amount = paymentIntent.mayEditAmount() ? amountCalculatorLink.getAmount() : paymentIntent.getAmount();

		return amount != null && amount.signum() > 0;
	}

	private boolean everythingValid()
	{
		return state == State.INPUT && isOutputsValid() && isAmountValid();
	}

	private void requestFocusFirst()
	{
		if (!isOutputsValid())
			receivingAddressView.requestFocus();
		else if (!isAmountValid())
			amountCalculatorLink.requestFocus();
		else if (everythingValid())
			viewGo.requestFocus();
		else
			log.warn("unclear focus");
	}

	private void popupMessage(@Nonnull final View anchor, @Nonnull final String message)
	{
		dismissPopup();

		popupMessageView.setText(message);
		popupMessageView.setMaxWidth(getView().getWidth());

		popup(anchor, popupMessageView);
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

		// final payment intent
		final PaymentIntent finalPaymentIntent = paymentIntent.mergeWithEditedValues(amountCalculatorLink.getAmount(),
				validatedAddress != null ? validatedAddress.address : null);
		final BigInteger finalAmount = finalPaymentIntent.getAmount();

		// prepare send request
		final SendRequest sendRequest = finalPaymentIntent.toSendRequest();
		final Address returnAddress = WalletUtils.pickOldestKey(wallet).toAddress(Constants.NETWORK_PARAMETERS);
		sendRequest.changeAddress = returnAddress;
		sendRequest.emptyWallet = paymentIntent.mayEditAmount() && finalAmount.equals(wallet.getBalance(BalanceType.AVAILABLE));

		new SendCoinsOfflineTask(wallet, backgroundHandler)
		{
			@Override
			protected void onSuccess(final Transaction transaction)
			{
				sentTransaction = transaction;

				state = State.SENDING;
				updateView();

				sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

				final Payment payment = PaymentProtocol.createPaymentMessage(sentTransaction, returnAddress, finalAmount, null,
						paymentIntent.payeeData);

				directPay(payment);

				application.broadcastTransaction(sentTransaction);

				final ComponentName callingActivity = activity.getCallingActivity();
				if (callingActivity != null)
				{
					log.info("returning result to calling activity: {}", callingActivity.flattenToString());

					final Intent result = new Intent();
					BitcoinIntegration.transactionHashToResult(result, sentTransaction.getHashAsString());
					if (paymentIntent.standard == Standard.BIP70)
						BitcoinIntegration.paymentToResult(result, payment.toByteArray());
					activity.setResult(Activity.RESULT_OK, result);
				}
			}

			private void directPay(final Payment payment)
			{
				if (directPaymentEnableView.isChecked())
				{
					final DirectPaymentTask.ResultCallback callback = new DirectPaymentTask.ResultCallback()
					{
						@Override
						public void onResult(final boolean ack)
						{
							directPaymentAck = ack;

							if (state == State.SENDING)
								state = State.SENT;

							updateView();
						}

						@Override
						public void onFail(final int messageResId, final Object... messageArgs)
						{
							final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_direct_payment_failed_title);
							dialog.setMessage(paymentIntent.paymentUrl + "\n" + getString(messageResId, messageArgs) + "\n\n"
									+ getString(R.string.send_coins_fragment_direct_payment_failed_msg));
							dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(final DialogInterface dialog, final int which)
								{
									directPay(payment);
								}
							});
							dialog.setNegativeButton(R.string.button_dismiss, null);
							dialog.show();
						}
					};

					if (paymentIntent.isHttpPaymentUrl())
					{
						new DirectPaymentTask.HttpPaymentTask(backgroundHandler, callback, paymentIntent.paymentUrl, application.httpUserAgent())
								.send(payment);
					}
					else if (paymentIntent.isBluetoothPaymentUrl() && bluetoothAdapter != null && bluetoothAdapter.isEnabled())
					{
						new DirectPaymentTask.BluetoothPaymentTask(backgroundHandler, callback, bluetoothAdapter,
								Bluetooth.getBluetoothMac(paymentIntent.paymentUrl)).send(payment);
					}
				}
			}

			@Override
			protected void onInsufficientMoney(@Nullable final BigInteger missing)
			{
				state = State.INPUT;
				updateView();

				final BigInteger estimated = wallet.getBalance(BalanceType.ESTIMATED);
				final BigInteger available = wallet.getBalance(BalanceType.AVAILABLE);
				final BigInteger pending = estimated.subtract(available);

				final int btcShift = config.getBtcShift();
				final int btcPrecision = config.getBtcMaxPrecision();
				final String btcPrefix = config.getBtcPrefix();

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_insufficient_money_title);
				final StringBuilder msg = new StringBuilder();
				if (missing != null)
					msg.append(
							getString(R.string.send_coins_fragment_insufficient_money_msg1,
									btcPrefix + ' ' + GenericUtils.formatValue(missing, btcPrecision, btcShift))).append("\n\n");
				if (pending.signum() > 0)
					msg.append(
							getString(R.string.send_coins_fragment_pending,
									btcPrefix + ' ' + GenericUtils.formatValue(pending, btcPrecision, btcShift))).append("\n\n");
				msg.append(getString(R.string.send_coins_fragment_insufficient_money_msg2));
				dialog.setMessage(msg);
				dialog.setPositiveButton(R.string.send_coins_options_empty, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						handleEmpty();
					}
				});
				dialog.setNegativeButton(R.string.button_cancel, null);
				dialog.show();
			}

			@Override
			protected void onFailure(@Nonnull Exception exception)
			{
				state = State.FAILED;
				updateView();

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_error_msg);
				dialog.setMessage(exception.toString());
				dialog.setNeutralButton(R.string.button_dismiss, null);
				dialog.show();
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

		updateView();
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
		if (paymentIntent != null)
		{
			getView().setVisibility(View.VISIBLE);

			if (paymentIntent.hasPayee())
			{
				payeeNameView.setVisibility(View.VISIBLE);
				payeeNameView.setText(paymentIntent.payeeName);

				if (paymentIntent.payeeOrganization != null)
				{
					payeeOrganizationView.setVisibility(View.VISIBLE);
					payeeOrganizationView.setText(paymentIntent.payeeOrganization);
				}
				else
				{
					payeeOrganizationView.setVisibility(View.GONE);
				}

				payeeVerifiedByView.setVisibility(View.VISIBLE);
				final String verifiedBy = paymentIntent.payeeVerifiedBy != null ? paymentIntent.payeeVerifiedBy
						: getString(R.string.send_coins_fragment_payee_verified_by_unknown);
				payeeVerifiedByView.setText(Constants.CHAR_CHECKMARK
						+ String.format(getString(R.string.send_coins_fragment_payee_verified_by), verifiedBy));
			}
			else
			{
				payeeNameView.setVisibility(View.GONE);
				payeeOrganizationView.setVisibility(View.GONE);
				payeeVerifiedByView.setVisibility(View.GONE);
			}

			if (paymentIntent.hasOutputs())
			{
				receivingAddressView.setVisibility(View.GONE);
				receivingStaticView.setVisibility(View.VISIBLE);

				receivingStaticLabelView.setText(paymentIntent.memo);

				if (paymentIntent.hasAddress())
					receivingStaticAddressView.setText(WalletUtils.formatAddress(paymentIntent.getAddress(), Constants.ADDRESS_FORMAT_GROUP_SIZE,
							Constants.ADDRESS_FORMAT_LINE_SIZE));
				else
					receivingStaticAddressView.setText(R.string.send_coins_fragment_receiving_address_complex);
			}
			else if (validatedAddress != null)
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

			amountCalculatorLink.setEnabled(state == State.INPUT && paymentIntent.mayEditAmount());

			final boolean directPaymentVisible;
			if (paymentIntent.hasPaymentUrl())
			{
				if (paymentIntent.isBluetoothPaymentUrl())
					directPaymentVisible = bluetoothAdapter != null;
				else
					directPaymentVisible = !Constants.BUG_OPENSSL_HEARTBLEED;
			}
			else
			{
				directPaymentVisible = false;
			}
			directPaymentEnableView.setVisibility(directPaymentVisible ? View.VISIBLE : View.GONE);
			directPaymentEnableView.setEnabled(state == State.INPUT);

			if (sentTransaction != null)
			{
				final int btcPrecision = config.getBtcPrecision();
				final int btcShift = config.getBtcShift();

				sentTransactionView.setVisibility(View.VISIBLE);
				sentTransactionListAdapter.setPrecision(btcPrecision, btcShift);
				sentTransactionListAdapter.replace(sentTransaction);
			}
			else
			{
				sentTransactionView.setVisibility(View.GONE);
				sentTransactionListAdapter.clear();
			}

			if (directPaymentAck != null)
			{
				directPaymentMessageView.setVisibility(View.VISIBLE);
				directPaymentMessageView.setText(directPaymentAck ? R.string.send_coins_fragment_direct_payment_ack
						: R.string.send_coins_fragment_direct_payment_nack);
			}
			else
			{
				directPaymentMessageView.setVisibility(View.GONE);
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

			// enable actions
			if (scanAction != null)
				scanAction.setEnabled(state == State.INPUT);
			if (emptyAction != null)
				emptyAction.setEnabled(state == State.INPUT);

			// focus linking
			final int activeAmountViewId = amountCalculatorLink.activeTextView().getId();
			receivingAddressView.setNextFocusDownId(activeAmountViewId);
			receivingStaticView.setNextFocusDownId(activeAmountViewId);
			GenericUtils.setNextFocusForwardId(receivingAddressView, activeAmountViewId);
			viewGo.setNextFocusUpId(activeAmountViewId);
		}
		else
		{
			getView().setVisibility(View.GONE);
		}
	}

	private void initStateFromIntentExtras(@Nonnull final Bundle extras)
	{
		final PaymentIntent paymentIntent = extras.getParcelable(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT);

		updateStateFrom(paymentIntent);
	}

	private void initStateFromBitcoinUri(@Nonnull final Uri bitcoinUri)
	{
		final String input = bitcoinUri.toString();

		new StringInputParser(input)
		{
			@Override
			protected void handlePaymentIntent(@Nonnull final PaymentIntent paymentIntent)
			{
				updateStateFrom(paymentIntent);
			}

			@Override
			protected void handlePrivateKey(@Nonnull final ECKey key)
			{
				throw new UnsupportedOperationException();
			}

			@Override
			protected void handleDirectTransaction(@Nonnull final Transaction transaction) throws VerificationException
			{
				throw new UnsupportedOperationException();
			}

			@Override
			protected void error(final int messageResId, final Object... messageArgs)
			{
				dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
			}
		}.parse();
	}

	private void initStateFromPaymentRequest(@Nonnull final String mimeType, @Nonnull final byte[] input)
	{
		new BinaryInputParser(mimeType, input)
		{
			@Override
			protected void handlePaymentIntent(final PaymentIntent paymentIntent)
			{
				updateStateFrom(paymentIntent);
			}

			@Override
			protected void error(final int messageResId, final Object... messageArgs)
			{
				dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
			}
		}.parse();
	}

	private void initStateFromIntentUri(@Nonnull final String mimeType, @Nonnull final Uri bitcoinUri)
	{
		try
		{
			final InputStream is = contentResolver.openInputStream(bitcoinUri);

			new StreamInputParser(mimeType, is)
			{
				@Override
				protected void handlePaymentIntent(final PaymentIntent paymentIntent)
				{
					updateStateFrom(paymentIntent);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
				}
			}.parse();
		}
		catch (final FileNotFoundException x)
		{
			throw new RuntimeException(x);
		}
	}

	private void updateStateFrom(final @Nonnull PaymentIntent paymentIntent)
	{
		log.info("got {}", paymentIntent);

		this.paymentIntent = paymentIntent;

		directPaymentAck = null;

		// delay these actions until fragment is resumed
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				if (state == State.INPUT)
				{
					amountCalculatorLink.setBtcAmount(paymentIntent.getAmount());

					if (paymentIntent.isBluetoothPaymentUrl())
						directPaymentEnableView.setChecked(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
					else if (paymentIntent.isHttpPaymentUrl())
						directPaymentEnableView.setChecked(!Constants.BUG_OPENSSL_HEARTBLEED);

					requestFocusFirst();
					updateView();
				}

				if (paymentIntent.hasPaymentRequestUrl())
				{
					if (paymentIntent.isBluetoothPaymentRequestUrl() && !Constants.BUG_OPENSSL_HEARTBLEED)
					{
						if (bluetoothAdapter.isEnabled())
							requestPaymentRequest(paymentIntent.paymentRequestUrl);
						else
							// ask for permission to enable bluetooth
							startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
									REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST);
					}
					else if (paymentIntent.isHttpPaymentRequestUrl())
					{
						requestPaymentRequest(paymentIntent.paymentRequestUrl);
					}
				}
			}
		});
	}

	private void requestPaymentRequest(final String paymentRequestUrl)
	{
		final String host;
		if (!Bluetooth.isBluetoothUrl(paymentRequestUrl))
			host = Uri.parse(paymentRequestUrl).getHost();
		else
			host = Bluetooth.decompressMac(Bluetooth.getBluetoothMac(paymentRequestUrl));

		ProgressDialogFragment.showProgress(fragmentManager, getString(R.string.send_coins_fragment_request_payment_request_progress, host));

		final RequestPaymentRequestTask.ResultCallback callback = new RequestPaymentRequestTask.ResultCallback()
		{
			@Override
			public void onPaymentIntent(final PaymentIntent paymentIntent)
			{
				ProgressDialogFragment.dismissProgress(fragmentManager);

				if (SendCoinsFragment.this.paymentIntent.isExtendedBy(paymentIntent))
				{
					updateStateFrom(paymentIntent);
					updateView();
				}
				else
				{
					final StringBuilder reasons = new StringBuilder();
					if (!SendCoinsFragment.this.paymentIntent.equalsAddress(paymentIntent))
						reasons.append("address");
					if (!SendCoinsFragment.this.paymentIntent.equalsAmount(paymentIntent))
						reasons.append(reasons.length() == 0 ? "" : ", ").append("amount");
					if (reasons.length() == 0)
						reasons.append("unknown");

					final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_request_payment_request_failed_title);
					dialog.setMessage(getString(R.string.send_coins_fragment_request_payment_request_wrong_signature) + "\n\n" + reasons);
					dialog.singleDismissButton(null);
					dialog.show();

					log.info("BIP72 trust check failed: {}", reasons);
				}
			}

			@Override
			public void onFail(final int messageResId, final Object... messageArgs)
			{
				ProgressDialogFragment.dismissProgress(fragmentManager);

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_request_payment_request_failed_title);
				dialog.setMessage(getString(messageResId, messageArgs));
				dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						requestPaymentRequest(paymentRequestUrl);
					}
				});
				dialog.setNegativeButton(R.string.button_dismiss, null);
				dialog.show();
			}
		};

		if (!Bluetooth.isBluetoothUrl(paymentRequestUrl))
			new RequestPaymentRequestTask.HttpRequestTask(backgroundHandler, callback, application.httpUserAgent())
					.requestPaymentRequest(paymentRequestUrl);
		else
			new RequestPaymentRequestTask.BluetoothRequestTask(backgroundHandler, callback, bluetoothAdapter)
					.requestPaymentRequest(paymentRequestUrl);
	}
}
