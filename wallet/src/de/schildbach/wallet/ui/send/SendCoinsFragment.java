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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nullable;

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
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.Wallet.CouldNotAdjustDownwards;
import org.bitcoinj.wallet.Wallet.DustySendRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.google.common.base.Strings;
import com.netki.WalletNameResolver;
import com.netki.dns.DNSBootstrapService;
import com.netki.dnssec.DNSSECResolver;
import com.netki.exceptions.WalletNameCurrencyUnavailableException;
import com.netki.exceptions.WalletNameLookupException;
import com.netki.tlsa.CACertService;
import com.netki.tlsa.CertChainValidator;
import com.netki.tlsa.TLSAValidator;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.data.DynamicFeeLoader;
import de.schildbach.wallet.data.ExchangeRate;
import de.schildbach.wallet.data.ExchangeRatesLoader;
import de.schildbach.wallet.data.ExchangeRatesProvider;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.data.PaymentIntent.Standard;
import de.schildbach.wallet.integration.android.BitcoinIntegration;
import de.schildbach.wallet.offline.DirectPaymentTask;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainStateLoader;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.AddressAndLabel;
import de.schildbach.wallet.ui.CurrencyAmountView;
import de.schildbach.wallet.ui.CurrencyCalculatorLink;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.InputParser.StreamInputParser;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.ProgressDialogFragment;
import de.schildbach.wallet.ui.ScanActivity;
import de.schildbach.wallet.ui.TransactionsAdapter;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.bluetooth.BluetoothAdapter;
import android.content.AsyncTaskLoader;
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
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends Fragment {
    private AbstractBindServiceActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private ContentResolver contentResolver;
    private LoaderManager loaderManager;
    private FragmentManager fragmentManager;
    @Nullable
    private BluetoothAdapter bluetoothAdapter;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private View payeeGroup;
    private TextView payeeNameView;
    private TextView payeeVerifiedByView;
    private AutoCompleteTextView receivingAddressView;
    private ReceivingAddressViewAdapter receivingAddressViewAdapter;
    private ReceivingAddressLoaderCallbacks receivingAddressLoaderCallbacks;
    private View receivingStaticView;
    private TextView receivingStaticAddressView;
    private TextView receivingStaticLabelView;
    private View amountGroup;
    private CurrencyCalculatorLink amountCalculatorLink;
    private CheckBox directPaymentEnableView;

    private TextView hintView;
    private TextView directPaymentMessageView;
    private FrameLayout sentTransactionView;
    private TransactionsAdapter sentTransactionAdapter;
    private RecyclerView.ViewHolder sentTransactionViewHolder;
    private View privateKeyPasswordViewGroup;
    private EditText privateKeyPasswordView;
    private View privateKeyBadPasswordView;
    private Button viewGo;
    private Button viewCancel;

    @Nullable
    private State state = null;

    private PaymentIntent paymentIntent = null;
    private FeeCategory feeCategory = FeeCategory.NORMAL;
    private AddressAndLabel validatedAddress = null;

    @Nullable
    private Map<FeeCategory, Coin> fees = null;
    @Nullable
    private BlockchainState blockchainState = null;
    private Transaction sentTransaction = null;
    private Boolean directPaymentAck = null;

    private Transaction dryrunTransaction;
    private Exception dryrunException;

    private static final int ID_DYNAMIC_FEES_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;
    private static final int ID_BLOCKCHAIN_STATE_LOADER = 2;
    private static final int ID_RECEIVING_ADDRESS_BOOK_LOADER = 3;
    private static final int ID_RECEIVING_ADDRESS_NAME_LOADER = 4;

    private static final int REQUEST_CODE_SCAN = 0;
    private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST = 1;
    private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT = 2;

    private static final Logger log = LoggerFactory.getLogger(SendCoinsFragment.class);

    private enum State {
        REQUEST_PAYMENT_REQUEST, //
        INPUT, // asks for confirmation
        DECRYPTING, SIGNING, SENDING, SENT, FAILED // sending states
    }

    private final class ReceivingAddressListener
            implements OnFocusChangeListener, TextWatcher, AdapterView.OnItemClickListener {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus) {
                validateReceivingAddress();
                updateView();
            }
        }

        @Override
        public void afterTextChanged(final Editable s) {
            if (s.length() > 0)
                validateReceivingAddress();
            else
                updateView();

            final Bundle args = new Bundle();
            args.putString(ReceivingAddressLoaderCallbacks.ARG_CONSTRAINT, s.toString());

            loaderManager.restartLoader(ID_RECEIVING_ADDRESS_BOOK_LOADER, args, receivingAddressLoaderCallbacks);
            if (config.getLookUpWalletNames())
                loaderManager.restartLoader(ID_RECEIVING_ADDRESS_NAME_LOADER, args, receivingAddressLoaderCallbacks);
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }

        @Override
        public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
            final Cursor cursor = receivingAddressViewAdapter.getCursor();
            cursor.moveToPosition(position);
            final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
            final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
            try {
                validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, address, label);
                receivingAddressView.setText(null);
            } catch (final AddressFormatException x) {
                // swallow
            }
        }
    }

    private final ReceivingAddressListener receivingAddressListener = new ReceivingAddressListener();

    private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener() {
        @Override
        public void changed() {
            updateView();
            handler.post(dryrunRunnable);
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
        }
    };

    private final TextWatcher privateKeyPasswordListener = new TextWatcher() {
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            privateKeyBadPasswordView.setVisibility(View.INVISIBLE);
            updateView();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void afterTextChanged(final Editable s) {
        }
    };

    private final ContentObserver contentObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            updateView();
        }
    };

    private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener() {
        @Override
        public void onConfidenceChanged(final TransactionConfidence confidence,
                final TransactionConfidence.Listener.ChangeReason reason) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isResumed())
                        return;

                    final TransactionConfidence confidence = sentTransaction.getConfidence();
                    final ConfidenceType confidenceType = confidence.getConfidenceType();
                    final int numBroadcastPeers = confidence.numBroadcastPeers();

                    if (state == State.SENDING) {
                        if (confidenceType == ConfidenceType.DEAD) {
                            setState(State.FAILED);
                        } else if (numBroadcastPeers > 1 || confidenceType == ConfidenceType.BUILDING) {
                            setState(State.SENT);

                            // Auto-close the dialog after a short delay
                            if (config.getSendCoinsAutoclose()) {
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        activity.finish();
                                    }
                                }, 500);
                            }
                        }
                    }

                    if (reason == ChangeReason.SEEN_PEERS && confidenceType == ConfidenceType.PENDING) {
                        // play sound effect
                        final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers,
                                "raw", activity.getPackageName());
                        if (soundResId > 0)
                            RingtoneManager
                                    .getRingtone(activity, Uri.parse(
                                            "android.resource://" + activity.getPackageName() + "/" + soundResId))
                                    .play();
                    }

                    updateView();
                }
            });
        }
    };

    private final LoaderCallbacks<Map<FeeCategory, Coin>> dynamicFeesLoaderCallbacks = new LoaderManager.LoaderCallbacks<Map<FeeCategory, Coin>>() {
        @Override
        public Loader<Map<FeeCategory, Coin>> onCreateLoader(final int id, final Bundle args) {
            return new DynamicFeeLoader(activity);
        }

        @Override
        public void onLoadFinished(final Loader<Map<FeeCategory, Coin>> loader, final Map<FeeCategory, Coin> data) {
            fees = data;
            updateView();
            handler.post(dryrunRunnable);
        }

        @Override
        public void onLoaderReset(final Loader<Map<FeeCategory, Coin>> loader) {
        }
    };

    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            return new ExchangeRatesLoader(activity, config);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                data.moveToFirst();
                final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);

                if (state == null || state.compareTo(State.INPUT) <= 0)
                    amountCalculatorLink.setExchangeRate(exchangeRate.rate);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    private final LoaderCallbacks<BlockchainState> blockchainStateLoaderCallbacks = new LoaderManager.LoaderCallbacks<BlockchainState>() {
        @Override
        public Loader<BlockchainState> onCreateLoader(final int id, final Bundle args) {
            return new BlockchainStateLoader(activity);
        }

        @Override
        public void onLoadFinished(final Loader<BlockchainState> loader, final BlockchainState blockchainState) {
            SendCoinsFragment.this.blockchainState = blockchainState;
            updateView();
        }

        @Override
        public void onLoaderReset(final Loader<BlockchainState> loader) {
        }
    };

    private static class ReceivingAddressLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        private final static String ARG_CONSTRAINT = "constraint";

        private final Context context;
        private final CursorAdapter targetAdapter;
        private Cursor receivingAddressBookCursor, receivingAddressNameCursor;

        public ReceivingAddressLoaderCallbacks(final Context context, final CursorAdapter targetAdapter) {
            this.context = checkNotNull(context);
            this.targetAdapter = checkNotNull(targetAdapter);
        }

        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            final String constraint = Strings.nullToEmpty(args != null ? args.getString(ARG_CONSTRAINT) : null);

            if (id == ID_RECEIVING_ADDRESS_BOOK_LOADER)
                return new CursorLoader(context, AddressBookProvider.contentUri(context.getPackageName()), null,
                        AddressBookProvider.SELECTION_QUERY, new String[] { constraint }, null);
            else if (id == ID_RECEIVING_ADDRESS_NAME_LOADER)
                return new ReceivingAddressNameLoader(context, constraint);
            else
                throw new IllegalArgumentException();
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, Cursor data) {
            if (data.getCount() == 0)
                data = null;
            if (loader instanceof CursorLoader)
                receivingAddressBookCursor = data;
            else
                receivingAddressNameCursor = data;
            swapTargetCursor();
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
            if (loader instanceof CursorLoader)
                receivingAddressBookCursor = null;
            else
                receivingAddressNameCursor = null;
            swapTargetCursor();
        }

        private void swapTargetCursor() {
            if (receivingAddressBookCursor == null && receivingAddressNameCursor == null)
                targetAdapter.swapCursor(null);
            else if (receivingAddressBookCursor != null && receivingAddressNameCursor == null)
                targetAdapter.swapCursor(receivingAddressBookCursor);
            else if (receivingAddressBookCursor == null && receivingAddressNameCursor != null)
                targetAdapter.swapCursor(receivingAddressNameCursor);
            else
                targetAdapter.swapCursor(
                        new MergeCursor(new Cursor[] { receivingAddressBookCursor, receivingAddressNameCursor }));
        }
    }

    private static class ReceivingAddressNameLoader extends AsyncTaskLoader<Cursor> {
        private String constraint;

        public ReceivingAddressNameLoader(final Context context, final String constraint) {
            super(context);
            this.constraint = constraint;
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            safeForceLoad();
        }

        @Override
        public Cursor loadInBackground() {
            final MatrixCursor cursor = new MatrixCursor(new String[] { AddressBookProvider.KEY_ROWID,
                    AddressBookProvider.KEY_LABEL, AddressBookProvider.KEY_ADDRESS }, 1);

            if (constraint.indexOf('.') >= 0 || constraint.indexOf('@') >= 0) {
                try {
                    final WalletNameResolver resolver = new WalletNameResolver(
                            new DNSSECResolver(new DNSBootstrapService()),
                            new TLSAValidator(new DNSSECResolver(new DNSBootstrapService()),
                                    CACertService.getInstance(), new CertChainValidator()));
                    final BitcoinURI resolvedUri = resolver.resolve(constraint, Constants.WALLET_NAME_CURRENCY_CODE,
                            true);
                    if (resolvedUri != null) {
                        final Address resolvedAddress = resolvedUri.getAddress();
                        if (resolvedAddress != null
                                && resolvedAddress.getParameters().equals(Constants.NETWORK_PARAMETERS)) {
                            final String resolvedLabel = Strings.emptyToNull(resolvedUri.getLabel());
                            cursor.addRow(new Object[] { -1, resolvedLabel != null ? resolvedLabel : constraint,
                                    resolvedAddress.toString() });
                            log.info("looked up wallet name: " + resolvedUri);
                        }
                    }
                } catch (final WalletNameCurrencyUnavailableException x) {
                    // swallow
                } catch (final WalletNameLookupException x) {
                    log.info("error looking up wallet name '" + constraint + "': " + x.getMessage());
                } catch (final Throwable x) {
                    log.info("error looking up wallet name", x);
                }
            }

            return cursor;
        }

        private void safeForceLoad() {
            try {
                forceLoad();
            } catch (final RejectedExecutionException x) {
                log.info("rejected execution: " + ReceivingAddressNameLoader.this.toString());
            }
        }
    }

    private final class ReceivingAddressViewAdapter extends CursorAdapter {
        public ReceivingAddressViewAdapter(final Context context) {
            super(context, null, false);
        }

        @Override
        public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.address_book_row, parent, false);
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {
            final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
            final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));

            final ViewGroup viewGroup = (ViewGroup) view;
            final TextView labelView = (TextView) viewGroup.findViewById(R.id.address_book_row_label);
            labelView.setText(label);
            final TextView addressView = (TextView) viewGroup.findViewById(R.id.address_book_row_address);
            addressView.setText(WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
                    Constants.ADDRESS_FORMAT_LINE_SIZE));
        }

        @Override
        public CharSequence convertToString(final Cursor cursor) {
            return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
        }
    }

    private final DialogInterface.OnClickListener activityDismissListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            activity.finish();
        }
    };

    @Override
    public void onAttach(final Activity activity) {
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
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            final Intent intent = activity.getIntent();
            final String action = intent.getAction();
            final Uri intentUri = intent.getData();
            final String scheme = intentUri != null ? intentUri.getScheme() : null;
            final String mimeType = intent.getType();

            if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
                    && intentUri != null && "bitcoin".equals(scheme)) {
                initStateFromBitcoinUri(intentUri);
            } else if ((NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
                    && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType)) {
                final NdefMessage ndefMessage = (NdefMessage) intent
                        .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
                final byte[] ndefMessagePayload = Nfc.extractMimePayload(PaymentProtocol.MIMETYPE_PAYMENTREQUEST,
                        ndefMessage);
                initStateFromPaymentRequest(mimeType, ndefMessagePayload);
            } else if ((Intent.ACTION_VIEW.equals(action))
                    && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType)) {
                final byte[] paymentRequest = BitcoinIntegration.paymentRequestFromIntent(intent);

                if (intentUri != null)
                    initStateFromIntentUri(mimeType, intentUri);
                else if (paymentRequest != null)
                    initStateFromPaymentRequest(mimeType, paymentRequest);
                else
                    throw new IllegalArgumentException();
            } else if (intent.hasExtra(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT)) {
                initStateFromIntentExtras(intent.getExtras());
            } else {
                updateStateFrom(PaymentIntent.blank());
            }
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.send_coins_fragment, container);

        payeeGroup = view.findViewById(R.id.send_coins_payee_group);

        payeeNameView = (TextView) view.findViewById(R.id.send_coins_payee_name);
        payeeVerifiedByView = (TextView) view.findViewById(R.id.send_coins_payee_verified_by);

        receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
        receivingAddressViewAdapter = new ReceivingAddressViewAdapter(activity);
        receivingAddressLoaderCallbacks = new ReceivingAddressLoaderCallbacks(activity, receivingAddressViewAdapter);
        receivingAddressView.setAdapter(receivingAddressViewAdapter);
        receivingAddressView.setOnFocusChangeListener(receivingAddressListener);
        receivingAddressView.addTextChangedListener(receivingAddressListener);
        receivingAddressView.setOnItemClickListener(receivingAddressListener);

        receivingStaticView = view.findViewById(R.id.send_coins_receiving_static);
        receivingStaticAddressView = (TextView) view.findViewById(R.id.send_coins_receiving_static_address);
        receivingStaticLabelView = (TextView) view.findViewById(R.id.send_coins_receiving_static_label);

        amountGroup = view.findViewById(R.id.send_coins_amount_group);

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
        directPaymentEnableView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                if (paymentIntent.isBluetoothPaymentUrl() && isChecked && !bluetoothAdapter.isEnabled()) {
                    // ask for permission to enable bluetooth
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT);
                }
            }
        });

        hintView = (TextView) view.findViewById(R.id.send_coins_hint);

        directPaymentMessageView = (TextView) view.findViewById(R.id.send_coins_direct_payment_message);

        sentTransactionView = (FrameLayout) view.findViewById(R.id.send_coins_sent_transaction);
        sentTransactionAdapter = new TransactionsAdapter(activity, wallet, false, application.maxConnectedPeers(),
                null);
        sentTransactionViewHolder = sentTransactionAdapter.createTransactionViewHolder(sentTransactionView);
        sentTransactionView.addView(sentTransactionViewHolder.itemView,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        privateKeyPasswordViewGroup = view.findViewById(R.id.send_coins_private_key_password_group);
        privateKeyPasswordView = (EditText) view.findViewById(R.id.send_coins_private_key_password);
        privateKeyBadPasswordView = view.findViewById(R.id.send_coins_private_key_bad_password);

        viewGo = (Button) view.findViewById(R.id.send_coins_go);
        viewGo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                validateReceivingAddress();

                if (everythingPlausible())
                    handleGo();
                else
                    requestFocusFirst();

                updateView();
            }
        });

        viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
        viewCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleCancel();
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
    }

    @Override
    public void onResume() {
        super.onResume();

        contentResolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true,
                contentObserver);

        amountCalculatorLink.setListener(amountsListener);
        privateKeyPasswordView.addTextChangedListener(privateKeyPasswordListener);

        loaderManager.initLoader(ID_DYNAMIC_FEES_LOADER, null, dynamicFeesLoaderCallbacks);
        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
        loaderManager.initLoader(ID_BLOCKCHAIN_STATE_LOADER, null, blockchainStateLoaderCallbacks);
        loaderManager.initLoader(ID_RECEIVING_ADDRESS_BOOK_LOADER, null, receivingAddressLoaderCallbacks);
        loaderManager.initLoader(ID_RECEIVING_ADDRESS_NAME_LOADER, null, receivingAddressLoaderCallbacks);

        updateView();
        handler.post(dryrunRunnable);
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_RECEIVING_ADDRESS_NAME_LOADER);
        loaderManager.destroyLoader(ID_RECEIVING_ADDRESS_BOOK_LOADER);
        loaderManager.destroyLoader(ID_BLOCKCHAIN_STATE_LOADER);
        loaderManager.destroyLoader(ID_RATE_LOADER);
        loaderManager.destroyLoader(ID_DYNAMIC_FEES_LOADER);

        privateKeyPasswordView.removeTextChangedListener(privateKeyPasswordListener);
        amountCalculatorLink.setListener(null);

        contentResolver.unregisterContentObserver(contentObserver);

        super.onPause();
    }

    @Override
    public void onDetach() {
        handler.removeCallbacksAndMessages(null);

        super.onDetach();
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();

        if (sentTransaction != null)
            sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);

        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        saveInstanceState(outState);
    }

    private void saveInstanceState(final Bundle outState) {
        outState.putSerializable("state", state);

        outState.putParcelable("payment_intent", paymentIntent);
        outState.putSerializable("fee_category", feeCategory);
        if (validatedAddress != null)
            outState.putParcelable("validated_address", validatedAddress);

        if (sentTransaction != null)
            outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());
        if (directPaymentAck != null)
            outState.putBoolean("direct_payment_ack", directPaymentAck);
    }

    private void restoreInstanceState(final Bundle savedInstanceState) {
        state = (State) savedInstanceState.getSerializable("state");

        paymentIntent = (PaymentIntent) savedInstanceState.getParcelable("payment_intent");
        feeCategory = (FeeCategory) savedInstanceState.getSerializable("fee_category");
        validatedAddress = savedInstanceState.getParcelable("validated_address");

        if (savedInstanceState.containsKey("sent_transaction_hash")) {
            sentTransaction = wallet
                    .getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
            sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
        }
        if (savedInstanceState.containsKey("direct_payment_ack"))
            directPaymentAck = savedInstanceState.getBoolean("direct_payment_ack");
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                onActivityResultResumed(requestCode, resultCode, intent);
            }
        });
    }

    private void onActivityResultResumed(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                new StringInputParser(input) {
                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                        setState(null);

                        updateStateFrom(paymentIntent);
                    }

                    @Override
                    protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                        cannotClassify(input);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        dialog(activity, null, R.string.button_scan, messageResId, messageArgs);
                    }
                }.parse();
            }
        } else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST) {
            if (paymentIntent.isBluetoothPaymentRequestUrl())
                requestPaymentRequest();
        } else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT) {
            if (paymentIntent.isBluetoothPaymentUrl())
                directPaymentEnableView.setChecked(resultCode == Activity.RESULT_OK);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.send_coins_fragment_options, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        final MenuItem scanAction = menu.findItem(R.id.send_coins_options_scan);
        final PackageManager pm = activity.getPackageManager();
        scanAction.setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));
        scanAction.setEnabled(state == State.INPUT);

        final MenuItem emptyAction = menu.findItem(R.id.send_coins_options_empty);
        emptyAction.setEnabled(state == State.INPUT && paymentIntent.mayEditAmount());

        final MenuItem feeCategoryAction = menu.findItem(R.id.send_coins_options_fee_category);
        feeCategoryAction.setEnabled(state == State.INPUT);
        if (feeCategory == FeeCategory.ECONOMIC)
            menu.findItem(R.id.send_coins_options_fee_category_economic).setChecked(true);
        else if (feeCategory == FeeCategory.NORMAL)
            menu.findItem(R.id.send_coins_options_fee_category_normal).setChecked(true);
        else if (feeCategory == FeeCategory.PRIORITY)
            menu.findItem(R.id.send_coins_options_fee_category_priority).setChecked(true);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.send_coins_options_scan:
            handleScan();
            return true;

        case R.id.send_coins_options_fee_category_economic:
            handleFeeCategory(FeeCategory.ECONOMIC);
            return true;
        case R.id.send_coins_options_fee_category_normal:
            handleFeeCategory(FeeCategory.NORMAL);
            return true;
        case R.id.send_coins_options_fee_category_priority:
            handleFeeCategory(FeeCategory.PRIORITY);
            return true;

        case R.id.send_coins_options_empty:
            handleEmpty();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void validateReceivingAddress() {
        try {
            final String addressStr = receivingAddressView.getText().toString().trim();
            if (!addressStr.isEmpty()
                    && Constants.NETWORK_PARAMETERS.equals(Address.getParametersFromAddress(addressStr))) {
                final String label = AddressBookProvider.resolveLabel(activity, addressStr);
                validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, addressStr, label);
                receivingAddressView.setText(null);
            }
        } catch (final AddressFormatException x) {
            // swallow
        }
    }

    private void handleCancel() {
        if (state == null || state.compareTo(State.INPUT) <= 0)
            activity.setResult(Activity.RESULT_CANCELED);

        activity.finish();
    }

    private boolean isPayeePlausible() {
        if (paymentIntent.hasOutputs())
            return true;

        if (validatedAddress != null)
            return true;

        return false;
    }

    private boolean isAmountPlausible() {
        if (dryrunTransaction != null)
            return dryrunException == null;
        else if (paymentIntent.mayEditAmount())
            return amountCalculatorLink.hasAmount();
        else
            return paymentIntent.hasAmount();
    }

    private boolean isPasswordPlausible() {
        if (!wallet.isEncrypted())
            return true;

        return !privateKeyPasswordView.getText().toString().trim().isEmpty();
    }

    private boolean everythingPlausible() {
        return state == State.INPUT && isPayeePlausible() && isAmountPlausible() && isPasswordPlausible();
    }

    private void requestFocusFirst() {
        if (!isPayeePlausible())
            receivingAddressView.requestFocus();
        else if (!isAmountPlausible())
            amountCalculatorLink.requestFocus();
        else if (!isPasswordPlausible())
            privateKeyPasswordView.requestFocus();
        else if (everythingPlausible())
            viewGo.requestFocus();
        else
            log.warn("unclear focus");
    }

    private void handleGo() {
        privateKeyBadPasswordView.setVisibility(View.INVISIBLE);

        if (wallet.isEncrypted()) {
            new DeriveKeyTask(backgroundHandler, application.scryptIterationsTarget()) {
                @Override
                protected void onSuccess(final KeyParameter encryptionKey, final boolean wasChanged) {
                    if (wasChanged)
                        application.backupWallet();
                    signAndSendPayment(encryptionKey);
                }
            }.deriveKey(wallet, privateKeyPasswordView.getText().toString().trim());

            setState(State.DECRYPTING);
        } else {
            signAndSendPayment(null);
        }
    }

    private void signAndSendPayment(final KeyParameter encryptionKey) {
        setState(State.SIGNING);

        // final payment intent
        final PaymentIntent finalPaymentIntent = paymentIntent.mergeWithEditedValues(amountCalculatorLink.getAmount(),
                validatedAddress != null ? validatedAddress.address : null);
        final Coin finalAmount = finalPaymentIntent.getAmount();

        // prepare send request
        final SendRequest sendRequest = finalPaymentIntent.toSendRequest();
        sendRequest.emptyWallet = paymentIntent.mayEditAmount()
                && finalAmount.equals(wallet.getBalance(BalanceType.AVAILABLE));
        sendRequest.feePerKb = fees.get(feeCategory);
        sendRequest.memo = paymentIntent.memo;
        sendRequest.exchangeRate = amountCalculatorLink.getExchangeRate();
        sendRequest.aesKey = encryptionKey;

        new SendCoinsOfflineTask(wallet, backgroundHandler) {
            @Override
            protected void onSuccess(final Transaction transaction) {
                sentTransaction = transaction;

                setState(State.SENDING);

                sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

                final Address refundAddress = paymentIntent.standard == Standard.BIP70
                        ? wallet.freshAddress(KeyPurpose.REFUND) : null;
                final Payment payment = PaymentProtocol.createPaymentMessage(
                        Arrays.asList(new Transaction[] { sentTransaction }), finalAmount, refundAddress, null,
                        paymentIntent.payeeData);

                if (directPaymentEnableView.isChecked())
                    directPay(payment);

                application.broadcastTransaction(sentTransaction);

                final ComponentName callingActivity = activity.getCallingActivity();
                if (callingActivity != null) {
                    log.info("returning result to calling activity: {}", callingActivity.flattenToString());

                    final Intent result = new Intent();
                    BitcoinIntegration.transactionHashToResult(result, sentTransaction.getHashAsString());
                    if (paymentIntent.standard == Standard.BIP70)
                        BitcoinIntegration.paymentToResult(result, payment.toByteArray());
                    activity.setResult(Activity.RESULT_OK, result);
                }
            }

            private void directPay(final Payment payment) {
                final DirectPaymentTask.ResultCallback callback = new DirectPaymentTask.ResultCallback() {
                    @Override
                    public void onResult(final boolean ack) {
                        directPaymentAck = ack;

                        if (state == State.SENDING)
                            setState(State.SENT);

                        updateView();
                    }

                    @Override
                    public void onFail(final int messageResId, final Object... messageArgs) {
                        final DialogBuilder dialog = DialogBuilder.warn(activity,
                                R.string.send_coins_fragment_direct_payment_failed_title);
                        dialog.setMessage(paymentIntent.paymentUrl + "\n" + getString(messageResId, messageArgs)
                                + "\n\n" + getString(R.string.send_coins_fragment_direct_payment_failed_msg));
                        dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                directPay(payment);
                            }
                        });
                        dialog.setNegativeButton(R.string.button_dismiss, null);
                        dialog.show();
                    }
                };

                if (paymentIntent.isHttpPaymentUrl()) {
                    new DirectPaymentTask.HttpPaymentTask(backgroundHandler, callback, paymentIntent.paymentUrl,
                            application.httpUserAgent()).send(payment);
                } else if (paymentIntent.isBluetoothPaymentUrl() && bluetoothAdapter != null
                        && bluetoothAdapter.isEnabled()) {
                    new DirectPaymentTask.BluetoothPaymentTask(backgroundHandler, callback, bluetoothAdapter,
                            Bluetooth.getBluetoothMac(paymentIntent.paymentUrl)).send(payment);
                }
            }

            @Override
            protected void onInsufficientMoney(final Coin missing) {
                setState(State.INPUT);

                final Coin estimated = wallet.getBalance(BalanceType.ESTIMATED);
                final Coin available = wallet.getBalance(BalanceType.AVAILABLE);
                final Coin pending = estimated.subtract(available);

                final MonetaryFormat btcFormat = config.getFormat();

                final DialogBuilder dialog = DialogBuilder.warn(activity,
                        R.string.send_coins_fragment_insufficient_money_title);
                final StringBuilder msg = new StringBuilder();
                msg.append(getString(R.string.send_coins_fragment_insufficient_money_msg1, btcFormat.format(missing)));

                if (pending.signum() > 0)
                    msg.append("\n\n")
                            .append(getString(R.string.send_coins_fragment_pending, btcFormat.format(pending)));
                if (paymentIntent.mayEditAmount())
                    msg.append("\n\n").append(getString(R.string.send_coins_fragment_insufficient_money_msg2));
                dialog.setMessage(msg);
                if (paymentIntent.mayEditAmount()) {
                    dialog.setPositiveButton(R.string.send_coins_options_empty, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            handleEmpty();
                        }
                    });
                    dialog.setNegativeButton(R.string.button_cancel, null);
                } else {
                    dialog.setNeutralButton(R.string.button_dismiss, null);
                }
                dialog.show();
            }

            @Override
            protected void onInvalidEncryptionKey() {
                setState(State.INPUT);

                privateKeyBadPasswordView.setVisibility(View.VISIBLE);
                privateKeyPasswordView.requestFocus();
            }

            @Override
            protected void onEmptyWalletFailed() {
                setState(State.INPUT);

                final DialogBuilder dialog = DialogBuilder.warn(activity,
                        R.string.send_coins_fragment_empty_wallet_failed_title);
                dialog.setMessage(R.string.send_coins_fragment_hint_empty_wallet_failed);
                dialog.setNeutralButton(R.string.button_dismiss, null);
                dialog.show();
            }

            @Override
            protected void onFailure(Exception exception) {
                setState(State.FAILED);

                final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_error_msg);
                dialog.setMessage(exception.toString());
                dialog.setNeutralButton(R.string.button_dismiss, null);
                dialog.show();
            }
        }.sendCoinsOffline(sendRequest); // send asynchronously
    }

    private void handleScan() {
        startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
    }

    private void handleFeeCategory(final FeeCategory feeCategory) {
        this.feeCategory = feeCategory;

        updateView();
        handler.post(dryrunRunnable);
    }

    private void handleEmpty() {
        final Coin available = wallet.getBalance(BalanceType.AVAILABLE);
        amountCalculatorLink.setBtcAmount(available);

        updateView();
        handler.post(dryrunRunnable);
    }

    private Runnable dryrunRunnable = new Runnable() {
        @Override
        public void run() {
            if (state == State.INPUT)
                executeDryrun();

            updateView();
        }

        private void executeDryrun() {
            dryrunTransaction = null;
            dryrunException = null;

            final Coin amount = amountCalculatorLink.getAmount();
            if (amount != null && fees != null) {
                try {
                    final Address dummy = wallet.currentReceiveAddress(); // won't be used, tx is never
                                                                          // committed
                    final SendRequest sendRequest = paymentIntent.mergeWithEditedValues(amount, dummy).toSendRequest();
                    sendRequest.signInputs = false;
                    sendRequest.emptyWallet = paymentIntent.mayEditAmount()
                            && amount.equals(wallet.getBalance(BalanceType.AVAILABLE));
                    sendRequest.feePerKb = fees.get(feeCategory);
                    wallet.completeTx(sendRequest);
                    dryrunTransaction = sendRequest.tx;
                } catch (final Exception x) {
                    dryrunException = x;
                }
            }
        }
    };

    private void setState(final State state) {
        this.state = state;

        activity.invalidateOptionsMenu();
        updateView();
    }

    private void updateView() {
        if (!isResumed())
            return;

        if (paymentIntent != null) {
            final MonetaryFormat btcFormat = config.getFormat();

            getView().setVisibility(View.VISIBLE);

            if (paymentIntent.hasPayee()) {
                payeeNameView.setVisibility(View.VISIBLE);
                payeeNameView.setText(paymentIntent.payeeName);

                payeeVerifiedByView.setVisibility(View.VISIBLE);
                final String verifiedBy = paymentIntent.payeeVerifiedBy != null ? paymentIntent.payeeVerifiedBy
                        : getString(R.string.send_coins_fragment_payee_verified_by_unknown);
                payeeVerifiedByView.setText(Constants.CHAR_CHECKMARK
                        + String.format(getString(R.string.send_coins_fragment_payee_verified_by), verifiedBy));
            } else {
                payeeNameView.setVisibility(View.GONE);
                payeeVerifiedByView.setVisibility(View.GONE);
            }

            if (paymentIntent.hasOutputs()) {
                payeeGroup.setVisibility(View.VISIBLE);
                receivingAddressView.setVisibility(View.GONE);
                receivingStaticView.setVisibility(
                        !paymentIntent.hasPayee() || paymentIntent.payeeVerifiedBy == null ? View.VISIBLE : View.GONE);

                receivingStaticLabelView.setText(paymentIntent.memo);

                if (paymentIntent.hasAddress())
                    receivingStaticAddressView.setText(WalletUtils.formatAddress(paymentIntent.getAddress(),
                            Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                else
                    receivingStaticAddressView.setText(R.string.send_coins_fragment_receiving_address_complex);
            } else if (validatedAddress != null) {
                payeeGroup.setVisibility(View.VISIBLE);
                receivingAddressView.setVisibility(View.GONE);
                receivingStaticView.setVisibility(View.VISIBLE);

                receivingStaticAddressView.setText(WalletUtils.formatAddress(validatedAddress.address,
                        Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                final String addressBookLabel = AddressBookProvider.resolveLabel(activity,
                        validatedAddress.address.toBase58());
                final String staticLabel;
                if (addressBookLabel != null)
                    staticLabel = addressBookLabel;
                else if (validatedAddress.label != null)
                    staticLabel = validatedAddress.label;
                else
                    staticLabel = getString(R.string.address_unlabeled);
                receivingStaticLabelView.setText(staticLabel);
                receivingStaticLabelView.setTextColor(getResources()
                        .getColor(validatedAddress.label != null ? R.color.fg_significant : R.color.fg_insignificant));
            } else if (paymentIntent.standard == null) {
                payeeGroup.setVisibility(View.VISIBLE);
                receivingStaticView.setVisibility(View.GONE);
                receivingAddressView.setVisibility(View.VISIBLE);
            } else {
                payeeGroup.setVisibility(View.GONE);
            }

            receivingAddressView.setEnabled(state == State.INPUT);

            amountGroup.setVisibility(paymentIntent.hasAmount() || (state != null && state.compareTo(State.INPUT) >= 0)
                    ? View.VISIBLE : View.GONE);
            amountCalculatorLink.setEnabled(state == State.INPUT && paymentIntent.mayEditAmount());

            final boolean directPaymentVisible;
            if (paymentIntent.hasPaymentUrl()) {
                if (paymentIntent.isBluetoothPaymentUrl())
                    directPaymentVisible = bluetoothAdapter != null;
                else
                    directPaymentVisible = !Constants.BUG_OPENSSL_HEARTBLEED;
            } else {
                directPaymentVisible = false;
            }
            directPaymentEnableView.setVisibility(directPaymentVisible ? View.VISIBLE : View.GONE);
            directPaymentEnableView.setEnabled(state == State.INPUT);

            hintView.setVisibility(View.GONE);
            if (state == State.INPUT) {
                if (paymentIntent.mayEditAddress() && validatedAddress == null
                        && !receivingAddressView.getText().toString().trim().isEmpty()) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_error));
                    hintView.setVisibility(View.VISIBLE);
                    hintView.setText(R.string.send_coins_fragment_receiving_address_error);
                } else if (dryrunException != null) {
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
                } else if (blockchainState != null && blockchainState.replaying) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_error));
                    hintView.setVisibility(View.VISIBLE);
                    hintView.setText(R.string.send_coins_fragment_hint_replaying);
                } else if (dryrunTransaction != null && dryrunTransaction.getFee() != null) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_insignificant));
                    hintView.setVisibility(View.VISIBLE);
                    final int hintResId;
                    if (feeCategory == FeeCategory.ECONOMIC)
                        hintResId = R.string.send_coins_fragment_hint_fee_economic;
                    else if (feeCategory == FeeCategory.PRIORITY)
                        hintResId = R.string.send_coins_fragment_hint_fee_priority;
                    else
                        hintResId = R.string.send_coins_fragment_hint_fee;
                    hintView.setText(getString(hintResId, btcFormat.format(dryrunTransaction.getFee())));
                } else if (paymentIntent.mayEditAddress() && validatedAddress != null
                        && wallet.isPubKeyHashMine(validatedAddress.address.getHash160())) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_insignificant));
                    hintView.setVisibility(View.VISIBLE);
                    hintView.setText(R.string.send_coins_fragment_receiving_address_own);
                }
            }

            if (sentTransaction != null) {
                sentTransactionView.setVisibility(View.VISIBLE);
                sentTransactionAdapter.setFormat(btcFormat);
                sentTransactionAdapter.replace(sentTransaction);
                sentTransactionAdapter.bindViewHolder(sentTransactionViewHolder, 0);
            } else {
                sentTransactionView.setVisibility(View.GONE);
            }

            if (directPaymentAck != null) {
                directPaymentMessageView.setVisibility(View.VISIBLE);
                directPaymentMessageView.setText(directPaymentAck ? R.string.send_coins_fragment_direct_payment_ack
                        : R.string.send_coins_fragment_direct_payment_nack);
            } else {
                directPaymentMessageView.setVisibility(View.GONE);
            }

            viewCancel.setEnabled(
                    state != State.REQUEST_PAYMENT_REQUEST && state != State.DECRYPTING && state != State.SIGNING);
            viewGo.setEnabled(everythingPlausible() && dryrunTransaction != null && fees != null
                    && (blockchainState == null || !blockchainState.replaying));

            if (state == null || state == State.REQUEST_PAYMENT_REQUEST) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(null);
            } else if (state == State.INPUT) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(R.string.send_coins_fragment_button_send);
            } else if (state == State.DECRYPTING) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(R.string.send_coins_fragment_state_decrypting);
            } else if (state == State.SIGNING) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(R.string.send_coins_preparation_msg);
            } else if (state == State.SENDING) {
                viewCancel.setText(R.string.send_coins_fragment_button_back);
                viewGo.setText(R.string.send_coins_sending_msg);
            } else if (state == State.SENT) {
                viewCancel.setText(R.string.send_coins_fragment_button_back);
                viewGo.setText(R.string.send_coins_sent_msg);
            } else if (state == State.FAILED) {
                viewCancel.setText(R.string.send_coins_fragment_button_back);
                viewGo.setText(R.string.send_coins_failed_msg);
            }

            final boolean privateKeyPasswordViewVisible = (state == State.INPUT || state == State.DECRYPTING)
                    && wallet.isEncrypted();
            privateKeyPasswordViewGroup.setVisibility(privateKeyPasswordViewVisible ? View.VISIBLE : View.GONE);
            privateKeyPasswordView.setEnabled(state == State.INPUT);

            // focus linking
            final int activeAmountViewId = amountCalculatorLink.activeTextView().getId();
            receivingAddressView.setNextFocusDownId(activeAmountViewId);
            receivingAddressView.setNextFocusForwardId(activeAmountViewId);
            amountCalculatorLink.setNextFocusId(
                    privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : R.id.send_coins_go);
            privateKeyPasswordView.setNextFocusUpId(activeAmountViewId);
            privateKeyPasswordView.setNextFocusDownId(R.id.send_coins_go);
            privateKeyPasswordView.setNextFocusForwardId(R.id.send_coins_go);
            viewGo.setNextFocusUpId(
                    privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : activeAmountViewId);
        } else {
            getView().setVisibility(View.GONE);
        }
    }

    private void initStateFromIntentExtras(final Bundle extras) {
        final PaymentIntent paymentIntent = extras.getParcelable(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT);
        final FeeCategory feeCategory = (FeeCategory) extras
                .getSerializable(SendCoinsActivity.INTENT_EXTRA_FEE_CATEGORY);

        if (feeCategory != null) {
            log.info("got fee category {}", feeCategory);
            this.feeCategory = feeCategory;
        }

        updateStateFrom(paymentIntent);
    }

    private void initStateFromBitcoinUri(final Uri bitcoinUri) {
        final String input = bitcoinUri.toString();

        new StringInputParser(input) {
            @Override
            protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                updateStateFrom(paymentIntent);
            }

            @Override
            protected void handlePrivateKey(final VersionedChecksummedBytes key) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs) {
                dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
            }
        }.parse();
    }

    private void initStateFromPaymentRequest(final String mimeType, final byte[] input) {
        new BinaryInputParser(mimeType, input) {
            @Override
            protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                updateStateFrom(paymentIntent);
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs) {
                dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
            }
        }.parse();
    }

    private void initStateFromIntentUri(final String mimeType, final Uri bitcoinUri) {
        try {
            final InputStream is = contentResolver.openInputStream(bitcoinUri);

            new StreamInputParser(mimeType, is) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                    updateStateFrom(paymentIntent);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
                }
            }.parse();
        } catch (final FileNotFoundException x) {
            throw new RuntimeException(x);
        }
    }

    private void updateStateFrom(final PaymentIntent paymentIntent) {
        log.info("got {}", paymentIntent);

        this.paymentIntent = paymentIntent;

        validatedAddress = null;
        directPaymentAck = null;

        // delay these actions until fragment is resumed
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (paymentIntent.hasPaymentRequestUrl() && paymentIntent.isBluetoothPaymentRequestUrl()) {
                    if (bluetoothAdapter.isEnabled())
                        requestPaymentRequest();
                    else
                        // ask for permission to enable bluetooth
                        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                                REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST);
                } else if (paymentIntent.hasPaymentRequestUrl() && paymentIntent.isHttpPaymentRequestUrl()
                        && !Constants.BUG_OPENSSL_HEARTBLEED) {
                    requestPaymentRequest();
                } else {
                    setState(State.INPUT);

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
            }
        });
    }

    private void requestPaymentRequest() {
        final String host;
        if (!Bluetooth.isBluetoothUrl(paymentIntent.paymentRequestUrl))
            host = Uri.parse(paymentIntent.paymentRequestUrl).getHost();
        else
            host = Bluetooth.decompressMac(Bluetooth.getBluetoothMac(paymentIntent.paymentRequestUrl));

        ProgressDialogFragment.showProgress(fragmentManager,
                getString(R.string.send_coins_fragment_request_payment_request_progress, host));
        setState(State.REQUEST_PAYMENT_REQUEST);

        final RequestPaymentRequestTask.ResultCallback callback = new RequestPaymentRequestTask.ResultCallback() {
            @Override
            public void onPaymentIntent(final PaymentIntent paymentIntent) {
                ProgressDialogFragment.dismissProgress(fragmentManager);

                if (SendCoinsFragment.this.paymentIntent.isExtendedBy(paymentIntent)) {
                    // success
                    setState(State.INPUT);
                    updateStateFrom(paymentIntent);
                    updateView();
                    handler.post(dryrunRunnable);
                } else {
                    final StringBuilder reasons = new StringBuilder();
                    if (!SendCoinsFragment.this.paymentIntent.equalsAddress(paymentIntent))
                        reasons.append("address");
                    if (!SendCoinsFragment.this.paymentIntent.equalsAmount(paymentIntent))
                        reasons.append(reasons.length() == 0 ? "" : ", ").append("amount");
                    if (reasons.length() == 0)
                        reasons.append("unknown");

                    final DialogBuilder dialog = DialogBuilder.warn(activity,
                            R.string.send_coins_fragment_request_payment_request_failed_title);
                    dialog.setMessage(getString(R.string.send_coins_fragment_request_payment_request_wrong_signature)
                            + "\n\n" + reasons);
                    dialog.singleDismissButton(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            handleCancel();
                        }
                    });
                    dialog.show();

                    log.info("BIP72 trust check failed: {}", reasons);
                }
            }

            @Override
            public void onFail(final int messageResId, final Object... messageArgs) {
                ProgressDialogFragment.dismissProgress(fragmentManager);

                final DialogBuilder dialog = DialogBuilder.warn(activity,
                        R.string.send_coins_fragment_request_payment_request_failed_title);
                dialog.setMessage(getString(messageResId, messageArgs));
                dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        requestPaymentRequest();
                    }
                });
                dialog.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        if (!paymentIntent.hasOutputs())
                            handleCancel();
                        else
                            setState(State.INPUT);
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
