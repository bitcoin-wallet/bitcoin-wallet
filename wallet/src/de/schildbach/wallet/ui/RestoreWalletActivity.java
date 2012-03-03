/*
 * Copyright 2013-2015 the original author or authors.
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.BalanceType;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.google.common.base.Charsets;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.Io;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class RestoreWalletActivity extends AbstractWalletActivity
{
	private static final int DIALOG_RESTORE_WALLET = 0;

	private WalletApplication application;
	private Configuration config;
	private Wallet wallet;
	private ContentResolver contentResolver;

	private Uri backupFileUri;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = getWalletApplication();
		config = application.getConfiguration();
		wallet = application.getWallet();
		contentResolver = getContentResolver();

		backupFileUri = getIntent().getData();

		showDialog(DIALOG_RESTORE_WALLET);
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == DIALOG_RESTORE_WALLET)
			return createRestoreWalletDialog();
		else
			throw new IllegalArgumentException();
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog)
	{
		if (id == DIALOG_RESTORE_WALLET)
			prepareRestoreWalletDialog(dialog);
	}

	private Dialog createRestoreWalletDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.restore_wallet_from_external_dialog, null);
		final EditText passwordView = (EditText) view.findViewById(R.id.import_keys_from_content_dialog_password);

		final DialogBuilder dialog = new DialogBuilder(this);
		dialog.setTitle(R.string.import_keys_dialog_title);
		dialog.setView(view);
		dialog.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				try
				{
					final InputStream is = contentResolver.openInputStream(backupFileUri);
					restoreWalletFromEncrypted(is, password);
				}
				catch (final FileNotFoundException x)
				{
					// should not happen
					throw new RuntimeException(x);
				}
			}
		});
		dialog.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
				finish();
			}
		});
		dialog.setOnCancelListener(new OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
				finish();
			}
		});

		return dialog.create();
	}

	private void prepareRestoreWalletDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final View replaceWarningView = alertDialog.findViewById(R.id.restore_wallet_from_content_dialog_replace_warning);
		final boolean hasCoins = wallet.getBalance(BalanceType.ESTIMATED).signum() > 0;
		replaceWarningView.setVisibility(hasCoins ? View.VISIBLE : View.GONE);

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.import_keys_from_content_dialog_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog)
		{
			@Override
			protected boolean hasFile()
			{
				return true;
			}
		};
		passwordView.addTextChangedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.import_keys_from_content_dialog_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private void restoreWalletFromEncrypted(final InputStream cipher, final String password)
	{
		try
		{
			final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(cipher, Charsets.UTF_8));
			final StringBuilder cipherText = new StringBuilder();
			Io.copy(cipherIn, cipherText, Constants.BACKUP_MAX_CHARS);
			cipherIn.close();

			final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
			final InputStream is = new ByteArrayInputStream(plainText);

			restoreWallet(WalletUtils.restoreWalletFromProtobufOrBase58(is));

			log.info("successfully restored encrypted wallet from external source");
		}
		catch (final IOException x)
		{
			final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
			dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
			dialog.setPositiveButton(R.string.button_dismiss, finishListener).setOnCancelListener(finishListener);
			dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					showDialog(DIALOG_RESTORE_WALLET);
				}
			});
			dialog.show();

			log.info("problem restoring wallet", x);
		}
	}

	private void restoreWallet(final Wallet wallet) throws IOException
	{
		application.replaceWallet(wallet);

		config.disarmBackupReminder();

		final DialogBuilder dialog = new DialogBuilder(this);
		final StringBuilder message = new StringBuilder();
		message.append(getString(R.string.restore_wallet_dialog_success));
		message.append("\n\n");
		message.append(getString(R.string.restore_wallet_dialog_success_replay));
		dialog.setMessage(message);
		dialog.setNeutralButton(R.string.button_ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int id)
			{
				getWalletApplication().resetBlockchain();
				finish();
			}
		});
		dialog.show();
	}

	private class FinishListener implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener
	{
		@Override
		public void onClick(final DialogInterface dialog, final int which)
		{
			finish();
		}

		@Override
		public void onCancel(final DialogInterface dialog)
		{
			finish();
		}
	}

	private final FinishListener finishListener = new FinishListener();
}
