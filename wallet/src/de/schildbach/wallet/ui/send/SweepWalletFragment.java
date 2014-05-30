package de.schildbach.wallet.ui.send;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.wallet.WalletTransaction;
import com.google.bitcoin.wallet.WalletTransaction.Pool;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.ProgressDialogFragment;
import de.schildbach.wallet.ui.ScanActivity;
import de.schildbach.wallet.ui.TransactionsListAdapter;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Maximilian Keller
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

	private State state = State.INPUT;
	private Wallet walletToSweep = null;
	private Transaction sentTransaction = null;

	private View walletUnknownView;
	private TextView balanceView;
	private TransactionsListAdapter sweepTransactionListAdapter;
	private View hintView;
	private ListView sweepTransactionView;
	private Button viewGo;
	private Button viewCancel;

	private MenuItem reloadAction;
	private MenuItem scanAction;

	private static final int REQUEST_CODE_SCAN = 0;

	private enum State
	{
		INPUT, PREPARATION, SENDING, SENT, FAILED
	}

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractBindServiceActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.config = application.getConfiguration();
		this.config = new Configuration(PreferenceManager.getDefaultSharedPreferences(activity));
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
				final ECKey key = (ECKey) intent.getSerializableExtra(SweepWalletActivity.INTENT_EXTRA_KEY);
				init(key);
			}
			else
			{
				walletToSweep = null;
			}
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.sweep_wallet_fragment, container);

		walletUnknownView = view.findViewById(R.id.sweep_wallet_fragment_wallet_unknown);

		balanceView = (TextView) view.findViewById(R.id.sweep_wallet_fragment_balance);

		hintView = view.findViewById(R.id.sweep_wallet_fragment_hint);

		sweepTransactionView = (ListView) view.findViewById(R.id.sweep_wallet_fragment_sent_transaction);
		sweepTransactionListAdapter = new TransactionsListAdapter(activity, application.getWallet(), application.maxConnectedPeers(), false);
		sweepTransactionView.setAdapter(sweepTransactionListAdapter);

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				handleGo();
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (state == State.INPUT)
					activity.setResult(Activity.RESULT_CANCELED);

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
					protected void handlePrivateKey(@Nonnull final ECKey key)
					{
						init(key);
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

		updateView();
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
		public void onConfidenceChanged(final Transaction tx, final TransactionConfidence.Listener.ChangeReason reason)
		{
			activity.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					sweepTransactionListAdapter.notifyDataSetChanged();

					final TransactionConfidence confidence = sentTransaction.getConfidence();
					final TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
					final int numBroadcastPeers = confidence.numBroadcastPeers();

					if (state == State.SENDING)
					{
						if (confidenceType == TransactionConfidence.ConfidenceType.DEAD)
							state = State.FAILED;
						else if (numBroadcastPeers > 1 || confidenceType == TransactionConfidence.ConfidenceType.BUILDING)
							state = State.SENT;

						updateView();
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
				}
			});
		}
	};

	private void init(final ECKey key)
	{
		walletToSweep = new Wallet(Constants.NETWORK_PARAMETERS);
		walletToSweep.addKey(key);

		// delay these actions until fragment is resumed
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				requestWalletBalance();
			}
		});
	}

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
			public void onFail(int messageResId, Object... messageArgs)
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

	private void updateView()
	{
		if (walletToSweep != null)
		{
			final int btcShift = config.getBtcShift();
			final int btcPrecision = config.getBtcMaxPrecision();
			final String btcPrefix = config.getBtcPrefix();

			balanceView.setVisibility(View.VISIBLE);
			final SpannableStringBuilder balance = new SpannableStringBuilder(GenericUtils.formatValue(
					walletToSweep.getBalance(BalanceType.ESTIMATED), btcPrecision, btcShift));
			WalletUtils.formatSignificant(balance, null);
			balance.insert(0, " "); // insert backwards
			balance.insert(0, btcPrefix);
			balance.insert(0, ": ");
			balance.insert(0, getString(R.string.sweep_wallet_fragment_balance));
			balanceView.setText(balance);
			walletUnknownView.setVisibility(View.GONE);
		}
		else
		{
			walletUnknownView.setVisibility(View.VISIBLE);
			balanceView.setVisibility(View.GONE);
		}

		hintView.setVisibility(state == State.INPUT ? View.VISIBLE : View.GONE);

		if (sentTransaction != null)
		{
			final int btcPrecision = config.getBtcPrecision();
			final int btcShift = config.getBtcShift();

			sweepTransactionView.setVisibility(View.VISIBLE);
			sweepTransactionListAdapter.setPrecision(btcPrecision, btcShift);
			sweepTransactionListAdapter.replace(sentTransaction);
		}
		else
		{
			sweepTransactionView.setVisibility(View.GONE);
			sweepTransactionListAdapter.clear();
		}

		if (state == State.INPUT)
		{
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.sweep_wallet_fragment_button_sweep);
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

		viewCancel.setEnabled(state != State.PREPARATION);
		viewGo.setEnabled(walletToSweep != null && walletToSweep.getBalance(BalanceType.ESTIMATED).signum() > 0);

		// enable actions
		if (reloadAction != null)
			reloadAction.setEnabled(state == State.INPUT && walletToSweep != null);
		if (scanAction != null)
			scanAction.setEnabled(state == State.INPUT);
	}

	private void handleGo()
	{
		state = State.PREPARATION;
		updateView();

		final SendRequest sendRequest = SendRequest.emptyWallet(application.determineSelectedAddress());

		new SendCoinsOfflineTask(walletToSweep, backgroundHandler)
		{
			@Override
			protected void onSuccess(final Transaction transaction)
			{
				sentTransaction = transaction;

				state = State.SENDING;
				updateView();

				sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

				application.processDirectTransaction(sentTransaction);
			}

			@Override
			protected void onInsufficientMoney(@Nullable final Coin missing)
			{
				state = State.FAILED;
				updateView();

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.sweep_wallet_fragment_insufficient_money_title);
				dialog.setMessage(R.string.sweep_wallet_fragment_insufficient_money_msg);
				dialog.setNeutralButton(R.string.button_dismiss, null);
				dialog.show();
			}

			@Override
			protected void onFailure(final Exception exception)
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
}
