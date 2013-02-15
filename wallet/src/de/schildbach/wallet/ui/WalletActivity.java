/*
 * Copyright 2011-2013 the original author or authors.
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
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.EncryptionUtils;
import de.schildbach.wallet.util.ErrorReporter;
import de.schildbach.wallet.util.Iso8601Format;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractWalletActivity
{
	private static final int DIALOG_HELP = 0;
	public static final int DIALOG_SAFETY = 1;
	private static final int DIALOG_IMPORT_KEYS = 2;
	private static final int DIALOG_EXPORT_KEYS = 3;
	private static final int DIALOG_ALERT_OLD_SDK = 4;

	private SharedPreferences prefs;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		ErrorReporter.getInstance().check(this);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		setContentView(R.layout.wallet_content);

		checkAlerts();

		touchLastUsed();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		getWalletApplication().startBlockchainService(true);

		checkLowStorageAlert();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		getSupportMenuInflater().inflate(R.menu.wallet_options, menu);
		menu.findItem(R.id.wallet_options_donate).setVisible(!Constants.TEST);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		super.onPrepareOptionsMenu(menu);

		final Resources res = getResources();
		final String externalStorageState = Environment.getExternalStorageState();

		menu.findItem(R.id.wallet_options_exchange_rates).setVisible(res.getBoolean(R.bool.show_exchange_rates_option));
		menu.findItem(R.id.wallet_options_import_keys).setEnabled(
				Environment.MEDIA_MOUNTED.equals(externalStorageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(externalStorageState));
		menu.findItem(R.id.wallet_options_export_keys).setEnabled(Environment.MEDIA_MOUNTED.equals(externalStorageState));

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_options_request:
				startActivity(new Intent(this, RequestCoinsActivity.class));
				return true;

			case R.id.wallet_options_send:
				startActivity(new Intent(this, SendCoinsActivity.class));
				return true;

			case R.id.wallet_options_address_book:
				AddressBookActivity.start(this, true);
				return true;

			case R.id.wallet_options_exchange_rates:
				startActivity(new Intent(this, ExchangeRatesActivity.class));
				return true;

			case R.id.wallet_options_peer_monitor:
				startActivity(new Intent(this, PeerMonitorActivity.class));
				return true;

			case R.id.wallet_options_import_keys:
				showDialog(DIALOG_IMPORT_KEYS);
				return true;

			case R.id.wallet_options_export_keys:
				showDialog(DIALOG_EXPORT_KEYS);
				return true;

			case R.id.wallet_options_disconnect:
				handleDisconnect();
				return true;

			case R.id.wallet_options_preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				return true;

			case R.id.wallet_options_about:
				startActivity(new Intent(this, AboutActivity.class));
				return true;

			case R.id.wallet_options_safety:
				showDialog(DIALOG_SAFETY);
				return true;

			case R.id.wallet_options_donate:
				final Intent intent = new Intent(this, SendCoinsActivity.class);
				intent.putExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS, Constants.DONATION_ADDRESS);
				intent.putExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS_LABEL, getString(R.string.wallet_donate_address_label));
				startActivity(intent);
				return true;

			case R.id.wallet_options_help:
				showDialog(DIALOG_HELP);
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleDisconnect()
	{
		getWalletApplication().stopBlockchainService();
		finish();
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == DIALOG_IMPORT_KEYS)
			return createImportKeysDialog();
		else if (id == DIALOG_EXPORT_KEYS)
			return createExportKeysDialog();
		else if (id == DIALOG_HELP)
			return createWebViewDialog("file:///android_asset/help" + languagePrefix() + ".html");
		else if (id == DIALOG_SAFETY)
			return createWebViewDialog("file:///android_asset/safety" + languagePrefix() + ".html");
		else if (id == DIALOG_ALERT_OLD_SDK)
			return createAlertOldSdkDialog();
		else
			throw new IllegalArgumentException();
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog)
	{
		if (id == DIALOG_IMPORT_KEYS)
			prepareImportKeysDialog(dialog);
		else if (id == DIALOG_EXPORT_KEYS)
			prepareExportKeysDialog(dialog);
	}

	private Dialog createImportKeysDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.wallet_import_keys_dialog, null);
		final TextView messageView = (TextView) view.findViewById(R.id.wallet_import_keys_message);
		messageView.setText(getString(R.string.wallet_import_keys_dialog_message, Constants.EXTERNAL_WALLET_BACKUP_DIR));
		final Spinner fileView = (Spinner) view.findViewById(R.id.wallet_import_keys_file);
		final EditText passwordView = (EditText) view.findViewById(R.id.wallet_import_keys_password);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.wallet_import_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.wallet_import_keys_dialog_button_import, new OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				final File file = (File) fileView.getSelectedItem();
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				importPrivateKeys(file, password);
			}
		});
		builder.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		builder.setOnCancelListener(new OnCancelListener()
		{
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});

		return builder.create();
	}

	private void prepareImportKeysDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final File[] files = Constants.EXTERNAL_WALLET_BACKUP_DIR.listFiles(new FileFilter()
		{
			public boolean accept(final File file)
			{
				return WalletUtils.KEYS_FILE_FILTER.accept(file) || EncryptionUtils.OPENSSL_FILE_FILTER.accept(file);
			}
		});
		Arrays.sort(files);
		final FileAdapter adapter = new FileAdapter(this, files);

		final Spinner fileView = (Spinner) alertDialog.findViewById(R.id.wallet_import_keys_file);
		fileView.setAdapter(adapter);
		fileView.setEnabled(!adapter.isEmpty());

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.wallet_import_keys_password);
		passwordView.setText(null);

		final DialogButtonEnablerListener dialogButtonEnabler = new DialogButtonEnablerListener(fileView, passwordView, alertDialog);
		passwordView.addTextChangedListener(dialogButtonEnabler);
		fileView.setOnItemSelectedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.wallet_import_keys_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private Dialog createExportKeysDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.wallet_export_keys_dialog, null);
		final EditText passwordView = (EditText) view.findViewById(R.id.wallet_export_keys_password);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.wallet_export_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.wallet_export_keys_dialog_button_export, new OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				exportPrivateKeys(password);
			}
		});
		builder.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		builder.setOnCancelListener(new OnCancelListener()
		{
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});

		final AlertDialog dialog = builder.create();

		return dialog;
	}

	private void prepareExportKeysDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.wallet_export_keys_password);
		passwordView.setText(null);

		final DialogButtonEnablerListener dialogButtonEnabler = new DialogButtonEnablerListener(null, passwordView, alertDialog);
		passwordView.addTextChangedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.wallet_export_keys_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private Dialog createWebViewDialog(final String url)
	{
		final WebView webView = new WebView(this);
		webView.loadUrl(url);

		final Dialog dialog = new Dialog(WalletActivity.this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	private Dialog createAlertOldSdkDialog()
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_old_sdk_dialog_title);
		builder.setMessage(R.string.wallet_old_sdk_dialog_message);
		builder.setPositiveButton(R.string.button_ok, null);
		builder.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int id)
			{
				prefs.edit().putBoolean(Constants.PREFS_KEY_ALERT_OLD_SDK_DISMISSED, true).commit();
				finish();
			}
		});
		return builder.create();
	}

	private static final class DialogButtonEnablerListener implements TextWatcher, OnItemSelectedListener
	{
		private final Spinner fileView;
		private final TextView passwordView;
		private final AlertDialog dialog;

		public DialogButtonEnablerListener(final Spinner fileView, final TextView passwordView, final AlertDialog dialog)
		{
			this.fileView = fileView;
			this.passwordView = passwordView;
			this.dialog = dialog;

			handle();
		}

		public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id)
		{
			handle();
		}

		public void onNothingSelected(final AdapterView<?> parent)
		{
			handle();
		}

		public void afterTextChanged(final Editable s)
		{
			handle();
		}

		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
		}

		public void handle()
		{
			final boolean hasFile;
			final boolean needsPassword;
			if (fileView != null)
			{
				final File selectedFile = (File) fileView.getSelectedItem();
				hasFile = selectedFile != null;
				needsPassword = hasFile ? EncryptionUtils.OPENSSL_FILE_FILTER.accept(selectedFile) : true;
			}
			else
			{
				hasFile = true;
				needsPassword = true;
			}

			final boolean hasPassword = passwordView.getText().toString().trim().length() > 0;

			final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
			button.setEnabled(hasFile && (!needsPassword || hasPassword));
		}
	}

	private void checkLowStorageAlert()
	{
		final Intent stickyIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
		if (stickyIntent != null)
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setTitle(R.string.wallet_low_storage_dialog_title);
			builder.setMessage(R.string.wallet_low_storage_dialog_msg);
			builder.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
					finish();
				}
			});
			builder.setNegativeButton(R.string.button_dismiss, null);
			builder.show();
		}
	}

	private void checkAlerts()
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					final int versionCode = getWalletApplication().applicationVersionCode();
					final URLConnection connection = new URL(Constants.VERSION_URL + "?current=" + versionCode).openConnection();
					connection.connect();
					final long serverTime = connection.getHeaderFieldDate("Date", 0);
					final InputStream is = connection.getInputStream();
					final BufferedReader reader = new BufferedReader(new InputStreamReader(is), 64);
					final int serverVersionCode = Integer.parseInt(reader.readLine().trim().split("\\s+")[0]);
					reader.close();

					if (serverTime > 0)
					{
						final long diffMinutes = Math.abs((System.currentTimeMillis() - serverTime) / 1000 / 60);

						if (diffMinutes >= 60)
						{
							runOnUiThread(new Runnable()
							{
								public void run()
								{
									if (!isFinishing())
										timeskewAlert(diffMinutes);
								}
							});

							return;
						}
					}

					if (serverVersionCode > versionCode)
					{
						runOnUiThread(new Runnable()
						{
							public void run()
							{
								if (!isFinishing())
									versionAlert(serverVersionCode);
							}
						});

						return;
					}
				}
				catch (final Exception x)
				{
					x.printStackTrace();
				}

				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1
						&& !prefs.getBoolean(Constants.PREFS_KEY_ALERT_OLD_SDK_DISMISSED, false))
				{
					runOnUiThread(new Runnable()
					{
						public void run()
						{
							showDialog(DIALOG_ALERT_OLD_SDK);
						}
					});

					return;
				}
			}
		}.start();
	}

	private void timeskewAlert(final long diffMinutes)
	{
		final PackageManager pm = getPackageManager();
		final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_timeskew_dialog_title);
		builder.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));

		if (pm.resolveActivity(settingsIntent, 0) != null)
		{
			builder.setPositiveButton(R.string.wallet_timeskew_dialog_button_settings, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(settingsIntent);
					finish();
				}
			});
		}

		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	private void versionAlert(final int serverVersionCode)
	{
		final PackageManager pm = getPackageManager();
		final Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName())));
		final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_version_dialog_title);
		builder.setMessage(getString(R.string.wallet_version_dialog_msg));

		if (pm.resolveActivity(marketIntent, 0) != null)
		{
			builder.setPositiveButton(R.string.wallet_version_dialog_button_market, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(marketIntent);
					finish();
				}
			});
		}

		if (pm.resolveActivity(binaryIntent, 0) != null)
		{
			builder.setNeutralButton(R.string.wallet_version_dialog_button_binary, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(binaryIntent);
					finish();
				}
			});
		}

		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	private void importPrivateKeys(final File file, final String password)
	{
		try
		{
			final Reader plainReader;
			if (EncryptionUtils.OPENSSL_FILE_FILTER.accept(file))
			{
				final BufferedReader cipherIn = new BufferedReader(new FileReader(file));
				final StringBuilder cipherText = new StringBuilder();
				while (true)
				{
					final String line = cipherIn.readLine();
					if (line == null)
						break;

					cipherText.append(line);
				}
				cipherIn.close();

				final String plainText = EncryptionUtils.decrypt(cipherText.toString(), password.toCharArray());
				plainReader = new StringReader(plainText);
			}
			else if (WalletUtils.KEYS_FILE_FILTER.accept(file))
			{
				plainReader = new FileReader(file);
			}
			else
			{
				throw new IllegalStateException(file.getAbsolutePath());
			}

			final BufferedReader keyReader = new BufferedReader(plainReader);
			final List<ECKey> importedKeys = WalletUtils.readKeys(keyReader);
			keyReader.close();

			final Wallet wallet = getWalletApplication().getWallet();
			int importCount = 0;
			k: for (final ECKey importedKey : importedKeys)
			{
				for (final ECKey key : wallet.getKeys())
					if (importedKey.equals(key))
						continue k;

				wallet.addKey(importedKey);
				importCount++;
			}

			final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setInverseBackgroundForced(true);
			dialog.setMessage(getString(R.string.wallet_import_keys_dialog_success, importCount));
			dialog.setPositiveButton(R.string.wallet_import_keys_dialog_button_reset_blockchain, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int id)
				{
					getWalletApplication().resetBlockchain();
					finish();
				}
			});
			dialog.setNegativeButton(R.string.button_dismiss, null);
			dialog.show();
		}
		catch (final IOException x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.wallet_import_export_keys_dialog_failure_title)
					.setMessage(getString(R.string.wallet_import_keys_dialog_failure, x.getMessage()))
					.setNeutralButton(R.string.button_dismiss, null).show();

			x.printStackTrace();
		}
	}

	private void exportPrivateKeys(final String password)
	{
		try
		{
			Constants.EXTERNAL_WALLET_BACKUP_DIR.mkdirs();
			final File file = new File(Constants.EXTERNAL_WALLET_BACKUP_DIR, Constants.EXTERNAL_WALLET_KEY_BACKUP + "-"
					+ Iso8601Format.newDateFormat().format(new Date()));

			final Wallet wallet = getWalletApplication().getWallet();
			final ArrayList<ECKey> keys = wallet.keychain;

			final StringWriter plainOut = new StringWriter();
			WalletUtils.writeKeys(plainOut, keys);
			plainOut.close();
			final String plainText = plainOut.toString();

			final String cipherText = EncryptionUtils.encrypt(plainText, password.toCharArray());

			final Writer cipherOut = new FileWriter(file);
			cipherOut.write(cipherText);
			cipherOut.close();

			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setMessage(getString(R.string.wallet_export_keys_dialog_success, file))
					.setNeutralButton(R.string.button_dismiss, null).show();
		}
		catch (final IOException x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.wallet_import_export_keys_dialog_failure_title)
					.setMessage(getString(R.string.wallet_export_keys_dialog_failure, x.getMessage()))
					.setNeutralButton(R.string.button_dismiss, null).show();

			x.printStackTrace();
		}
	}
}
