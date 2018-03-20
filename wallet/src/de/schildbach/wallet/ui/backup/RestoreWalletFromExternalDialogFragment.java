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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.backup;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.ShowPasswordCheckListener;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.Io;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnShowListener;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

/**
 * @author Andreas Schildbach
 */
public class RestoreWalletFromExternalDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = RestoreWalletFromExternalDialogFragment.class.getName();
    private static final String KEY_BACKUP_URI = "backup_uri";

    public static void show(final FragmentManager fm, final Uri backupUri) {
        final DialogFragment newFragment = new RestoreWalletFromExternalDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(KEY_BACKUP_URI, backupUri);
        newFragment.setArguments(args);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private ContentResolver contentResolver;
    private Configuration config;
    private Wallet wallet;
    private Uri backupUri;

    private EditText passwordView;
    private CheckBox showView;
    private View replaceWarningView;

    @Nullable
    private AlertDialog dialog;

    private static final Logger log = LoggerFactory.getLogger(RestoreWalletFromExternalDialogFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.contentResolver = application.getContentResolver();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.backupUri = (Uri) getArguments().getParcelable(KEY_BACKUP_URI);
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(activity).inflate(R.layout.restore_wallet_from_external_dialog, null);
        passwordView = (EditText) view.findViewById(R.id.import_keys_from_content_dialog_password);
        showView = (CheckBox) view.findViewById(R.id.import_keys_from_content_dialog_show);
        replaceWarningView = view.findViewById(R.id.restore_wallet_from_content_dialog_replace_warning);

        final DialogBuilder builder = new DialogBuilder(activity);
        builder.setTitle(R.string.import_keys_dialog_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final String password = passwordView.getText().toString().trim();
                passwordView.setText(null); // get rid of it asap
                handleRestore(password);
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                passwordView.setText(null); // get rid of it asap
                activity.finish();
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(final DialogInterface dialog) {
                passwordView.setText(null); // get rid of it asap
                activity.finish();
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
                        return true;
                    }
                };
                passwordView.addTextChangedListener(dialogButtonEnabler);

                RestoreWalletFromExternalDialogFragment.this.dialog = dialog;
                updateView();
            }
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
        super.onDismiss(dialog);
    }

    private void updateView() {
        if (dialog == null)
            return;

        final boolean hasCoins = wallet.getBalance(BalanceType.ESTIMATED).signum() > 0;
        replaceWarningView.setVisibility(hasCoins ? View.VISIBLE : View.GONE);

        showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
    }

    private void handleRestore(final String password) {
        try {
            final InputStream is = contentResolver.openInputStream(backupUri);
            restoreWalletFromEncrypted(is, password);
            config.disarmBackupReminder();
            log.info("successfully restored encrypted wallet from external source");

            final DialogBuilder dialog = new DialogBuilder(activity);
            final StringBuilder message = new StringBuilder();
            message.append(getString(R.string.restore_wallet_dialog_success));
            message.append("\n\n");
            message.append(getString(R.string.restore_wallet_dialog_success_replay));
            if (application.getWallet().isEncrypted()) {
                message.append("\n\n");
                message.append(getString(R.string.restore_wallet_dialog_success_encrypted));
            }
            dialog.setMessage(message);
            dialog.setNeutralButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    BlockchainService.resetBlockchain(activity);
                    activity.finish();
                }
            });
            dialog.show();
        } catch (final IOException x) {
            log.info("problem restoring wallet", x);

            final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.import_export_keys_dialog_failure_title);
            dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
            dialog.setPositiveButton(R.string.button_dismiss, finishListener).setOnCancelListener(finishListener);
            dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    show(activity.getSupportFragmentManager(), backupUri);
                }
            });
            dialog.show();
        }
    }

    private void restoreWalletFromEncrypted(final InputStream cipher, final String password) throws IOException {
        final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(cipher, StandardCharsets.UTF_8));
        final StringBuilder cipherText = new StringBuilder();
        Io.copy(cipherIn, cipherText, Constants.BACKUP_MAX_CHARS);
        cipherIn.close();

        final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
        final InputStream is = new ByteArrayInputStream(plainText);

        application.replaceWallet(WalletUtils.restoreWalletFromProtobufOrBase58(is, Constants.NETWORK_PARAMETERS));
    }

    private class FinishListener implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            activity.finish();
        }

        @Override
        public void onCancel(final DialogInterface dialog) {
            activity.finish();
        }
    }

    private final FinishListener finishListener = new FinishListener();
}
