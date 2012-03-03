/*
 * Copyright 2014-2015 the original author or authors.
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

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.VersionedChecksummedBytes;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.BalanceType;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.WalletTransaction.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.ProgressDialogFragment;
import de.schildbach.wallet.ui.ScanActivity;
import de.schildbach.wallet.ui.TransactionsAdapter;
import de.schildbach.wallet.util.MonetarySpannable;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class SweepWalletFragment extends Fragment
{
	private AbstractBindServiceActivity activity;
	private WalletApplication application;
	private Configuration config;
	private FragmentManager fragmentManager;

	private final Handler handler = new Handler();
	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private State state = State.DECODE_KEY;
	private VersionedChecksummedBytes privateKeyToSweep = null;
	private Wallet walletToSweep = null;
	private Transaction sentTransaction = null;

	private TextView messageView;
	private View passwordViewGroup;
	private EditText passwordView;
	private View badPasswordView;
	private TextView balanceView;
	private View hintView;
	private FrameLayout sweepTransactionView;
	private TransactionsAdapter sweepTransactionAdapter;
	private RecyclerView.ViewHolder sweepTransactionViewHolder;
	private Button viewGo;
	private Button viewCancel;

	private MenuItem reloadAction;
	private MenuItem scanAction;

	private static final int REQUEST_CODE_SCAN = 0;

	private enum State
	{
		DECODE_KEY, // ask for password
		CONFIRM_SWEEP, // displays balance and asks for confirmation
		PREPARATION, SENDING, SENT, FAILED // sending states
	}

	private static final Logger log = LoggerFactory.getLogger(SweepWalletFragment.class);

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractBindServiceActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.config = application.getConfiguration();
		this.fragmentManager = getFragmentManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
		setHasOptionsMenu(true);

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

			if (intent.hasExtra(SweepWalletActivity.INTENT_EXTRA_KEY))
			{
				privateKeyToSweep = (VersionedChecksummedBytes) intent.getSerializableExtra(SweepWalletActivity.INTENT_EXTRA_KEY);

				// delay until fragment is resumed
				handler.post(maybeDecodeKeyRunnable);
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.sweep_wallet_fragment, container);

		messageView = (TextView) view.findViewById(R.id.sweep_wallet_fragment_message);

		passwordViewGroup = view.findViewById(R.id.sweep_wallet_fragment_password_group);
		passwordView = (EditText) view.findViewById(R.id.sweep_wallet_fragment_password);
		badPasswordView = view.findViewById(R.id.sweep_wallet_fragment_bad_password);

		balanceView = (TextView) view.findViewById(R.id.sweep_wallet_fragment_balance);

		hintView = view.findViewById(R.id.sweep_wallet_fragment_hint);

		sweepTransactionView = (FrameLayout) view.findViewById(R.id.sweep_wallet_fragment_sent_transaction);
		sweepTransactionAdapter = new TransactionsAdapter(activity, application.getWallet(), false, application.maxConnectedPeers(), null);
		sweepTransactionViewHolder = sweepTransactionAdapter.createTransactionViewHolder(sweepTransactionView);
		sweepTransactionView.addView(sweepTransactionViewHolder.itemView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (state == State.DECODE_KEY)
					handleDecrypt();
				if (state == State.CONFIRM_SWEEP)
					handleSweep();
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				activity.finish();
			}
		});

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
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
		if (walletToSweep != null)
			outState.putByteArray("wallet_to_sweep", WalletUtils.walletToByteArray(walletToSweep));
		if (sentTransaction != null)
			outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());
	}

	private void restoreInstanceState(final Bundle savedInstanceState)
	{
		state = (State) savedInstanceState.getSerializable("state");
		if (savedInstanceState.containsKey("wallet_to_sweep"))
			walletToSweep = WalletUtils.walletFromByteArray(savedInstanceState.getByteArray("wallet_to_sweep"));
		if (savedInstanceState.containsKey("sent_transaction_hash"))
		{
			sentTransaction = application.getWallet().getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
			sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
		}
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
					protected void handlePrivateKey(final VersionedChecksummedBytes key)
					{
						privateKeyToSweep = key;
						setState(State.DECODE_KEY);
						maybeDecodeKey();
					}

					@Override
					protected void handlePaymentIntent(final PaymentIntent paymentIntent)
					{
						cannotClassify(input);
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
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.sweep_wallet_fragment_options, menu);

		reloadAction = menu.findItem(R.id.sweep_wallet_options_reload);
		scanAction = menu.findItem(R.id.sweep_wallet_options_scan);

		final PackageManager pm = activity.getPackageManager();
		scanAction.setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.sweep_wallet_options_reload:
				handleReload();
				return true;

			case R.id.sweep_wallet_options_scan:
				handleScan();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleReload()
	{
		if (walletToSweep == null)
			return;

		requestWalletBalance();
	}

	private void handleScan()
	{
		startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener()
	{
		@Override
		public void onConfidenceChanged(final TransactionConfidence confidence, final TransactionConfidence.Listener.ChangeReason reason)
		{
			activity.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					if (!isResumed())
						return;

					final TransactionConfidence confidence = sentTransaction.getConfidence();
					final TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
					final int numBroadcastPeers = confidence.numBroadcastPeers();

					if (state == State.SENDING)
					{
						if (confidenceType == TransactionConfidence.ConfidenceType.DEAD)
							setState(State.FAILED);
						else if (numBroadcastPeers > 1 || confidenceType == TransactionConfidence.ConfidenceType.BUILDING)
							setState(State.SENT);
					}

					if (reason == ChangeReason.SEEN_PEERS && confidenceType == TransactionConfidence.ConfidenceType.PENDING)
					{
						// play sound effect
						final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers, "raw",
								activity.getPackageName());
						if (soundResId > 0)
							RingtoneManager.getRingtone(activity, Uri.parse("android.resource://" + activity.getPackageName() + "/" + soundResId))
									.play();
					}

					updateView();
				}
			});
		}
	};

	private final Runnable maybeDecodeKeyRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			maybeDecodeKey();
		}
	};

	private void maybeDecodeKey()
	{
		checkState(state == State.DECODE_KEY);
		checkState(privateKeyToSweep != null);

		if (privateKeyToSweep instanceof DumpedPrivateKey)
		{
			final ECKey key = ((DumpedPrivateKey) privateKeyToSweep).getKey();
			askConfirmSweep(key);
		}
		else if (privateKeyToSweep instanceof BIP38PrivateKey)
		{
			badPasswordView.setVisibility(View.INVISIBLE);

			final String password = passwordView.getText().toString().trim();
			passwordView.setText(null); // get rid of it asap

			if (!password.isEmpty())
			{
				ProgressDialogFragment.showProgress(fragmentManager, getString(R.string.sweep_wallet_fragment_decrypt_progress));

				new DecodePrivateKeyTask(backgroundHandler)
				{
					@Override
					protected void onSuccess(ECKey decryptedKey)
					{
						log.info("successfully decoded BIP38 private key");

						ProgressDialogFragment.dismissProgress(fragmentManager);

						askConfirmSweep(decryptedKey);
					}

					@Override
					protected void onBadPassphrase()
					{
						log.info("failed decoding BIP38 private key (bad password)");

						ProgressDialogFragment.dismissProgress(fragmentManager);

						badPasswordView.setVisibility(View.VISIBLE);
						passwordView.requestFocus();
					}
				}.decodePrivateKey((BIP38PrivateKey) privateKeyToSweep, password);
			}
		}
		else
		{
			throw new IllegalStateException("cannot handle type: " + privateKeyToSweep.getClass().getName());
		}
	}

	private void askConfirmSweep(final ECKey key)
	{
		// create non-HD wallet
		final KeyChainGroup group = new KeyChainGroup(Constants.NETWORK_PARAMETERS);
		group.importKeys(key);
		walletToSweep = new Wallet(Constants.NETWORK_PARAMETERS, group);

		setState(State.CONFIRM_SWEEP);

		// delay until fragment is resumed
		handler.post(requestWalletBalanceRunnable);
	}

	private final Runnable requestWalletBalanceRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			requestWalletBalance();
		}
	};

	private void requestWalletBalance()
	{
		ProgressDialogFragment.showProgress(fragmentManager, getString(R.string.sweep_wallet_fragment_request_wallet_balance_progress));

		final RequestWalletBalanceTask.ResultCallback callback = new RequestWalletBalanceTask.ResultCallback()
		{
			@Override
			public void onResult(final Collection<Transaction> transactions)
			{
				ProgressDialogFragment.dismissProgress(fragmentManager);

				walletToSweep.clearTransactions(0);
				for (final Transaction transaction : transactions)
					walletToSweep.addWalletTransaction(new WalletTransaction(Pool.UNSPENT, transaction));

				updateView();
			}

			@Override
			public void onFail(final int messageResId, final Object... messageArgs)
			{
				ProgressDialogFragment.dismissProgress(fragmentManager);

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.sweep_wallet_fragment_request_wallet_balance_failed_title);
				dialog.setMessage(getString(messageResId, messageArgs));
				dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						requestWalletBalance();
					}
				});
				dialog.setNegativeButton(R.string.button_dismiss, null);
				dialog.show();
			}
		};

		final Address address = walletToSweep.getImportedKeys().iterator().next().toAddress(Constants.NETWORK_PARAMETERS);
		new RequestWalletBalanceTask(backgroundHandler, callback, application.httpUserAgent()).requestWalletBalance(address);
	}

	private void setState(final State state)
	{
		this.state = state;

		updateView();
	}

	private void updateView()
	{
		final MonetaryFormat btcFormat = config.getFormat();

		if (walletToSweep != null)
		{
			balanceView.setVisibility(View.VISIBLE);
			final MonetarySpannable balanceSpannable = new MonetarySpannable(btcFormat, walletToSweep.getBalance(BalanceType.ESTIMATED));
			balanceSpannable.applyMarkup(null, null);
			final SpannableStringBuilder balance = new SpannableStringBuilder(balanceSpannable);
			balance.insert(0, ": ");
			balance.insert(0, getString(R.string.sweep_wallet_fragment_balance));
			balanceView.setText(balance);
		}
		else
		{
			balanceView.setVisibility(View.GONE);
		}

		if (state == State.DECODE_KEY && privateKeyToSweep == null)
		{
			messageView.setVisibility(View.VISIBLE);
			messageView.setText(R.string.sweep_wallet_fragment_wallet_unknown);
		}
		else if (state == State.DECODE_KEY && privateKeyToSweep != null)
		{
			messageView.setVisibility(View.VISIBLE);
			messageView.setText(R.string.sweep_wallet_fragment_encrypted);
		}
		else if (privateKeyToSweep != null)
		{
			messageView.setVisibility(View.GONE);
		}

		passwordViewGroup.setVisibility(state == State.DECODE_KEY && privateKeyToSweep != null ? View.VISIBLE : View.GONE);

		hintView.setVisibility(state == State.DECODE_KEY && privateKeyToSweep == null ? View.VISIBLE : View.GONE);

		if (sentTransaction != null)
		{
			sweepTransactionView.setVisibility(View.VISIBLE);
			sweepTransactionAdapter.setFormat(btcFormat);
			sweepTransactionAdapter.replace(sentTransaction);
			sweepTransactionAdapter.bindViewHolder(sweepTransactionViewHolder, 0);
		}
		else
		{
			sweepTransactionView.setVisibility(View.GONE);
		}

		if (state == State.DECODE_KEY)
		{
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.sweep_wallet_fragment_button_decrypt);
			viewGo.setEnabled(privateKeyToSweep != null);
		}
		else if (state == State.CONFIRM_SWEEP)
		{
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.sweep_wallet_fragment_button_sweep);
			viewGo.setEnabled(walletToSweep != null && walletToSweep.getBalance(BalanceType.ESTIMATED).signum() > 0);
		}
		else if (state == State.PREPARATION)
		{
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.send_coins_preparation_msg);
			viewGo.setEnabled(false);
		}
		else if (state == State.SENDING)
		{
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_sending_msg);
			viewGo.setEnabled(false);
		}
		else if (state == State.SENT)
		{
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_sent_msg);
			viewGo.setEnabled(false);
		}
		else if (state == State.FAILED)
		{
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_failed_msg);
			viewGo.setEnabled(false);
		}

		viewCancel.setEnabled(state != State.PREPARATION);

		// enable actions
		if (reloadAction != null)
			reloadAction.setEnabled(state == State.CONFIRM_SWEEP && walletToSweep != null);
		if (scanAction != null)
			scanAction.setEnabled(state == State.DECODE_KEY || state == State.CONFIRM_SWEEP);
	}

	private void handleDecrypt()
	{
		handler.post(maybeDecodeKeyRunnable);
	}

	private void handleSweep()
	{
		setState(State.PREPARATION);

		final SendRequest sendRequest = SendRequest.emptyWallet(application.getWallet().freshReceiveAddress());
		sendRequest.feePerKb = FeeCategory.NORMAL.feePerKb;

		new SendCoinsOfflineTask(walletToSweep, backgroundHandler)
		{
			@Override
			protected void onSuccess(final Transaction transaction)
			{
				sentTransaction = transaction;

				setState(State.SENDING);

				sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

				application.processDirectTransaction(sentTransaction);
			}

			@Override
			protected void onInsufficientMoney(@Nullable final Coin missing)
			{
				setState(State.FAILED);

				showInsufficientMoneyDialog();
			}

			@Override
			protected void onEmptyWalletFailed()
			{
				setState(State.FAILED);

				showInsufficientMoneyDialog();
			}

			@Override
			protected void onFailure(final Exception exception)
			{
				setState(State.FAILED);

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_error_msg);
				dialog.setMessage(exception.toString());
				dialog.setNeutralButton(R.string.button_dismiss, null);
				dialog.show();
			}

			@Override
			protected void onInvalidKey()
			{
				throw new RuntimeException(); // cannot happen
			}

			private void showInsufficientMoneyDialog()
			{
				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.sweep_wallet_fragment_insufficient_money_title);
				dialog.setMessage(R.string.sweep_wallet_fragment_insufficient_money_msg);
				dialog.setNeutralButton(R.string.button_dismiss, null);
				dialog.show();
			}
		}.sendCoinsOffline(sendRequest); // send asynchronously
	}
}
