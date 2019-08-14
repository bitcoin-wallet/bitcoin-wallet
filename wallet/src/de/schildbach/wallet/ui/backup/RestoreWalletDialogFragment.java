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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import de.schildbach.wallet.util.WalletUtils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnShowListener;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

/**
 * @author Andreas Schildbach
 */
public class RestoreWalletDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = RestoreWalletDialogFragment.class.getName();

    public static void show(final FragmentManager fm) {
        final DialogFragment newFragment = new RestoreWalletDialogFragment();
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private FragmentManager fragmentManager;

    private TextView messageView;
    private Spinner fileView;
    private EditText passwordView;
    private CheckBox showView;
    private View replaceWarningView;

    private RestoreWalletViewModel viewModel;

    private static final int REQUEST_CODE_RESTORE_WALLET = 1;

    private static final Logger log = LoggerFactory.getLogger(RestoreWalletDialogFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.config = application.getConfiguration();
        this.fragmentManager = getFragmentManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("opening dialog {}", getClass().getName());

        viewModel = ViewModelProviders.of(this).get(RestoreWalletViewModel.class);
        viewModel.showSuccessDialog.observe(this, new Event.Observer<Boolean>() {
            @Override
            public void onEvent(final Boolean showEncryptedMessage) {
                SuccessDialogFragment.showDialog(fragmentManager, showEncryptedMessage);
            }
        });
        viewModel.showFailureDialog.observe(this, new Event.Observer<String>() {
            @Override
            public void onEvent(final String message) {
                FailureDialogFragment.showDialog(fragmentManager, message);
            }
        });

        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            log.info("missing {}, requesting", Manifest.permission.READ_EXTERNAL_STORAGE);
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, REQUEST_CODE_RESTORE_WALLET);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
            final int[] grantResults) {
        if (requestCode == REQUEST_CODE_RESTORE_WALLET) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                log.info("missing {}, showing error", Manifest.permission.READ_EXTERNAL_STORAGE);
                PermissionDeniedDialogFragment.showDialog(fragmentManager);
            }
        }
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(activity).inflate(R.layout.restore_wallet_dialog, null);
        messageView = (TextView) view.findViewById(R.id.restore_wallet_dialog_message);
        fileView = (Spinner) view.findViewById(R.id.import_keys_from_storage_file);
        passwordView = (EditText) view.findViewById(R.id.import_keys_from_storage_password);
        showView = (CheckBox) view.findViewById(R.id.import_keys_from_storage_show);
        replaceWarningView = view.findViewById(R.id.restore_wallet_from_storage_dialog_replace_warning);

        final DialogBuilder builder = new DialogBuilder(activity);
        builder.setTitle(R.string.import_keys_dialog_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final File file = (File) fileView.getSelectedItem();
                final String password = passwordView.getText().toString().trim();
                passwordView.setText(null); // get rid of it asap

                if (WalletUtils.BACKUP_FILE_FILTER.accept(file))
                    restoreWalletFromProtobuf(file);
                else if (Crypto.OPENSSL_FILE_FILTER.accept(file))
                    restoreWalletFromEncrypted(file, password);
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                passwordView.setText(null); // get rid of it asap
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(final DialogInterface dialog) {
                passwordView.setText(null); // get rid of it asap
            }
        });

        fileView.setAdapter(new FileAdapter(activity) {
            @Override
            public View getDropDownView(final int position, View row, final ViewGroup parent) {
                final File file = getItem(position);
                final boolean isExternal = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.equals(file.getParentFile());
                final boolean isEncrypted = Crypto.OPENSSL_FILE_FILTER.accept(file);

                if (row == null)
                    row = inflater.inflate(R.layout.restore_wallet_file_row, null);

                final TextView filenameView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_filename);
                filenameView.setText(file.getName());

                final TextView securityView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_security);
                final String encryptedStr = context
                        .getString(isEncrypted ? R.string.import_keys_dialog_file_security_encrypted
                                : R.string.import_keys_dialog_file_security_unencrypted);
                final String storageStr = context
                        .getString(isExternal ? R.string.import_keys_dialog_file_security_external
                                : R.string.import_keys_dialog_file_security_internal);
                securityView.setText(encryptedStr + ", " + storageStr);

                final TextView createdView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_created);
                createdView.setText(context.getString(
                        isExternal ? R.string.import_keys_dialog_file_created_manual
                                : R.string.import_keys_dialog_file_created_automatic,
                        DateUtils.getRelativeTimeSpanString(context, file.lastModified(), true)));

                return row;
            }
        });

        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(final DialogInterface d) {
                final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(
                        passwordView, dialog) {
                    @Override
                    protected boolean hasFile() {
                        return fileView.getSelectedItem() != null;
                    }

                    @Override
                    protected boolean needsPassword() {
                        final File selectedFile = (File) fileView.getSelectedItem();
                        return selectedFile != null ? Crypto.OPENSSL_FILE_FILTER.accept(selectedFile) : false;
                    }
                };
                passwordView.addTextChangedListener(dialogButtonEnabler);
                fileView.setOnItemSelectedListener(dialogButtonEnabler);

                updateView();

                viewModel.balance.observe(RestoreWalletDialogFragment.this, new Observer<Coin>() {
                    @Override
                    public void onChanged(final Coin balance) {
                        final boolean hasCoins = balance.signum() > 0;
                        replaceWarningView.setVisibility(hasCoins ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });

        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateView();
    }

    private void updateView() {
        final String path;
        final String backupPath = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.getAbsolutePath();
        final String storagePath = Constants.Files.EXTERNAL_STORAGE_DIR.getAbsolutePath();
        if (backupPath.startsWith(storagePath))
            path = backupPath.substring(storagePath.length());
        else
            path = backupPath;

        final List<File> files = new LinkedList<File>();

        // external storage
        log.info("looking for backup files in '{}'", Constants.Files.EXTERNAL_WALLET_BACKUP_DIR);
        final File[] externalFiles = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.listFiles();
        if (externalFiles != null) {
            for (final File file : externalFiles) {
                final boolean looksLikeBackup = Crypto.OPENSSL_FILE_FILTER.accept(file);
                if (looksLikeBackup)
                    files.add(file);
            }
        }

        // app-private storage
        log.info("adding backup files from app-private storage");
        for (final String filename : activity.fileList()) {
            if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + '.')) {
                files.add(new File(activity.getFilesDir(), filename));
            }
        }

        // sort
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(final File lhs, final File rhs) {
                return lhs.getName().compareToIgnoreCase(rhs.getName());
            }
        });

        messageView.setText(getString(
                !files.isEmpty() ? R.string.import_keys_dialog_message : R.string.restore_wallet_dialog_message_empty,
                path));

        fileView.setVisibility(!files.isEmpty() ? View.VISIBLE : View.GONE);
        final FileAdapter adapter = (FileAdapter) fileView.getAdapter();
        adapter.setFiles(files);

        passwordView.setVisibility(!files.isEmpty() ? View.VISIBLE : View.GONE);
        passwordView.setText(null);

        showView.setVisibility(!files.isEmpty() ? View.VISIBLE : View.GONE);
        showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
    }

    private void restoreWalletFromEncrypted(final File file, final String password) {
        try {
            final BufferedReader cipherIn = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            final StringBuilder cipherText = new StringBuilder();
            CharStreams.copy(cipherIn, cipherText);
            cipherIn.close();

            final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
            final InputStream is = new ByteArrayInputStream(plainText);

            restoreWallet(WalletUtils.restoreWalletFromProtobuf(is, Constants.NETWORK_PARAMETERS));
            log.info("successfully restored encrypted wallet: {}", file);
        } catch (final IOException x) {
            viewModel.showFailureDialog.setValue(new Event<>(x.getMessage()));
            log.info("problem restoring wallet: " + file, x);
        }
    }

    private void restoreWalletFromProtobuf(final File file) {
        try (final FileInputStream is = new FileInputStream(file)) {
            restoreWallet(WalletUtils.restoreWalletFromProtobuf(is, Constants.NETWORK_PARAMETERS));
            log.info("successfully restored unencrypted wallet: {}", file);
        } catch (final IOException x) {
            viewModel.showFailureDialog.setValue(new Event<>(x.getMessage()));
            log.info("problem restoring unencrypted wallet: " + file, x);
        }
    }

    private void restoreWallet(final Wallet restoredWallet) throws IOException {
        application.replaceWallet(restoredWallet);
        config.disarmBackupReminder();
        config.updateLastRestoreTime();
        viewModel.showSuccessDialog.setValue(new Event<>(restoredWallet.isEncrypted()));
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
            final DialogBuilder dialog = new DialogBuilder(getContext());
            final StringBuilder message = new StringBuilder();
            message.append(getString(R.string.restore_wallet_dialog_success));
            message.append("\n\n");
            message.append(getString(R.string.restore_wallet_dialog_success_replay));
            if (showEncryptedMessage) {
                message.append("\n\n");
                message.append(getString(R.string.restore_wallet_dialog_success_encrypted));
            }
            dialog.setMessage(message);
            dialog.setNeutralButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    BlockchainService.resetBlockchain(getContext());
                    activity.finish();
                }
            });
            return dialog.create();
        }
    }

    public static class FailureDialogFragment extends DialogFragment {
        private static final String FRAGMENT_TAG = FailureDialogFragment.class.getName();
        private static final String KEY_EXCEPTION_MESSAGE = "exception_message";

        public static void showDialog(final FragmentManager fm, final String exceptionMessage) {
            final DialogFragment newFragment = new FailureDialogFragment();
            final Bundle args = new Bundle();
            args.putString(KEY_EXCEPTION_MESSAGE, exceptionMessage);
            newFragment.setArguments(args);
            newFragment.show(fm, FRAGMENT_TAG);
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final String exceptionMessage = getArguments().getString(KEY_EXCEPTION_MESSAGE);
            final DialogBuilder dialog = DialogBuilder.warn(getContext(),
                    R.string.import_export_keys_dialog_failure_title);
            dialog.setMessage(getString(R.string.import_keys_dialog_failure, exceptionMessage));
            dialog.setPositiveButton(R.string.button_dismiss, null);
            dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    RestoreWalletDialogFragment.show(getFragmentManager());
                }
            });
            return dialog.create();
        }
    }

    public static class PermissionDeniedDialogFragment extends DialogFragment {
        private static final String FRAGMENT_TAG = PermissionDeniedDialogFragment.class.getName();

        public static void showDialog(final FragmentManager fm) {
            final DialogFragment newFragment = new PermissionDeniedDialogFragment();
            newFragment.show(fm, FRAGMENT_TAG);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final DialogBuilder dialog = new DialogBuilder(getContext());
            dialog.setTitle(R.string.restore_wallet_permission_dialog_title);
            dialog.setMessage(getString(R.string.restore_wallet_permission_dialog_message));
            dialog.singleDismissButton(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    final DialogFragment fragment = (DialogFragment) getFragmentManager()
                            .findFragmentByTag(RestoreWalletDialogFragment.FRAGMENT_TAG);
                    fragment.dismiss();
                }
            });
            return dialog.create();
        }
    }
}
