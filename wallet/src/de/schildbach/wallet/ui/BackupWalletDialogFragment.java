/*
 * Copyright 2015 the original author or authors.
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

package de.schildbach.wallet.ui;

import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.Iso8601Format;
import de.schildbach.wallet.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public class BackupWalletDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = BackupWalletDialogFragment.class.getName();

    public static void show(final FragmentManager fm) {
        final DialogFragment newFragment = new BackupWalletDialogFragment();
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Wallet wallet;

    @Nullable
    private AlertDialog dialog;

    private EditText passwordView, passwordAgainView;
    private TextView passwordStrengthView;
    private View passwordMismatchView;
    private CheckBox showView;
    private Button positiveButton;

    private static final Logger log = LoggerFactory.getLogger(BackupWalletDialogFragment.class);

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            passwordMismatchView.setVisibility(View.INVISIBLE);
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
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractWalletActivity) activity;
        this.application = (WalletApplication) activity.getApplication();
        this.wallet = application.getWallet();
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(activity).inflate(R.layout.backup_wallet_dialog, null);

        passwordView = (EditText) view.findViewById(R.id.backup_wallet_dialog_password);
        passwordView.setText(null);

        passwordAgainView = (EditText) view.findViewById(R.id.backup_wallet_dialog_password_again);
        passwordAgainView.setText(null);

        passwordStrengthView = (TextView) view.findViewById(R.id.backup_wallet_dialog_password_strength);

        passwordMismatchView = view.findViewById(R.id.backup_wallet_dialog_password_mismatch);

        showView = (CheckBox) view.findViewById(R.id.backup_wallet_dialog_show);

        final TextView warningView = (TextView) view.findViewById(R.id.backup_wallet_dialog_warning_encrypted);
        warningView.setVisibility(wallet.isEncrypted() ? View.VISIBLE : View.GONE);

        final DialogBuilder builder = new DialogBuilder(activity);
        builder.setTitle(R.string.export_keys_dialog_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.button_ok, null); // dummy, just to make it show
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(final DialogInterface d) {
                positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                positiveButton.setTypeface(Typeface.DEFAULT_BOLD);
                positiveButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        handleGo();
                    }
                });

                passwordView.addTextChangedListener(textWatcher);
                passwordAgainView.addTextChangedListener(textWatcher);

                showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView, passwordAgainView));

                BackupWalletDialogFragment.this.dialog = dialog;
                updateView();
            }
        });

        return dialog;
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        this.dialog = null;

        passwordView.removeTextChangedListener(textWatcher);
        passwordAgainView.removeTextChangedListener(textWatcher);

        showView.setOnCheckedChangeListener(null);

        wipePasswords();

        super.onDismiss(dialog);
    }

    private void handleGo() {
        final String password = passwordView.getText().toString().trim();
        final String passwordAgain = passwordAgainView.getText().toString().trim();

        if (passwordAgain.equals(password)) {
            passwordView.setText(null); // get rid of it asap
            passwordAgainView.setText(null);

            backupWallet(password);

            dismiss();

            application.getConfiguration().disarmBackupReminder();
        } else {
            passwordMismatchView.setVisibility(View.VISIBLE);
        }
    }

    private void wipePasswords() {
        passwordView.setText(null);
    }

    private void updateView() {
        if (dialog == null)
            return;

        final int passwordLength = passwordView.getText().length();
        passwordStrengthView.setVisibility(passwordLength > 0 ? View.VISIBLE : View.INVISIBLE);
        if (passwordLength < 6) {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_weak);
            passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_weak));
        } else if (passwordLength < 8) {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_fair);
            passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_fair));
        } else if (passwordLength < 10) {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_good);
            passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_less_significant));
        } else {
            passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_strong);
            passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_strong));
        }

        final boolean hasPassword = !passwordView.getText().toString().trim().isEmpty();
        final boolean hasPasswordAgain = !passwordAgainView.getText().toString().trim().isEmpty();

        positiveButton.setEnabled(hasPassword && hasPasswordAgain);
    }

    private void backupWallet(final String password) {
        final File file = determineBackupFile();

        final Protos.Wallet walletProto = new WalletProtobufSerializer().walletToProto(wallet);

        Writer cipherOut = null;

        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            walletProto.writeTo(baos);
            baos.close();
            final byte[] plainBytes = baos.toByteArray();

            cipherOut = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
            cipherOut.write(Crypto.encrypt(plainBytes, password.toCharArray()));
            cipherOut.flush();

            log.info("backed up wallet to: '" + file + "'");

            ArchiveBackupDialogFragment.show(getFragmentManager(), file);
        } catch (final IOException x) {
            final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.import_export_keys_dialog_failure_title);
            dialog.setMessage(getString(R.string.export_keys_dialog_failure, x.getMessage()));
            dialog.singleDismissButton(null);
            dialog.show();

            log.error("problem backing up wallet", x);
        } finally {
            if (cipherOut != null) {
                try {
                    cipherOut.close();
                } catch (final IOException x) {
                    // swallow
                }
            }
        }
    }

    private File determineBackupFile() {
        Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.mkdirs();
        checkState(Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.isDirectory(), "%s is not a directory",
                Constants.Files.EXTERNAL_WALLET_BACKUP_DIR);

        final DateFormat dateFormat = Iso8601Format.newDateFormat();
        dateFormat.setTimeZone(TimeZone.getDefault());

        for (int i = 0; true; i++) {
            final StringBuilder filename = new StringBuilder(Constants.Files.EXTERNAL_WALLET_BACKUP);
            filename.append('-');
            filename.append(dateFormat.format(new Date()));
            if (i > 0)
                filename.append(" (").append(i).append(')');

            final File file = new File(Constants.Files.EXTERNAL_WALLET_BACKUP_DIR, filename.toString());
            if (!file.exists())
                return file;
        }
    }
}
