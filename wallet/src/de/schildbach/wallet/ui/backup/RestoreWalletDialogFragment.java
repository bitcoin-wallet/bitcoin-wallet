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

package de.schildbach.wallet.ui.backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.common.io.CharStreams;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.Event;
import de.schildbach.wallet.ui.ShowPasswordCheckListener;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Andreas Schildbach
 */
public class RestoreWalletDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = RestoreWalletDialogFragment.class.getName();
    private static final String KEY_BACKUP_URI = "backup_uri";
    private static final int REQUEST_CODE_OPEN_DOCUMENT = 0;

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private ContentResolver contentResolver;
    private Configuration config;
    private FragmentManager fragmentManager;

    private TextView messageView;
    private EditText passwordView;
    private CheckBox showView;
    private View replaceWarningView;

    private RestoreWalletViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(RestoreWalletDialogFragment.class);

    public static void showPick(final FragmentManager fm) {
        final DialogFragment newFragment = new RestoreWalletDialogFragment();
        newFragment.show(fm, FRAGMENT_TAG);
    }

    public static void show(final FragmentManager fm, final Uri backupUri) {
        final DialogFragment newFragment = new RestoreWalletDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(KEY_BACKUP_URI, backupUri);
        newFragment.setArguments(args);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.contentResolver = application.getContentResolver();
        this.config = application.getConfiguration();
        this.fragmentManager = getParentFragmentManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("opening dialog {}", getClass().getName());

        viewModel = new ViewModelProvider(this).get(RestoreWalletViewModel.class);
        viewModel.showSuccessDialog.observe(this, new Event.Observer<Boolean>() {
            @Override
            protected void onEvent(final Boolean showEncryptedMessage) {
                SuccessDialogFragment.showDialog(fragmentManager, showEncryptedMessage);
            }
        });
        viewModel.showFailureDialog.observe(this, new Event.Observer<String>() {
            @Override
            protected void onEvent(final String message) {
                FailureDialogFragment.showDialog(fragmentManager, message, viewModel.backupUri.getValue());
            }
        });
        viewModel.backupUri.observe(this, uri -> {
            final String backupProvider = WalletUtils.uriToProvider(uri);
            log.info("picked '{}'{}", uri, backupProvider != null ? " (" + backupProvider + ")" : "");
            final Cursor cursor = contentResolver.query(uri, null, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst())
                        viewModel.displayName.setValue(cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
                } finally {
                    cursor.close();
                }
            }
        });
        viewModel.displayName.observe(this, name -> messageView.setText(name));

        final Bundle args = getArguments();
        if (args != null) {
            viewModel.backupUri.setValue((Uri) args.getParcelable(KEY_BACKUP_URI));
        } else {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
            } catch (final ActivityNotFoundException x) {
                log.warn("Cannot open document selector: {}", intent);
                new Toast(activity).longToast(R.string.toast_start_storage_provider_selector_failed);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    viewModel.backupUri.setValue(data.getData());
                } else {
                    log.info("didn't get uri");
                    dismiss();
                    maybeFinishActivity();
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                log.info("cancelled restoring wallet");
                dismiss();
                maybeFinishActivity();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(activity).inflate(R.layout.restore_wallet_dialog, null);
        messageView = view.findViewById(R.id.restore_wallet_dialog_message);
        passwordView = view.findViewById(R.id.restore_wallet_dialog_password);
        showView = view.findViewById(R.id.restore_wallet_dialog_show);
        replaceWarningView = view.findViewById(R.id.restore_wallet_dialog_replace_warning);

        final DialogBuilder builder = DialogBuilder.custom(activity, R.string.import_keys_dialog_title, view);
        builder.setPositiveButton(R.string.import_keys_dialog_button_import, (dialog, which) -> {
            final String password = passwordView.getText().toString().trim();
            passwordView.setText(null); // get rid of it asap
            handleRestore(password);
        });
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> {
            passwordView.setText(null); // get rid of it asap
            maybeFinishActivity();
        });
        builder.setOnCancelListener(dialog -> {
            passwordView.setText(null); // get rid of it asap
            maybeFinishActivity();
        });

        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(
                    passwordView, dialog) {
                @Override
                protected boolean hasFile() {
                    return true;
                }
            };
            passwordView.addTextChangedListener(dialogButtonEnabler);
            showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));

            viewModel.balance.observe(RestoreWalletDialogFragment.this, balance -> {
                final boolean hasCoins = balance.signum() > 0;
                replaceWarningView.setVisibility(hasCoins ? View.VISIBLE : View.GONE);
            });
        });

        return dialog;
    }

    private void handleRestore(final String password) {
        final Uri backupUri = viewModel.backupUri.getValue();
        if (backupUri != null) {
            try {
                final InputStream is = contentResolver.openInputStream(backupUri);
                final Wallet restoredWallet = restoreWalletFromEncrypted(is, password);
                application.replaceWallet(restoredWallet);
                config.disarmBackupReminder();
                config.updateLastRestoreTime();
                viewModel.showSuccessDialog.setValue(new Event<>(restoredWallet.isEncrypted()));
                log.info("successfully restored encrypted wallet from external source");
            } catch (final IOException x) {
                viewModel.showFailureDialog.setValue(new Event<>(x.getMessage()));
                log.info("problem restoring wallet", x);
            }
        } else {
            final String message = "no backup data provided";
            viewModel.showFailureDialog.setValue(new Event<>(message));
            log.info("problem restoring wallet: {}", message);
        }
    }

    private Wallet restoreWalletFromEncrypted(final InputStream cipher, final String password) throws IOException {
        final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(cipher, StandardCharsets.UTF_8));
        final StringBuilder cipherText = new StringBuilder();
        CharStreams.copy(cipherIn, cipherText);
        cipherIn.close();

        final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
        final InputStream is = new ByteArrayInputStream(plainText);

        return WalletUtils.restoreWalletFromProtobuf(is, Constants.NETWORK_PARAMETERS);
    }

    public static class SuccessDialogFragment extends DialogFragment {
        private static final String FRAGMENT_TAG = SuccessDialogFragment.class.getName();
        private static final String KEY_SHOW_ENCRYPTED_MESSAGE = "show_encrypted_message";

        private Activity activity;

        public static void showDialog(final FragmentManager fm, final boolean showEncryptedMessage) {
            final DialogFragment newFragment = new SuccessDialogFragment();
            final Bundle args = new Bundle();
            args.putBoolean(KEY_SHOW_ENCRYPTED_MESSAGE, showEncryptedMessage);
            newFragment.setArguments(args);
            newFragment.show(fm, FRAGMENT_TAG);
        }

        @Override
        public void onAttach(final Context context) {
            super.onAttach(context);
            this.activity = (Activity) context;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final boolean showEncryptedMessage = getArguments().getBoolean(KEY_SHOW_ENCRYPTED_MESSAGE);
            final StringBuilder message = new StringBuilder();
            message.append(getString(R.string.restore_wallet_dialog_success));
            message.append("\n\n");
            message.append(getString(R.string.restore_wallet_dialog_success_replay));
            if (showEncryptedMessage) {
                message.append("\n\n");
                message.append(getString(R.string.restore_wallet_dialog_success_encrypted));
            }
            final DialogBuilder dialog = DialogBuilder.dialog(activity, 0, message);
            dialog.setNeutralButton(R.string.button_ok, (dialog1, id) -> {
                BlockchainService.resetBlockchain(activity);
                activity.finish();
            });
            return dialog.create();
        }
    }

    public static class FailureDialogFragment extends DialogFragment {
        private static final String FRAGMENT_TAG = FailureDialogFragment.class.getName();
        private static final String KEY_EXCEPTION_MESSAGE = "exception_message";
        private static final String KEY_BACKUP_URI = "backup_uri";

        private Activity activity;

        public static void showDialog(final FragmentManager fm, final String exceptionMessage, final Uri backupUri) {
            final DialogFragment newFragment = new FailureDialogFragment();
            final Bundle args = new Bundle();
            args.putString(KEY_EXCEPTION_MESSAGE, exceptionMessage);
            args.putParcelable(KEY_BACKUP_URI, checkNotNull(backupUri));
            newFragment.setArguments(args);
            newFragment.show(fm, FRAGMENT_TAG);
        }

        @Override
        public void onAttach(final Context context) {
            super.onAttach(context);
            this.activity = (Activity) context;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final String exceptionMessage = getArguments().getString(KEY_EXCEPTION_MESSAGE);
            final Uri backupUri = checkNotNull((Uri) getArguments().getParcelable(KEY_BACKUP_URI));
            final DialogBuilder dialog = DialogBuilder.warn(activity,
                    R.string.import_export_keys_dialog_failure_title, R.string.import_keys_dialog_failure,
                    exceptionMessage);
            dialog.setPositiveButton(R.string.button_dismiss, (dialog13, which) -> {
                if (activity instanceof RestoreWalletFromExternalActivity)
                    activity.finish();
            });
            dialog.setOnCancelListener(dialog12 -> {
                if (activity instanceof RestoreWalletFromExternalActivity)
                    activity.finish();
            });
            dialog.setNegativeButton(R.string.button_retry, (dialog1, id) -> RestoreWalletDialogFragment.show(getParentFragmentManager(), backupUri));
            return dialog.create();
        }
    }

    private void maybeFinishActivity() {
        if (activity instanceof RestoreWalletFromExternalActivity)
            activity.finish();
    }
}
