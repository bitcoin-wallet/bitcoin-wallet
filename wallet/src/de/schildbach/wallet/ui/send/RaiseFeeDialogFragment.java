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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.AbstractWalletActivityViewModel;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static androidx.core.util.Preconditions.checkNotNull;

/**
 * @author Andreas Schildbach
 */
public class RaiseFeeDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = RaiseFeeDialogFragment.class.getName();
    private static final String KEY_TRANSACTION = "transaction";

    public static void show(final FragmentManager fm, final Sha256Hash transactionId) {
        final DialogFragment newFragment = instance(transactionId);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private static RaiseFeeDialogFragment instance(final Sha256Hash transactionId) {
        final RaiseFeeDialogFragment fragment = new RaiseFeeDialogFragment();

        final Bundle args = new Bundle();
        args.putByteArray(KEY_TRANSACTION, transactionId.getBytes());
        fragment.setArguments(args);

        return fragment;
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;

    @Nullable
    private Coin feeRaise = null;
    @Nullable
    private Transaction transaction = null;

    @Nullable
    private AlertDialog dialog;

    private TextView messageView;
    private View passwordGroup;
    private EditText passwordView;
    private View badPasswordView;
    private Button positiveButton, negativeButton;

    private AbstractWalletActivityViewModel walletActivityViewModel;
    private RaiseFeeViewModel viewModel;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private enum State {
        INPUT, DECRYPTING, DONE
    }

    private State state = State.INPUT;

    private static final Logger log = LoggerFactory.getLogger(RaiseFeeDialogFragment.class);

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
        log.info("opening dialog {}", getClass().getName());

        walletActivityViewModel = new ViewModelProvider(activity).get(AbstractWalletActivityViewModel.class);
        walletActivityViewModel.wallet.observe(this, wallet -> {
            final Bundle args = getArguments();
            transaction = checkNotNull(wallet.getTransaction(Sha256Hash.wrap(args.getByteArray(KEY_TRANSACTION))));
            updateView();

            viewModel.getDynamicFees().observe(this, dynamicFees -> {
                // We basically have to pay fee for two transactions:
                // The transaction to raise the fee of and the CPFP transaction we're about to create.
                final int size = transaction.getMessageSize() + 192;
                feeRaise = dynamicFees.get(FeeCategory.PRIORITY).multiply(size).divide(1000);
                updateView();
            });
        });
        viewModel = new ViewModelProvider(this).get(RaiseFeeViewModel.class);

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(activity).inflate(R.layout.raise_fee_dialog, null);

        messageView = view.findViewById(R.id.raise_fee_dialog_message);

        passwordGroup = view.findViewById(R.id.raise_fee_dialog_password_group);

        passwordView = view.findViewById(R.id.raise_fee_dialog_password);
        passwordView.setText(null);

        badPasswordView = view.findViewById(R.id.raise_fee_dialog_bad_password);

        final DialogBuilder builder = DialogBuilder.custom(activity, R.string.raise_fee_dialog_title, view);
        // dummies, just to make buttons show
        builder.setPositiveButton(R.string.raise_fee_dialog_button_raise, null);
        builder.setNegativeButton(R.string.button_dismiss, null);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        dialog.setOnShowListener(d -> {
            positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);

            positiveButton.setTypeface(Typeface.DEFAULT_BOLD);
            positiveButton.setOnClickListener(v -> handleGo());
            negativeButton.setOnClickListener(v -> dismissAllowingStateLoss());

            passwordView.addTextChangedListener(textWatcher);

            RaiseFeeDialogFragment.this.dialog = dialog;
            updateView();
        });

        log.info("showing raise fee dialog");

        return dialog;
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        this.dialog = null;

        wipePasswords();

        super.onDismiss(dialog);
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    private void handleGo() {
        state = State.DECRYPTING;
        updateView();

        final Wallet wallet = walletActivityViewModel.wallet.getValue();

        if (wallet.isEncrypted()) {
            new DeriveKeyTask(backgroundHandler, application.scryptIterationsTarget()) {
                @Override
                protected void onSuccess(final KeyParameter encryptionKey, final boolean wasChanged) {
                    if (wasChanged)
                        WalletUtils.autoBackupWallet(activity, wallet);
                    doRaiseFee(wallet, encryptionKey);
                }
            }.deriveKey(wallet, passwordView.getText().toString().trim());

            updateView();
        } else {
            doRaiseFee(wallet, null);
        }
    }

    private void doRaiseFee(final Wallet wallet, final KeyParameter encryptionKey) {
        // construct child-pays-for-parent
        final TransactionOutput outputToSpend = checkNotNull(findSpendableOutput(wallet, transaction, feeRaise));
        final Transaction transactionToSend = new Transaction(Constants.NETWORK_PARAMETERS);
        transactionToSend.addInput(outputToSpend);
        transactionToSend.addOutput(outputToSpend.getValue().subtract(feeRaise),
                wallet.freshAddress(KeyPurpose.CHANGE));
        transactionToSend.setPurpose(Transaction.Purpose.RAISE_FEE);

        final SendRequest sendRequest = SendRequest.forTx(transactionToSend);
        sendRequest.aesKey = encryptionKey;

        try {
            wallet.signTransaction(sendRequest);

            log.info("raise fee: cpfp {}", transactionToSend);

            walletActivityViewModel.broadcastTransaction(transactionToSend);

            state = State.DONE;
            updateView();

            dismiss();
        } catch (final KeyCrypterException x) {
            badPasswordView.setVisibility(View.VISIBLE);

            state = State.INPUT;
            updateView();

            passwordView.requestFocus();

            log.info("raise fee: bad spending password");
        }
    }

    private void wipePasswords() {
        passwordView.setText(null);
    }

    private void updateView() {
        if (dialog == null)
            return;

        final Wallet wallet = walletActivityViewModel.wallet.getValue();
        final boolean needsPassword = wallet != null && wallet.isEncrypted();

        if (wallet == null || transaction == null || feeRaise == null) {
            messageView.setText(R.string.raise_fee_dialog_determining_fee);
            passwordGroup.setVisibility(View.GONE);
        } else if (findSpendableOutput(wallet, transaction, feeRaise) == null) {
            messageView.setText(R.string.raise_fee_dialog_cant_raise);
            passwordGroup.setVisibility(View.GONE);
        } else {
            messageView.setText(getString(R.string.raise_fee_dialog_message, config.getFormat().format(feeRaise)));
            passwordGroup.setVisibility(needsPassword ? View.VISIBLE : View.GONE);
        }

        if (state == State.INPUT) {
            positiveButton.setText(R.string.raise_fee_dialog_button_raise);
            positiveButton.setEnabled((!needsPassword || passwordView.getText().toString().trim().length() > 0)
                    && wallet != null && transaction != null && feeRaise != null && findSpendableOutput(wallet,
                    transaction, feeRaise) != null);
            negativeButton.setEnabled(true);
        } else if (state == State.DECRYPTING) {
            positiveButton.setText(R.string.raise_fee_dialog_state_decrypting);
            positiveButton.setEnabled(false);
            negativeButton.setEnabled(false);
        } else if (state == State.DONE) {
            positiveButton.setText(R.string.raise_fee_dialog_state_done);
            positiveButton.setEnabled(false);
            negativeButton.setEnabled(false);
        }
    }

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            badPasswordView.setVisibility(View.INVISIBLE);
            updateView();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void afterTextChanged(final Editable s) {
        }
    };

    public static boolean feeCanLikelyBeRaised(final Wallet wallet, final Transaction transaction) {
        if (transaction.getConfidence().getDepthInBlocks() > 0)
            return false;

        if (WalletUtils.isPayToManyTransaction(transaction))
            return false;

        // We don't know dynamic fees here, so we need to guess.
        if (findSpendableOutput(wallet, transaction, Transaction.DEFAULT_TX_FEE) == null)
            return false;

        return true;
    }

    private static @Nullable TransactionOutput findSpendableOutput(final Wallet wallet, final Transaction transaction,
            final Coin minimumOutputValue) {
        for (final TransactionOutput output : transaction.getOutputs()) {
            if (output.isMine(wallet) && output.isAvailableForSpending()
                    && output.getValue().isGreaterThan(minimumOutputValue))
                return output;
        }

        return null;
    }
}
