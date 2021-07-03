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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.common.util.concurrent.ListenableFuture;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DeterministicUpgradeRequiresPassword;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * @author Andreas Schildbach
 */
public class MaintenanceDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = MaintenanceDialogFragment.class.getName();

    public static void show(final FragmentManager fm) {
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new MaintenanceDialogFragment();
            fm.beginTransaction().add(fragment, FRAGMENT_TAG).commit();
        }
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Wallet wallet;

    @Nullable
    private AlertDialog dialog;

    private View passwordGroup;
    private EditText passwordView;
    private View badPasswordView;
    private Button positiveButton, negativeButton;

    private Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private enum State {
        INPUT, DECRYPTING, DONE
    }

    private State state = State.INPUT;

    private static final Logger log = LoggerFactory.getLogger(MaintenanceDialogFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.wallet = application.getWallet();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("opening dialog {}", getClass().getName());

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(activity).inflate(R.layout.maintenance_dialog, null);

        Coin value = Coin.ZERO;
        Coin fee = Coin.ZERO;
        for (final Transaction tx : determineMaintenanceTransactions()) {
            value = value.add(tx.getValueSentFromMe(wallet));
            fee = fee.add(tx.getFee());
        }
        final TextView messageView = view.findViewById(R.id.maintenance_dialog_message);
        final MonetaryFormat format = application.getConfiguration().getFormat();
        messageView.setText(getString(R.string.maintenance_dialog_message, format.format(value), format.format(fee)));

        passwordGroup = view.findViewById(R.id.maintenance_dialog_password_group);

        passwordView = view.findViewById(R.id.maintenance_dialog_password);
        passwordView.setText(null);

        badPasswordView = view.findViewById(R.id.maintenance_dialog_bad_password);

        final DialogBuilder builder = DialogBuilder.custom(activity, R.string.maintenance_dialog_title, view);
        // dummies, just to make buttons show
        builder.setPositiveButton(R.string.maintenance_dialog_button_move, null);
        builder.setNegativeButton(R.string.button_dismiss, null);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        dialog.setOnShowListener(d -> {
            positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);

            positiveButton.setTypeface(Typeface.DEFAULT_BOLD);
            positiveButton.setOnClickListener(v -> {
                log.info("user decided to do maintenance");
                handleGo();
            });
            negativeButton.setOnClickListener(v -> {
                log.info("user decided to dismiss");
                dismissAllowingStateLoss();
            });

            passwordView.addTextChangedListener(textWatcher);

            MaintenanceDialogFragment.this.dialog = dialog;
            updateView();
        });

        log.info("showing maintenance dialog");

        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();

        updateView();
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

        if (wallet.isEncrypted()) {
            new DeriveKeyTask(backgroundHandler, application.scryptIterationsTarget()) {
                @Override
                protected void onSuccess(final KeyParameter encryptionKey, final boolean wasChanged) {
                    if (wasChanged)
                        WalletUtils.autoBackupWallet(activity, wallet);
                    doMaintenance(encryptionKey);
                }
            }.deriveKey(wallet, passwordView.getText().toString().trim());

            updateView();
        } else {
            doMaintenance(null);
        }
    }

    private void doMaintenance(final KeyParameter encryptionKey) {
        backgroundHandler.post(() -> {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

            try {
                wallet.doMaintenance(encryptionKey, true);

                handler.post(() -> {
                    state = State.DONE;
                    updateView();

                    delayedDismiss();
                });
            } catch (final KeyCrypterException x) {
                handler.post(() -> {
                    badPasswordView.setVisibility(View.VISIBLE);

                    state = State.INPUT;
                    updateView();

                    passwordView.requestFocus();

                    log.info("bad spending password");
                });
            }
        });
    }

    private void delayedDismiss() {
        handler.postDelayed(() -> dismiss(), 2000);
    }

    private void wipePasswords() {
        passwordView.setText(null);
    }

    private void updateView() {
        if (dialog == null)
            return;

        final boolean needsPassword = wallet.isEncrypted();
        passwordGroup.setVisibility(needsPassword ? View.VISIBLE : View.GONE);

        if (state == State.INPUT) {
            positiveButton.setText(R.string.maintenance_dialog_button_move);
            positiveButton.setEnabled(!needsPassword || passwordView.getText().toString().trim().length() > 0);
            negativeButton.setEnabled(true);
        } else if (state == State.DECRYPTING) {
            positiveButton.setText(R.string.maintenance_dialog_state_decrypting);
            positiveButton.setEnabled(false);
            negativeButton.setEnabled(false);
        } else if (state == State.DONE) {
            positiveButton.setText(R.string.maintenance_dialog_state_done);
            positiveButton.setEnabled(false);
            negativeButton.setEnabled(false);
        }
    }

    private List<Transaction> determineMaintenanceTransactions() {
        try {
            final ListenableFuture<List<Transaction>> result = wallet.doMaintenance(null, false);
            return result.get();
        } catch (final DeterministicUpgradeRequiresPassword x) {
            return Collections.emptyList();
        } catch (final Exception x) {
            throw new RuntimeException(x);
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
}
