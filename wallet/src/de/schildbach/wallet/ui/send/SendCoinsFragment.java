/*
 * Copyright 2011-2015 the original author or authors.
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
import java.util.Arrays;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.bitcoin.protocols.payments.Protos.Payment;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.VersionedChecksummedBytes;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.BalanceType;
import org.bitcoinj.core.Wallet.CouldNotAdjustDownwards;
import org.bitcoinj.core.Wallet.DustySendRequested;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
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
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;
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
import de.schildbach.wallet.util.Nfc;
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
	private TextView payeeVerifiedByView;
	private AutoCompleteTextView receivingAddressView;
	private ReceivingAddressViewAdapter receivingAddressViewAdapter;
	private View receivingStaticView;
	private TextView receivingStaticAddressView;
	private TextView receivingStaticLabelView;
	private CurrencyCalculatorLink amountCalculatorLink;
	private CheckBox directPaymentEnableView;

	private TextView hintView;
	private TextView directPaymentMessageView;
	private ListView sentTransactionView;
	private TransactionsListAdapter sentTransactionListAdapter;
	private View privateKeyPasswordViewGroup;
	private EditText privateKeyPasswordView;
	private View privateKeyBadPasswordView;
	private Button viewGo;
	private Button viewCancel;

	private State state = State.INPUT;

	private PaymentIntent paymentIntent = null;
	private boolean priority = false;
	private AddressAndLabel validatedAddress = null;

	private Transaction sentTransaction = null;
	private Boolean directPaymentAck = null;

	private Transaction dryrunTransaction;
	private Exception dryrunException;

	private static final int ID_RATE_LOADER = 0;
	private static final int ID_RECEIVING_ADDRESS_LOADER = 1;

	private static final int REQUEST_CODE_SCAN = 0;
	private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST = 1;
	private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT = 2;

	private static final Logger log = LoggerFactory.getLogger(SendCoinsFragment.class);

	private enum State
	{
		INPUT, DECRYPTING, SIGNING, SENDING, SENT, FAILED
	}

	private final class ReceivingAddressListener implements OnFocusChangeListener, TextWatcher
	{
		@Override
		public void onFocusChange(final View v, final boolean hasFocus)
		{
			if (!hasFocus)
			{
				validateReceivingAddress();
				updateView();
			}
		}

		@Override
		public void afterTextChanged(final Editable s)
		{
			if (s.length() > 0)
				validateReceivingAddress();
			else
				updateView();
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
			handler.post(dryrunRunnable);
		}

		@Override
		public void focusChanged(final boolean hasFocus)
		{
		}
	};

	private final TextWatcher privateKeyPasswordListener = new TextWatcher()
	{
		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
			privateKeyBadPasswordView.setVisibility(View.INVISIBLE);
			updateView();
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		@Override
		public void afterTextChanged(final Editable s)
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
					if (!isResumed())
						return;

					sentTransactionListAdapter.notifyDataSetChanged();

					final TransactionConfidence confidence = sentTransaction.getConfidence();
					final ConfidenceType confidenceType = confidence.getConfidenceType();
					final int numBroadcastPeers = confidence.numBroadcastPeers();

					if (state == State.SENDING)
					{
						if (confidenceType == ConfidenceType.DEAD)
							setState(State.FAILED);
						else if (numBroadcastPeers > 1 || confidenceType == ConfidenceType.BUILDING)
							setState(State.SENT);
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
					amountCalculatorLink.setExchangeRate(exchangeRate.rate);
			}
		}

		@Override
		public void onLoaderReset(final Loader<Cursor> loader)
		{
		}
	};

	private final LoaderCallbacks<Cursor> receivingAddressLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		@Override
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			final String constraint = args != null ? args.getString("constraint") : null;
			return new CursorLoader(activity, AddressBookProvider.contentUri(activity.getPackageName()), null, AddressBookProvider.SELECTION_QUERY,
					new String[] { constraint != null ? constraint : "" }, null);
		}

		@Override
		public void onLoadFinished(final Loader<Cursor> cursor, final Cursor data)
		{
			receivingAddressViewAdapter.swapCursor(data);
		}

		@Override
		public void onLoaderReset(final Loader<Cursor> cursor)
		{
			receivingAddressViewAdapter.swapCursor(null);
		}
	};

	private final class ReceivingAddressViewAdapter extends CursorAdapter implements FilterQueryProvider
	{
		public ReceivingAddressViewAdapter(final Context context)
		{
			super(context, null, false);
			setFilterQueryProvider(this);
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
		public Cursor runQuery(final CharSequence constraint)
		{
			final Bundle args = new Bundle();
			if (constraint != null)
				args.putString("constraint", constraint.toString());
			loaderManager.restartLoader(ID_RECEIVING_ADDRESS_LOADER, args, receivingAddressLoaderCallbacks);
			return getCursor();
		}
	}

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
		payeeVerifiedByView = (TextView) view.findViewById(R.id.send_coins_payee_verified_by);

		receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
		receivingAddressViewAdapter = new ReceivingAddressViewAdapter(activity);
		receivingAddressView.setAdapter(receivingAddressViewAdapter);
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
		btcAmountView.setCurrencySymbol(config.getFormat().code());
		btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
		btcAmountView.setHintFormat(config.getFormat());

		final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_local);
		localAmountView.setInputFormat(Constants.LOCAL_FORMAT);
		localAmountView.setHintFormat(Constants.LOCAL_FORMAT);
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

		hintView = (TextView) view.findViewById(R.id.send_coins_hint);

		directPaymentMessageView = (TextView) view.findViewById(R.id.send_coins_direct_payment_message);

		sentTransactionView = (ListView) view.findViewById(R.id.send_coins_sent_transaction);
		sentTransactionListAdapter = new TransactionsListAdapter(activity, wallet, application.maxConnectedPeers(), false);
		sentTransactionView.setAdapter(sentTransactionListAdapter);

		privateKeyPasswordViewGroup = view.findViewById(R.id.send_coins_private_key_password_group);
		privateKeyPasswordView = (EditText) view.findViewById(R.id.send_coins_private_key_password);
		privateKeyBadPasswordView = view.findViewById(R.id.send_coins_private_key_bad_password);

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				validateReceivingAddress();

				if (everythingValid())
					handleGo();
				else
					requestFocusFirst();

				updateView();
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				handleCancel();
			}
		});

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
		privateKeyPasswordView.addTextChangedListener(privateKeyPasswordListener);

		loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
		loaderManager.initLoader(ID_RECEIVING_ADDRESS_LOADER, null, receivingAddressLoaderCallbacks);

		updateView();
		handler.post(dryrunRunnable);
	}

	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_RECEIVING_ADDRESS_LOADER);
		loaderManager.destroyLoader(ID_RATE_LOADER);

		privateKeyPasswordView.removeTextChangedListener(privateKeyPasswordListener);
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

		outState.putParcelable("payment_intent", paymentIntent);
		outState.putBoolean("priority", priority);
		if (validatedAddress != null)
			outState.putParcelable("validated_address", validatedAddress);

		if (sentTransaction != null)
			outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());
		if (directPaymentAck != null)
			outState.putBoolean("direct_payment_ack", directPaymentAck);
	}

	private void restoreInstanceState(final Bundle savedInstanceState)
	{
		state = (State) savedInstanceState.getSerializable("state");

		paymentIntent = (PaymentIntent) savedInstanceState.getParcelable("payment_intent");
		priority = savedInstanceState.getBoolean("priority");
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
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				onActivityResultResumed(requestCode, resultCode, intent);
			}
		});
	}

	private void onActivityResultResumed(final int requestCode, final int resultCode, final Intent intent)
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
				requestPaymentRequest();
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

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu)
	{
		final MenuItem scanAction = menu.findItem(R.id.send_coins_options_scan);
		final PackageManager pm = activity.getPackageManager();
		scanAction.setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));
		scanAction.setEnabled(state == State.INPUT);

		final MenuItem emptyAction = menu.findItem(R.id.send_coins_options_empty);
		emptyAction.setEnabled(state == State.INPUT);

		final MenuItem priorityAction = menu.findItem(R.id.send_coins_options_priority);
		priorityAction.setChecked(priority);
		priorityAction.setEnabled(state == State.INPUT);

		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.send_coins_options_scan:
				handleScan();
				return true;

			case R.id.send_coins_options_priority:
				handlePriority();
				return true;

			case R.id.send_coins_options_empty:
				handleEmpty();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void validateReceivingAddress()
	{
		try
		{
			final String addressStr = receivingAddressView.getText().toString().trim();
			if (!addressStr.isEmpty() && Constants.NETWORK_PARAMETERS.equals(Address.getParametersFromAddress(addressStr)))
			{
				final String label = AddressBookProvider.resolveLabel(activity, addressStr);
				validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, addressStr, label);
				receivingAddressView.setText(null);
			}
		}
		catch (final AddressFormatException x)
		{
			// swallow
		}
	}

	private void handleCancel()
	{
		if (state == State.INPUT)
			activity.setResult(Activity.RESULT_CANCELED);

		activity.finish();
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
		return dryrunTransaction != null && dryrunException == null;
	}

	private boolean isPasswordValid()
	{
		if (!wallet.isEncrypted())
			return true;

		return !privateKeyPasswordView.getText().toString().trim().isEmpty();
	}

	private boolean everythingValid()
	{
		return state == State.INPUT && isOutputsValid() && isAmountValid() && isPasswordValid();
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

	private void handleGo()
	{
		privateKeyBadPasswordView.setVisibility(View.INVISIBLE);

		if (wallet.isEncrypted())
		{
			new DeriveKeyTask(backgroundHandler)
			{
				@Override
				protected void onSuccess(@Nonnull KeyParameter encryptionKey)
				{
					signAndSendPayment(encryptionKey);
				}
			}.deriveKey(wallet.getKeyCrypter(), privateKeyPasswordView.getText().toString().trim());

			setState(State.DECRYPTING);
		}
		else
		{
			signAndSendPayment(null);
		}
	}

	private void signAndSendPayment(final KeyParameter encryptionKey)
	{
		setState(State.SIGNING);

		// final payment intent
		final PaymentIntent finalPaymentIntent = paymentIntent.mergeWithEditedValues(amountCalculatorLink.getAmount(),
				validatedAddress != null ? validatedAddress.address : null);
		final Coin finalAmount = finalPaymentIntent.getAmount();

		// prepare send request
		final SendRequest sendRequest = finalPaymentIntent.toSendRequest();
		sendRequest.emptyWallet = paymentIntent.mayEditAmount() && finalAmount.equals(wallet.getBalance(BalanceType.AVAILABLE));
		sendRequest.feePerKb = priority ? SendRequest.DEFAULT_FEE_PER_KB.multiply(10) : SendRequest.DEFAULT_FEE_PER_KB;
		sendRequest.memo = paymentIntent.memo;
		sendRequest.exchangeRate = amountCalculatorLink.getExchangeRate();
		sendRequest.aesKey = encryptionKey;

		new SendCoinsOfflineTask(wallet, backgroundHandler)
		{
			@Override
			protected void onSuccess(final Transaction transaction)
			{
				sentTransaction = transaction;

				setState(State.SENDING);

				sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

				final Address refundAddress = paymentIntent.standard == Standard.BIP70 ? wallet.freshAddress(KeyPurpose.REFUND) : null;
				final Payment payment = PaymentProtocol.createPaymentMessage(Arrays.asList(new Transaction[] { sentTransaction }), finalAmount,
						refundAddress, null, paymentIntent.payeeData);

				if (directPaymentEnableView.isChecked())
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
				final DirectPaymentTask.ResultCallback callback = new DirectPaymentTask.ResultCallback()
				{
					@Override
					public void onResult(final boolean ack)
					{
						directPaymentAck = ack;

						if (state == State.SENDING)
							setState(State.SENT);

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

			@Override
			protected void onInsufficientMoney(@Nonnull final Coin missing)
			{
				setState(State.INPUT);

				final Coin estimated = wallet.getBalance(BalanceType.ESTIMATED);
				final Coin available = wallet.getBalance(BalanceType.AVAILABLE);
				final Coin pending = estimated.subtract(available);

				final MonetaryFormat btcFormat = config.getFormat();

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_insufficient_money_title);
				final StringBuilder msg = new StringBuilder();
				msg.append(getString(R.string.send_coins_fragment_insufficient_money_msg1, btcFormat.format(missing)));
				msg.append("\n\n");
				if (pending.signum() > 0)
					msg.append(getString(R.string.send_coins_fragment_pending, btcFormat.format(pending))).append("\n\n");
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
			protected void onInvalidKey()
			{
				setState(State.INPUT);

				privateKeyBadPasswordView.setVisibility(View.VISIBLE);
				privateKeyPasswordView.requestFocus();
			}

			@Override
			protected void onEmptyWalletFailed()
			{
				setState(State.INPUT);

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_empty_wallet_failed_title);
				dialog.setMessage(R.string.send_coins_fragment_hint_empty_wallet_failed);
				dialog.setNeutralButton(R.string.button_dismiss, null);
				dialog.show();
			}

			@Override
			protected void onFailure(@Nonnull Exception exception)
			{
				setState(State.FAILED);

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

	private void handlePriority()
	{
		priority = !priority;

		updateView();
		handler.post(dryrunRunnable);
	}

	private void handleEmpty()
	{
		final Coin available = wallet.getBalance(BalanceType.AVAILABLE);
		amountCalculatorLink.setBtcAmount(available);

		updateView();
		handler.post(dryrunRunnable);
	}

	private Runnable dryrunRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if (state == State.INPUT)
				executeDryrun();

			updateView();
		}

		private void executeDryrun()
		{
			dryrunTransaction = null;
			dryrunException = null;

			final Coin amount = amountCalculatorLink.getAmount();
			if (amount != null)
			{
				try
				{
					final Address dummy = wallet.currentReceiveAddress(); // won't be used, tx is never committed
					final SendRequest sendRequest = paymentIntent.mergeWithEditedValues(amount, dummy).toSendRequest();
					sendRequest.signInputs = false;
					sendRequest.emptyWallet = paymentIntent.mayEditAmount() && amount.equals(wallet.getBalance(BalanceType.AVAILABLE));
					sendRequest.feePerKb = priority ? SendRequest.DEFAULT_FEE_PER_KB.multiply(10) : SendRequest.DEFAULT_FEE_PER_KB;
					wallet.completeTx(sendRequest);
					dryrunTransaction = sendRequest.tx;
				}
				catch (final Exception x)
				{
					dryrunException = x;
				}
			}
		}
	};

	private void setState(final State state)
	{
		this.state = state;

		activity.invalidateOptionsMenu();
		updateView();
	}

	private void updateView()
	{
		if (paymentIntent != null)
		{
			final MonetaryFormat btcFormat = config.getFormat();

			getView().setVisibility(View.VISIBLE);

			if (paymentIntent.hasPayee())
			{
				payeeNameView.setVisibility(View.VISIBLE);
				payeeNameView.setText(paymentIntent.payeeName);

				payeeVerifiedByView.setVisibility(View.VISIBLE);
				final String verifiedBy = paymentIntent.payeeVerifiedBy != null ? paymentIntent.payeeVerifiedBy
						: getString(R.string.send_coins_fragment_payee_verified_by_unknown);
				payeeVerifiedByView.setText(Constants.CHAR_CHECKMARK
						+ String.format(getString(R.string.send_coins_fragment_payee_verified_by), verifiedBy));
			}
			else
			{
				payeeNameView.setVisibility(View.GONE);
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

			hintView.setVisibility(View.GONE);
			if (state == State.INPUT)
			{
				if (paymentIntent.mayEditAddress() && validatedAddress == null && !receivingAddressView.getText().toString().trim().isEmpty())
				{
					hintView.setTextColor(getResources().getColor(R.color.fg_error));
					hintView.setVisibility(View.VISIBLE);
					hintView.setText(R.string.send_coins_fragment_receiving_address_error);
				}
				else if (dryrunException != null)
				{
					hintView.setTextColor(getResources().getColor(R.color.fg_error));
					hintView.setVisibility(View.VISIBLE);
					if (dryrunException instanceof DustySendRequested)
						hintView.setText(getString(R.string.send_coins_fragment_hint_dusty_send));
					else if (dryrunException instanceof InsufficientMoneyException)
						hintView.setText(getString(R.string.send_coins_fragment_hint_insufficient_money,
								btcFormat.format(((InsufficientMoneyException) dryrunException).missing)));
					else if (dryrunException instanceof CouldNotAdjustDownwards)
						hintView.setText(getString(R.string.send_coins_fragment_hint_empty_wallet_failed));
					else
						hintView.setText(dryrunException.toString());
				}
				else if (dryrunTransaction != null)
				{
					final Coin previewFee = dryrunTransaction.getFee();
					if (previewFee != null)
					{
						hintView.setText(getString(R.string.send_coins_fragment_hint_fee, btcFormat.format(previewFee)));
						hintView.setTextColor(getResources().getColor(R.color.fg_insignificant));
						hintView.setVisibility(View.VISIBLE);
					}
				}
			}

			if (sentTransaction != null)
			{
				sentTransactionView.setVisibility(View.VISIBLE);
				sentTransactionListAdapter.setFormat(btcFormat);
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

			viewCancel.setEnabled(state != State.DECRYPTING && state != State.SIGNING);
			viewGo.setEnabled(everythingValid());

			if (state == State.INPUT)
			{
				viewCancel.setText(R.string.button_cancel);
				viewGo.setText(R.string.send_coins_fragment_button_send);
			}
			else if (state == State.DECRYPTING)
			{
				viewCancel.setText(R.string.button_cancel);
				viewGo.setText(R.string.send_coins_fragment_state_decrypting);
			}
			else if (state == State.SIGNING)
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

			final boolean privateKeyPasswordViewVisible = (state == State.INPUT || state == State.DECRYPTING) && wallet.isEncrypted();
			privateKeyPasswordViewGroup.setVisibility(privateKeyPasswordViewVisible ? View.VISIBLE : View.GONE);
			privateKeyPasswordView.setEnabled(state == State.INPUT);

			// focus linking
			final int activeAmountViewId = amountCalculatorLink.activeTextView().getId();
			receivingAddressView.setNextFocusDownId(activeAmountViewId);
			receivingAddressView.setNextFocusForwardId(activeAmountViewId);
			receivingStaticView.setNextFocusDownId(activeAmountViewId);
			amountCalculatorLink.setNextFocusId(privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : R.id.send_coins_go);
			privateKeyPasswordView.setNextFocusUpId(activeAmountViewId);
			privateKeyPasswordView.setNextFocusDownId(R.id.send_coins_go);
			viewGo.setNextFocusUpId(privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : activeAmountViewId);
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
			protected void handlePrivateKey(@Nonnull final VersionedChecksummedBytes key)
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

		validatedAddress = null;
		directPaymentAck = null;

		// delay these actions until fragment is resumed
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				if (state == State.INPUT)
				{
					receivingAddressView.setText(null);
					amountCalculatorLink.setBtcAmount(paymentIntent.getAmount());

					if (paymentIntent.isBluetoothPaymentUrl())
						directPaymentEnableView.setChecked(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
					else if (paymentIntent.isHttpPaymentUrl())
						directPaymentEnableView.setChecked(!Constants.BUG_OPENSSL_HEARTBLEED);

					requestFocusFirst();
					updateView();
					handler.post(dryrunRunnable);
				}

				if (paymentIntent.hasPaymentRequestUrl())
				{
					if (paymentIntent.isBluetoothPaymentRequestUrl() && !Constants.BUG_OPENSSL_HEARTBLEED)
					{
						if (bluetoothAdapter.isEnabled())
							requestPaymentRequest();
						else
							// ask for permission to enable bluetooth
							startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
									REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST);
					}
					else if (paymentIntent.isHttpPaymentRequestUrl())
					{
						requestPaymentRequest();
					}
				}
			}
		});
	}

	private void requestPaymentRequest()
	{
		final String host;
		if (!Bluetooth.isBluetoothUrl(paymentIntent.paymentRequestUrl))
			host = Uri.parse(paymentIntent.paymentRequestUrl).getHost();
		else
			host = Bluetooth.decompressMac(Bluetooth.getBluetoothMac(paymentIntent.paymentRequestUrl));

		ProgressDialogFragment.showProgress(fragmentManager, getString(R.string.send_coins_fragment_request_payment_request_progress, host));

		final RequestPaymentRequestTask.ResultCallback callback = new RequestPaymentRequestTask.ResultCallback()
		{
			@Override
			public void onPaymentIntent(final PaymentIntent paymentIntent)
			{
				ProgressDialogFragment.dismissProgress(fragmentManager);

				if (SendCoinsFragment.this.paymentIntent.isExtendedBy(paymentIntent))
				{
					// success
					updateStateFrom(paymentIntent);
					updateView();
					handler.post(dryrunRunnable);
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
						requestPaymentRequest();
					}
				});
				dialog.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						if (!paymentIntent.hasOutputs())
							handleCancel();
					}
				});
				dialog.show();
			}
		};

		if (!Bluetooth.isBluetoothUrl(paymentIntent.paymentRequestUrl))
			new RequestPaymentRequestTask.HttpRequestTask(backgroundHandler, callback, application.httpUserAgent())
					.requestPaymentRequest(paymentIntent.paymentRequestUrl);
		else
			new RequestPaymentRequestTask.BluetoothRequestTask(backgroundHandler, callback, bluetoothAdapter)
					.requestPaymentRequest(paymentIntent.paymentRequestUrl);
	}
}
