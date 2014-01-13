/*
 * Copyright 2013-2014 the original author or authors.
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

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

import android.widget.Toast;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class ImportKeysActivity extends AbstractWalletActivity
{
	private static final int DIALOG_IMPORT_KEYS = 0;

	private Wallet wallet;
	private ContentResolver contentResolver;

	private Uri backupFileUri;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		wallet = getWalletApplication().getWallet();
		contentResolver = getContentResolver();

		backupFileUri = getIntent().getData();

		showDialog(DIALOG_IMPORT_KEYS);
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == DIALOG_IMPORT_KEYS)
			return createImportKeysDialog();
		else
			throw new IllegalArgumentException();
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog)
	{
		if (id == DIALOG_IMPORT_KEYS)
			prepareImportKeysDialog(dialog);
	}

	private Dialog createImportKeysDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.import_keys_from_content_dialog, null);
		final EditText passwordView = (EditText) view.findViewById(R.id.import_keys_from_content_dialog_password);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setIcon(R.drawable.app_icon);
		builder.setTitle(R.string.import_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap
                if(backupFileUri == null) {
                    Toast.makeText(ImportKeysActivity.this,
                            "File URI was null.  Please let developer know.", Toast.LENGTH_LONG)
                            .show();
                    return;
                }
				try
				{
					final InputStream is = contentResolver.openInputStream(backupFileUri);
					importPrivateKeys(is, password);
				}
				catch (final FileNotFoundException x)
				{
					// should not happen
					throw new RuntimeException(x);
				}
			}
		});
		builder.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
				finish();
			}
		});
		builder.setOnCancelListener(new OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
				finish();
			}
		});

		return builder.create();
	}

	private void prepareImportKeysDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

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

	private void importPrivateKeys(final InputStream is, final String password)
	{
		try
		{
			final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(is, Constants.UTF_8));
			final StringBuilder cipherText = new StringBuilder();
			while (true)
			{
				final String line = cipherIn.readLine();
				if (line == null)
					break;

				cipherText.append(line);
			}
			cipherIn.close();

			final String plainText = Crypto.decrypt(cipherText.toString(), password.toCharArray());
			final Reader plainReader = new StringReader(plainText);

			final BufferedReader keyReader = new BufferedReader(plainReader);
			final List<ECKey> importedKeys = WalletUtils.readKeys(keyReader);
			keyReader.close();

			final int numKeysToImport = importedKeys.size();
			final int numKeysImported = wallet.addKeys(importedKeys);

			final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setInverseBackgroundForced(true);
			final StringBuilder message = new StringBuilder();
			if (numKeysImported > 0)
				message.append(getString(R.string.import_keys_dialog_success_imported, numKeysImported));
			if (numKeysImported < numKeysToImport)
			{
				if (message.length() > 0)
					message.append('\n');
				message.append(getString(R.string.import_keys_dialog_success_existing, numKeysToImport - numKeysImported));
			}
			if (numKeysImported > 0)
			{
				if (message.length() > 0)
					message.append("\n\n");
				message.append(getString(R.string.import_keys_dialog_success_reset));
			}
			dialog.setMessage(message);
			if (numKeysImported > 0)
			{
				dialog.setPositiveButton(R.string.import_keys_dialog_button_reset_blockchain, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int id)
					{
						getWalletApplication().resetBlockchain();
						finish();
					}
				});
				dialog.setNegativeButton(R.string.button_dismiss, finishListener);
			}
			else
			{
				dialog.setNeutralButton(R.string.button_dismiss, finishListener);
			}
			dialog.setOnCancelListener(finishListener);
			dialog.show();

			log.info("imported " + numKeysImported + " of " + numKeysToImport + " private keys");
		}
		catch (final IOException x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.import_export_keys_dialog_failure_title)
					.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()))
					.setNeutralButton(R.string.button_dismiss, finishListener).setOnCancelListener(finishListener).show();

			log.info("problem reading private keys", x);
		}
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
