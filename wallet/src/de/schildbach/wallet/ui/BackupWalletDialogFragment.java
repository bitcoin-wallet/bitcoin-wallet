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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.bitcoinj.core.Wallet;
import org.bitcoinj.store.WalletProtobufSerializer;
import org.bitcoinj.wallet.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
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

import com.google.common.base.Charsets;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.Iso8601Format;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.util.WholeStringBuilder;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class BackupWalletDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = BackupWalletDialogFragment.class.getName();

	public static void show(final FragmentManager fm)
	{
		final DialogFragment newFragment = new BackupWalletDialogFragment();
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Wallet wallet;

	@CheckForNull
	private AlertDialog dialog;

	private EditText passwordView, passwordAgainView;
	private TextView passwordStrengthView;
	private View passwordMismatchView;
	private CheckBox showView;
	private Button positiveButton;

	private static final Logger log = LoggerFactory.getLogger(BackupWalletDialogFragment.class);

	private final TextWatcher textWatcher = new TextWatcher()
	{
		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
			passwordMismatchView.setVisibility(View.INVISIBLE);
			updateView();
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		@Override
		public void afterTextChanged(final Editable s)
		{
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
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

		dialog.setOnShowListener(new OnShowListener()
		{
			@Override
			public void onShow(final DialogInterface d)
			{
				positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
				positiveButton.setTypeface(Typeface.DEFAULT_BOLD);
				positiveButton.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(final View v)
					{
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
	public void onDismiss(final DialogInterface dialog)
	{
		wipePasswords();

		this.dialog = null;

		passwordView.removeTextChangedListener(textWatcher);
		passwordAgainView.removeTextChangedListener(textWatcher);

		showView.setOnCheckedChangeListener(null);

		super.onDismiss(dialog);
	}

	private void handleGo()
	{
		final String password = passwordView.getText().toString().trim();
		final String passwordAgain = passwordAgainView.getText().toString().trim();

		if (passwordAgain.equals(password))
		{
			passwordView.setText(null); // get rid of it asap
			passwordAgainView.setText(null);

			backupWallet(password);

			dismiss();

			application.getConfiguration().disarmBackupReminder();
		}
		else
		{
			passwordMismatchView.setVisibility(View.VISIBLE);
		}
	}

	private void wipePasswords()
	{
		passwordView.setText(null);
	}

	private void updateView()
	{
		if (dialog == null)
			return;

		final int passwordLength = passwordView.getText().length();
		passwordStrengthView.setVisibility(passwordLength > 0 ? View.VISIBLE : View.INVISIBLE);
		if (passwordLength < 6)
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_weak);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_weak));
		}
		else if (passwordLength < 8)
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_fair);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_fair));
		}
		else if (passwordLength < 10)
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_good);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_less_significant));
		}
		else
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_strong);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_strong));
		}

		final boolean hasPassword = !passwordView.getText().toString().trim().isEmpty();
		final boolean hasPasswordAgain = !passwordAgainView.getText().toString().trim().isEmpty();

		positiveButton.setEnabled(hasPassword && hasPasswordAgain);
	}

	private void backupWallet(@Nonnull final String password)
	{
		Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.mkdirs();
		final DateFormat dateFormat = Iso8601Format.newDateFormat();
		dateFormat.setTimeZone(TimeZone.getDefault());
		final File file = new File(Constants.Files.EXTERNAL_WALLET_BACKUP_DIR, Constants.Files.EXTERNAL_WALLET_BACKUP + "-"
				+ dateFormat.format(new Date()));

		final Protos.Wallet walletProto = new WalletProtobufSerializer().walletToProto(wallet);

		Writer cipherOut = null;

		try
		{
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			walletProto.writeTo(baos);
			baos.close();
			final byte[] plainBytes = baos.toByteArray();

			cipherOut = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
			cipherOut.write(Crypto.encrypt(plainBytes, password.toCharArray()));
			cipherOut.flush();

			final DialogBuilder dialog = new DialogBuilder(activity);
			dialog.setMessage(Html.fromHtml(getString(R.string.export_keys_dialog_success, file)));
			dialog.setPositiveButton(WholeStringBuilder.bold(getString(R.string.export_keys_dialog_button_archive)),
					new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(final DialogInterface dialog, final int which)
						{
							archiveWalletBackup(file);
						}
					});
			dialog.setNegativeButton(R.string.button_dismiss, null);
			dialog.show();

			log.info("backed up wallet to: '" + file + "'");
		}
		catch (final IOException x)
		{
			final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.import_export_keys_dialog_failure_title);
			dialog.setMessage(getString(R.string.export_keys_dialog_failure, x.getMessage()));
			dialog.singleDismissButton(null);
			dialog.show();

			log.error("problem backing up wallet", x);
		}
		finally
		{
			if (cipherOut != null)
			{
				try
				{
					cipherOut.close();
				}
				catch (final IOException x)
				{
					// swallow
				}
			}
		}
	}

	private void archiveWalletBackup(@Nonnull final File file)
	{
		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_keys_dialog_mail_subject));
		intent.putExtra(Intent.EXTRA_TEXT,
				getString(R.string.export_keys_dialog_mail_text) + "\n\n" + String.format(Constants.WEBMARKET_APP_URL, activity.getPackageName())
						+ "\n\n" + Constants.SOURCE_URL + '\n');
		intent.setType(Constants.MIMETYPE_WALLET_BACKUP);
		intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));

		try
		{
			startActivity(Intent.createChooser(intent, getString(R.string.export_keys_dialog_mail_intent_chooser)));
			log.info("invoked chooser for archiving wallet backup");
		}
		catch (final Exception x)
		{
			new Toast(activity).longToast(R.string.export_keys_dialog_mail_intent_failed);
			log.error("archiving wallet backup failed", x);
		}
	}
}
