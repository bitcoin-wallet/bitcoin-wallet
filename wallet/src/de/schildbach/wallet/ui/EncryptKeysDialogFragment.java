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

package de.schildbach.wallet.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.common.base.Strings;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class EncryptKeysDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = EncryptKeysDialogFragment.class.getName();

    public static void show(final FragmentManager fm) {
        final DialogFragment newFragment = new EncryptKeysDialogFragment();
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;

    @Nullable
    private AlertDialog dialog;

    private View oldPasswordGroup;
    private EditText oldPasswordView;
    private EditText newPasswordView;
    private View badPasswordView;
    private TextView passwordStrengthView;
    private CheckBox showView;
    private Button positiveButton, negativeButton;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private WalletActivityViewModel activityViewModel;

    private enum State {
        INPUT, CRYPTING, DONE
    }

    private State state = State.INPUT;

    private static final Logger log = LoggerFactory.getLogger(EncryptKeysDialogFragment.class);

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

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("opening dialog {}", getClass().getName());

        activityViewModel = new ViewModelProvider(activity).get(WalletActivityViewModel.class);

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(activity).inflate(R.layout.encrypt_keys_dialog, null);

        oldPasswordGroup = view.findViewById(R.id.encrypt_keys_dialog_password_old_group);

        oldPasswordView = view.findViewById(R.id.encrypt_keys_dialog_password_old);
        oldPasswordView.setText(null);

        newPasswordView = view.findViewById(R.id.encrypt_keys_dialog_password_new);
        newPasswordView.setText(null);

        badPasswordView = view.findViewById(R.id.encrypt_keys_dialog_bad_password);

        passwordStrengthView = view.findViewById(R.id.encrypt_keys_dialog_password_strength);

        showView = view.findViewById(R.id.encrypt_keys_dialog_show);

        final DialogBuilder builder = DialogBuilder.custom(activity, R.string.encrypt_keys_dialog_title, view);
        // dummies, just to make buttons show
        builder.setPositiveButton(R.string.button_ok, null);
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        dialog.setOnShowListener((OnShowListener) d -> {
            positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);

            positiveButton.setTypeface(Typeface.DEFAULT_BOLD);
            positiveButton.setOnClickListener(v -> handleGo());

            negativeButton.setOnClickListener(v -> dismissAllowingStateLoss());

            oldPasswordView.addTextChangedListener(textWatcher);
            newPasswordView.addTextChangedListener(textWatcher);

            showView = dialog.findViewById(R.id.encrypt_keys_dialog_show);
            showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(newPasswordView, oldPasswordView));
            showView.setChecked(true);

            EncryptKeysDialogFragment.this.dialog = dialog;
            updateView();
        });

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

        oldPasswordView.removeTextChangedListener(textWatcher);
        newPasswordView.removeTextChangedListener(textWatcher);

        showView.setOnCheckedChangeListener(null);

        wipePasswords();

        super.onDismiss(dialog);
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    private void handleGo() {
        final String oldPassword = Strings.emptyToNull(oldPasswordView.getText().toString().trim());
        final String newPassword = Strings.emptyToNull(newPasswordView.getText().toString().trim());

        if (oldPassword != null && newPassword != null)
            log.info("changing spending password");
        else if (newPassword != null)
            log.info("setting spending password");
        else if (oldPassword != null)
            log.info("removing spending password");
        else
            throw new IllegalStateException();

        state = State.CRYPTING;
        updateView();

        backgroundHandler.post(() -> {
            // For the old key, we use the key crypter that was used to derive the password in the first
            // place.
            final KeyCrypter oldKeyCrypter = wallet.getKeyCrypter();
            final KeyParameter oldKey = oldKeyCrypter != null && oldPassword != null ?
                    oldKeyCrypter.deriveKey(oldPassword) : null;

            // For the new key, we create a new key crypter according to the desired parameters.
            final KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt(application.scryptIterationsTarget());
            final KeyParameter newKey = newPassword != null ? keyCrypter.deriveKey(newPassword) : null;

            handler.post(() -> {
                // Decrypt from old password
                if (wallet.isEncrypted()) {
                    if (oldKey == null) {
                        log.info("wallet is encrypted, but did not provide spending password");
                        state = State.INPUT;
                        oldPasswordView.requestFocus();
                    } else {
                        try {
                            wallet.decrypt(oldKey);

                            state = State.DONE;
                            log.info("wallet successfully decrypted");
                        } catch (final KeyCrypterException x) {
                            log.info("wallet decryption failed: " + x.getMessage());
                            badPasswordView.setVisibility(View.VISIBLE);
                            state = State.INPUT;
                            oldPasswordView.requestFocus();
                        }
                    }
                }

                // Use opportunity to maybe upgrade wallet
                if (wallet.isDeterministicUpgradeRequired(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE)
                        && !wallet.isEncrypted())
                    wallet.upgradeToDeterministic(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE, null);

                // Encrypt to new password
                if (newKey != null && !wallet.isEncrypted()) {
                    wallet.encrypt(keyCrypter, newKey);
                    config.updateLastEncryptKeysTime();
                    log.info(
                            "wallet successfully encrypted, using key derived by new spending password ({} scrypt iterations)",
                            keyCrypter.getScryptParameters().getN());
                    state = State.DONE;
                }

                updateView();

                if (state == State.DONE) {
                    WalletUtils.autoBackupWallet(activity, wallet);
                    // trigger load manually because of missing callbacks for encryption state
                    activityViewModel.walletEncrypted.load();
                    handler.postDelayed(() -> dismiss(), 2000);
                }
            });
        });
    }

    private void wipePasswords() {
        oldPasswordView.setText(null);
        newPasswordView.setText(null);
    }

    private void updateView() {
        if (dialog == null)
            return;

        final boolean hasOldPassword = !oldPasswordView.getText().toString().trim().isEmpty();
        final boolean hasPassword = !newPasswordView.getText().toString().trim().isEmpty();

        oldPasswordGroup.setVisibility(wallet.isEncrypted() ? View.VISIBLE : View.GONE);
        oldPasswordView.setEnabled(state == State.INPUT);

        newPasswordView.setEnabled(state == State.INPUT);

        final int passwordLength = newPasswordView.getText().length();
        passwordStrengthView.setVisibility(state == State.INPUT && passwordLength > 0 ? View.VISIBLE : View.INVISIBLE);
        if (passwordLength < 4) {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_weak);
            passwordStrengthView.setTextColor(activity.getColor(R.color.fg_password_strength_weak));
        } else if (passwordLength < 6) {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_fair);
            passwordStrengthView.setTextColor(activity.getColor(R.color.fg_password_strength_fair));
        } else if (passwordLength < 8) {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_good);
            passwordStrengthView.setTextColor(activity.getColor(R.color.fg_password_strength_good));
        } else {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_strong);
            passwordStrengthView.setTextColor(activity.getColor(R.color.fg_password_strength_strong));
        }

        showView.setEnabled(state == State.INPUT);

        if (state == State.INPUT) {
            if (wallet.isEncrypted()) {
                positiveButton.setText(hasPassword ? R.string.button_edit : R.string.button_remove);
                positiveButton.setEnabled(hasOldPassword);
            } else {
                positiveButton.setText(R.string.button_set);
                positiveButton.setEnabled(hasPassword);
            }

            negativeButton.setEnabled(true);
        } else if (state == State.CRYPTING) {
            positiveButton.setText(newPasswordView.getText().toString().trim().isEmpty()
                    ? R.string.encrypt_keys_dialog_state_decrypting : R.string.encrypt_keys_dialog_state_encrypting);
            positiveButton.setEnabled(false);
            negativeButton.setEnabled(false);
        } else if (state == State.DONE) {
            positiveButton.setText(R.string.encrypt_keys_dialog_state_done);
            positiveButton.setEnabled(false);
            negativeButton.setEnabled(false);
        }
    }
}
