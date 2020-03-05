/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.send;

import static androidx.core.util.Preconditions.checkState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrefixedChecksummedBytes;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ComparisonChain;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.DialogEvent;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.ProgressDialogFragment;
import de.schildbach.wallet.ui.TransactionsAdapter;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.util.MonetarySpannable;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

/**
 * @author Andreas Schildbach
 */
public class SweepWalletFragment extends Fragment {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private FragmentManager fragmentManager;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private TextView messageView;
    private View passwordViewGroup;
    private EditText passwordView;
    private View badPasswordView;
    private TextView balanceView;
    private View hintView;
    private View sweepTransactionView;
    private TransactionsAdapter.TransactionViewHolder sweepTransactionViewHolder;
    private Button viewGo;
    private Button viewCancel;

    private MenuItem reloadAction;
    private MenuItem scanAction;

    private SweepWalletViewModel viewModel;

    private static final int REQUEST_CODE_SCAN = 0;

    private static final Logger log = LoggerFactory.getLogger(SweepWalletFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.config = application.getConfiguration();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.fragmentManager = getChildFragmentManager();

        setHasOptionsMenu(true);

        if (!Constants.ENABLE_SWEEP_WALLET)
            throw new IllegalStateException("ENABLE_SWEEP_WALLET is disabled");

        viewModel = new ViewModelProvider(this).get(SweepWalletViewModel.class);
        viewModel.getDynamicFees().observe(this, dynamicFees -> updateView());
        viewModel.progress.observe(this, new ProgressDialogFragment.Observer(fragmentManager));
        viewModel.sentTransaction.observe(this, transaction -> {
            if (viewModel.state == SweepWalletViewModel.State.SENDING) {
                final TransactionConfidence confidence = transaction.getConfidence();
                final ConfidenceType confidenceType = confidence.getConfidenceType();
                final int numBroadcastPeers = confidence.numBroadcastPeers();
                if (confidenceType == ConfidenceType.DEAD) {
                    setState(SweepWalletViewModel.State.FAILED);
                } else if (numBroadcastPeers > 1 || confidenceType == ConfidenceType.BUILDING) {
                    setState(SweepWalletViewModel.State.SENT);
                }
            }
            updateView();
        });
        viewModel.showDialog.observe(this, new DialogEvent.Observer(activity));
        viewModel.showDialogWithRetryRequestBalance.observe(this, new DialogEvent.Observer(activity) {
            @Override
            protected void onBuildButtons(final DialogBuilder dialog) {
                dialog.setPositiveButton(R.string.button_retry, (d, which) -> requestWalletBalance());
                dialog.setNegativeButton(R.string.button_dismiss, null);
            }
        });

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        if (savedInstanceState == null) {
            final Intent intent = activity.getIntent();

            if (intent.hasExtra(SweepWalletActivity.INTENT_EXTRA_KEY)) {
                viewModel.privateKeyToSweep = (PrefixedChecksummedBytes) intent
                        .getSerializableExtra(SweepWalletActivity.INTENT_EXTRA_KEY);

                // delay until fragment is resumed
                handler.post(maybeDecodeKeyRunnable);
            }
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.sweep_wallet_fragment, container, false);

        messageView = view.findViewById(R.id.sweep_wallet_fragment_message);

        passwordViewGroup = view.findViewById(R.id.sweep_wallet_fragment_password_group);
        passwordView = view.findViewById(R.id.sweep_wallet_fragment_password);
        badPasswordView = view.findViewById(R.id.sweep_wallet_fragment_bad_password);

        balanceView = view.findViewById(R.id.sweep_wallet_fragment_balance);

        hintView = view.findViewById(R.id.sweep_wallet_fragment_hint);

        sweepTransactionView = view.findViewById(R.id.transaction_row);
        sweepTransactionView.setVisibility(View.GONE);
        sweepTransactionViewHolder = new TransactionsAdapter.TransactionViewHolder(view);

        viewGo = view.findViewById(R.id.send_coins_go);
        viewGo.setOnClickListener(v -> {
            if (viewModel.state == SweepWalletViewModel.State.DECODE_KEY)
                handleDecrypt();
            if (viewModel.state == SweepWalletViewModel.State.CONFIRM_SWEEP)
                handleSweep();
        });

        viewCancel = view.findViewById(R.id.send_coins_cancel);
        viewCancel.setOnClickListener(v -> activity.finish());

        return view;
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();
        super.onDestroy();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                new StringInputParser(input) {
                    @Override
                    protected void handlePrivateKey(final PrefixedChecksummedBytes key) {
                        viewModel.privateKeyToSweep = key;
                        setState(SweepWalletViewModel.State.DECODE_KEY);
                        maybeDecodeKey();
                    }

                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                        cannotClassify(input);
                    }

                    @Override
                    protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                        cannotClassify(input);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        viewModel.showDialog.setValue(DialogEvent.dialog(R.string.button_scan,
                                messageResId, messageArgs));
                    }
                }.parse();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.sweep_wallet_fragment_options, menu);

        reloadAction = menu.findItem(R.id.sweep_wallet_options_reload);
        scanAction = menu.findItem(R.id.sweep_wallet_options_scan);

        final PackageManager pm = activity.getPackageManager();
        scanAction.setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.sweep_wallet_options_reload) {
            handleReload();
            return true;
        } else if (itemId == R.id.sweep_wallet_options_scan) {
            ScanActivity.startForResult(this, activity, REQUEST_CODE_SCAN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleReload() {
        if (viewModel.walletToSweep == null)
            return;

        requestWalletBalance();
    }

    private final Runnable maybeDecodeKeyRunnable = () -> maybeDecodeKey();

    private void maybeDecodeKey() {
        checkState(viewModel.state == SweepWalletViewModel.State.DECODE_KEY);
        checkState(viewModel.privateKeyToSweep != null);

        if (viewModel.privateKeyToSweep instanceof DumpedPrivateKey) {
            final ECKey key = ((DumpedPrivateKey) viewModel.privateKeyToSweep).getKey();
            askConfirmSweep(key);
        } else if (viewModel.privateKeyToSweep instanceof BIP38PrivateKey) {
            badPasswordView.setVisibility(View.INVISIBLE);

            final String password = passwordView.getText().toString().trim();
            passwordView.setText(null); // get rid of it asap

            if (!password.isEmpty()) {
                viewModel.progress.setValue(getString(R.string.sweep_wallet_fragment_decrypt_progress));

                new DecodePrivateKeyTask(backgroundHandler) {
                    @Override
                    protected void onSuccess(ECKey decryptedKey) {
                        log.info("successfully decoded BIP38 private key");

                        viewModel.progress.setValue(null);

                        askConfirmSweep(decryptedKey);
                    }

                    @Override
                    protected void onBadPassphrase() {
                        log.info("failed decoding BIP38 private key (bad password)");

                        viewModel.progress.setValue(null);

                        badPasswordView.setVisibility(View.VISIBLE);
                        passwordView.requestFocus();
                    }
                }.decodePrivateKey((BIP38PrivateKey) viewModel.privateKeyToSweep, password);
            }
        } else {
            throw new IllegalStateException("cannot handle type: " + viewModel.privateKeyToSweep.getClass().getName());
        }
    }

    private void askConfirmSweep(final ECKey key) {
        viewModel.walletToSweep = Wallet.createBasic(Constants.NETWORK_PARAMETERS);
        viewModel.walletToSweep.importKey(key);

        setState(SweepWalletViewModel.State.CONFIRM_SWEEP);

        // delay until fragment is resumed
        handler.post(requestWalletBalanceRunnable);
    }

    private final Runnable requestWalletBalanceRunnable = () -> requestWalletBalance();

    private static final Comparator<UTXO> UTXO_COMPARATOR = (lhs, rhs) -> ComparisonChain.start().compare(lhs.getHash(), rhs.getHash()).compare(lhs.getIndex(), rhs.getIndex())
            .result();

    private void requestWalletBalance() {
        viewModel.progress.setValue(getString(R.string.sweep_wallet_fragment_request_wallet_balance_progress));

        final RequestWalletBalanceTask.ResultCallback callback = new RequestWalletBalanceTask.ResultCallback() {
            @Override
            public void onResult(final Set<UTXO> utxos) {
                viewModel.progress.setValue(null);

                // Filter UTXOs we've already spent and sort the rest.
                final Set<Transaction> walletTxns = application.getWallet().getTransactions(false);
                final Set<UTXO> sortedUtxos = new TreeSet<>(UTXO_COMPARATOR);
                for (final UTXO utxo : utxos)
                    if (!utxoSpentBy(walletTxns, utxo))
                        sortedUtxos.add(utxo);

                // Fake transaction funding the wallet to sweep.
                final Map<Sha256Hash, Transaction> fakeTxns = new HashMap<>();
                for (final UTXO utxo : sortedUtxos) {
                    Transaction fakeTx = fakeTxns.get(utxo.getHash());
                    if (fakeTx == null) {
                        fakeTx = new FakeTransaction(Constants.NETWORK_PARAMETERS, utxo.getHash(), utxo.getHash());
                        fakeTx.getConfidence().setConfidenceType(ConfidenceType.BUILDING);
                        fakeTxns.put(fakeTx.getTxId(), fakeTx);
                    }
                    final TransactionOutput fakeOutput = new TransactionOutput(Constants.NETWORK_PARAMETERS, fakeTx,
                            utxo.getValue(), utxo.getScript().getProgram());
                    // Fill with output dummies as needed.
                    while (fakeTx.getOutputs().size() < utxo.getIndex())
                        fakeTx.addOutput(new TransactionOutput(Constants.NETWORK_PARAMETERS, fakeTx,
                                Coin.NEGATIVE_SATOSHI, new byte[] {}));
                    // Add the actual output we will spend later.
                    fakeTx.addOutput(fakeOutput);
                }

                viewModel.walletToSweep.clearTransactions(0);
                for (final Transaction tx : fakeTxns.values())
                    viewModel.walletToSweep
                            .addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, tx));
                log.info("built wallet to sweep:\n{}",
                        viewModel.walletToSweep.toString(false, false, null, true, false, null));

                updateView();
            }

            private boolean utxoSpentBy(final Set<Transaction> transactions, final UTXO utxo) {
                for (final Transaction tx : transactions) {
                    for (final TransactionInput input : tx.getInputs()) {
                        final TransactionOutPoint outpoint = input.getOutpoint();
                        if (outpoint.getHash().equals(utxo.getHash()) && outpoint.getIndex() == utxo.getIndex())
                            return true;
                    }
                }
                return false;
            }

            @Override
            public void onFail(final int messageResId, final Object... messageArgs) {
                viewModel.progress.setValue(null);
                viewModel.showDialogWithRetryRequestBalance.setValue(DialogEvent.warn(
                        R.string.sweep_wallet_fragment_request_wallet_balance_failed_title, messageResId, messageArgs));
            }
        };

        final ECKey key = viewModel.walletToSweep.getImportedKeys().iterator().next();
        new RequestWalletBalanceTask(backgroundHandler, callback).requestWalletBalance(activity.getAssets(), key);
    }

    private void setState(final SweepWalletViewModel.State state) {
        viewModel.state = state;

        updateView();
    }

    private void updateView() {
        final Map<FeeCategory, Coin> fees = viewModel.getDynamicFees().getValue();
        final MonetaryFormat btcFormat = config.getFormat();

        if (viewModel.walletToSweep != null) {
            balanceView.setVisibility(View.VISIBLE);
            final MonetarySpannable balanceSpannable = new MonetarySpannable(btcFormat,
                    viewModel.walletToSweep.getBalance(BalanceType.ESTIMATED));
            balanceSpannable.applyMarkup(null, null);
            final SpannableStringBuilder balance = new SpannableStringBuilder(balanceSpannable);
            balance.insert(0, ": ");
            balance.insert(0, getString(R.string.sweep_wallet_fragment_balance));
            balanceView.setText(balance);
        } else {
            balanceView.setVisibility(View.GONE);
        }

        if (viewModel.state == SweepWalletViewModel.State.DECODE_KEY && viewModel.privateKeyToSweep == null) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(R.string.sweep_wallet_fragment_wallet_unknown);
        } else if (viewModel.state == SweepWalletViewModel.State.DECODE_KEY && viewModel.privateKeyToSweep != null) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(R.string.sweep_wallet_fragment_encrypted);
        } else if (viewModel.privateKeyToSweep != null) {
            messageView.setVisibility(View.GONE);
        }

        passwordViewGroup.setVisibility(
                viewModel.state == SweepWalletViewModel.State.DECODE_KEY && viewModel.privateKeyToSweep != null
                        ? View.VISIBLE : View.GONE);

        hintView.setVisibility(
                viewModel.state == SweepWalletViewModel.State.DECODE_KEY && viewModel.privateKeyToSweep == null
                        ? View.VISIBLE : View.GONE);

        final Transaction sentTransaction = viewModel.sentTransaction.getValue();
        if (sentTransaction != null) {
            sweepTransactionView.setVisibility(View.VISIBLE);
            sweepTransactionViewHolder
                    .fullBind(new TransactionsAdapter.ListItem.TransactionItem(activity, sentTransaction,
                            application.getWallet(), null, btcFormat, application.maxConnectedPeers()));
        } else {
            sweepTransactionView.setVisibility(View.GONE);
        }

        if (viewModel.state == SweepWalletViewModel.State.DECODE_KEY) {
            viewCancel.setText(R.string.button_cancel);
            viewGo.setText(R.string.sweep_wallet_fragment_button_decrypt);
            viewGo.setEnabled(viewModel.privateKeyToSweep != null);
        } else if (viewModel.state == SweepWalletViewModel.State.CONFIRM_SWEEP) {
            viewCancel.setText(R.string.button_cancel);
            viewGo.setText(R.string.sweep_wallet_fragment_button_sweep);
            viewGo.setEnabled(viewModel.walletToSweep != null
                    && viewModel.walletToSweep.getBalance(BalanceType.ESTIMATED).signum() > 0 && fees != null);
        } else if (viewModel.state == SweepWalletViewModel.State.PREPARATION) {
            viewCancel.setText(R.string.button_cancel);
            viewGo.setText(R.string.send_coins_preparation_msg);
            viewGo.setEnabled(false);
        } else if (viewModel.state == SweepWalletViewModel.State.SENDING) {
            viewCancel.setText(R.string.send_coins_fragment_button_back);
            viewGo.setText(R.string.send_coins_sending_msg);
            viewGo.setEnabled(false);
        } else if (viewModel.state == SweepWalletViewModel.State.SENT) {
            viewCancel.setText(R.string.send_coins_fragment_button_back);
            viewGo.setText(R.string.send_coins_sent_msg);
            viewGo.setEnabled(false);
        } else if (viewModel.state == SweepWalletViewModel.State.FAILED) {
            viewCancel.setText(R.string.send_coins_fragment_button_back);
            viewGo.setText(R.string.send_coins_failed_msg);
            viewGo.setEnabled(false);
        }

        viewCancel.setEnabled(viewModel.state != SweepWalletViewModel.State.PREPARATION);

        // enable actions
        if (reloadAction != null)
            reloadAction.setEnabled(
                    viewModel.state == SweepWalletViewModel.State.CONFIRM_SWEEP && viewModel.walletToSweep != null);
        if (scanAction != null)
            scanAction.setEnabled(viewModel.state == SweepWalletViewModel.State.DECODE_KEY
                    || viewModel.state == SweepWalletViewModel.State.CONFIRM_SWEEP);
    }

    private void handleDecrypt() {
        handler.post(maybeDecodeKeyRunnable);
    }

    private void handleSweep() {
        setState(SweepWalletViewModel.State.PREPARATION);

        final Map<FeeCategory, Coin> fees = viewModel.getDynamicFees().getValue();
        final SendRequest sendRequest = SendRequest.emptyWallet(application.getWallet().freshReceiveAddress());
        sendRequest.feePerKb = fees.get(FeeCategory.NORMAL);

        new SendCoinsOfflineTask(viewModel.walletToSweep, backgroundHandler) {
            @Override
            protected void onSuccess(final Transaction transaction) {
                viewModel.sentTransaction.setValue(transaction);
                setState(SweepWalletViewModel.State.SENDING);

                application.processDirectTransaction(transaction);
            }

            @Override
            protected void onInsufficientMoney(@Nullable final Coin missing) {
                setState(SweepWalletViewModel.State.FAILED);
                viewModel.showDialog.setValue(DialogEvent.warn(
                        R.string.sweep_wallet_fragment_insufficient_money_title,
                        R.string.sweep_wallet_fragment_insufficient_money_msg)
                );
            }

            @Override
            protected void onEmptyWalletFailed() {
                setState(SweepWalletViewModel.State.FAILED);
                viewModel.showDialog.setValue(DialogEvent.warn(
                        R.string.sweep_wallet_fragment_insufficient_money_title,
                        R.string.sweep_wallet_fragment_insufficient_money_msg)
                );
            }

            @Override
            protected void onFailure(final Exception exception) {
                setState(SweepWalletViewModel.State.FAILED);
                viewModel.showDialog.setValue(DialogEvent.warn(0, R.string.send_coins_error_msg,
                        exception.toString())
                );
            }

            @Override
            protected void onInvalidEncryptionKey() {
                throw new RuntimeException(); // cannot happen
            }
        }.sendCoinsOffline(sendRequest); // send asynchronously
    }

    private static class FakeTransaction extends Transaction {
        private final Sha256Hash txId, wTxId;

        public FakeTransaction(final NetworkParameters params, final Sha256Hash txId, final Sha256Hash wTxId) {
            super(params);
            this.txId = txId;
            this.wTxId = wTxId;
        }

        @Override
        public Sha256Hash getTxId() {
            return txId;
        }

        @Override
        public Sha256Hash getWTxId() {
            return wTxId;
        }
    }
}
