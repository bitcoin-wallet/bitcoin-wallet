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

package de.schildbach.wallet.ui.backup;

import static android.support.v4.util.Preconditions.checkState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.ShowPasswordCheckListener;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.Iso8601Format;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.Html;
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
    private Button positiveButton, negativeButton;

    private static final int REQUEST_CODE_CREATE_DOCUMENT = 0;

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
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
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
        // dummies, just to make buttons show
        builder.setPositiveButton(R.string.button_ok, null);
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

                negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                negativeButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        dismissAllowingStateLoss();
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
            backupWallet();
        } else {
            passwordMismatchView.setVisibility(View.VISIBLE);
        }
    }

    private void wipePasswords() {
        passwordView.setText(null);
        passwordAgainView.setText(null);
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

    private void backupWallet() {
        passwordView.setEnabled(false);
        passwordAgainView.setEnabled(false);

        final DateFormat dateFormat = Iso8601Format.newDateFormat();
        dateFormat.setTimeZone(TimeZone.getDefault());

        final StringBuilder filename = new StringBuilder(Constants.Files.EXTERNAL_WALLET_BACKUP);
        filename.append('-');
        filename.append(dateFormat.format(new Date()));

        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(Constants.MIMETYPE_WALLET_BACKUP);
        intent.putExtra(Intent.EXTRA_TITLE, filename.toString());
        startActivityForResult(intent, REQUEST_CODE_CREATE_DOCUMENT);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_CREATE_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK) {
                final Uri targetUri = intent.getData();
                final String password = passwordView.getText().toString().trim();
                checkState(!password.isEmpty());
                wipePasswords();
                dismiss();

                final Protos.Wallet walletProto = new WalletProtobufSerializer().walletToProto(wallet);

                try (final Writer cipherOut = new OutputStreamWriter(
                        activity.getContentResolver().openOutputStream(targetUri), StandardCharsets.UTF_8)) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    walletProto.writeTo(baos);
                    baos.close();
                    final byte[] plainBytes = baos.toByteArray();

                    cipherOut.write(Crypto.encrypt(plainBytes, password.toCharArray()));
                    cipherOut.flush();

                    final String target = uriToTarget(targetUri);

                    log.info("backed up wallet to: '" + targetUri + "'" + (target != null ? " (" + target + ")" : ""));
                    final DialogBuilder dialog = new DialogBuilder(activity);
                    dialog.setTitle(R.string.export_keys_dialog_title);
                    dialog.setMessage(Html.fromHtml(
                            getString(R.string.export_keys_dialog_success, target != null ? target : targetUri)));
                    dialog.singleDismissButton(null);
                    dialog.show();

                    application.getConfiguration().disarmBackupReminder();
                } catch (final IOException x) {
                    log.error("problem backing up wallet", x);
                    final DialogBuilder dialog = DialogBuilder.warn(activity,
                            R.string.import_export_keys_dialog_failure_title);
                    dialog.setMessage(getString(R.string.export_keys_dialog_failure, x.getMessage()));
                    dialog.singleDismissButton(null);
                    dialog.show();
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                log.info("cancelled backing up wallet");
                passwordView.setEnabled(true);
                passwordAgainView.setEnabled(true);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    private @Nullable String uriToTarget(final Uri uri) {
        if (!uri.getScheme().equals("content"))
            return null;
        final String host = uri.getHost();
        if ("com.google.android.apps.docs.storage".equals(host))
            return "Google Drive";
        if ("com.android.providers.downloads.documents".equals(host))
            return "internal storage";
        return null;
    }
}
