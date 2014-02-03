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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.annotation.Nonnull;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.IntentIntegrator;
import de.schildbach.wallet.util.IntentResult;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.HttpGetThread;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class WalletActivity extends AbstractOnDemandServiceActivity
{
	private static final int DIALOG_IMPORT_KEYS = 0;
	private static final int DIALOG_EXPORT_KEYS = 1;
	private static final int DIALOG_ALERT_OLD_SDK = 2;

	private WalletApplication application;
	private Wallet wallet;
	private SharedPreferences prefs;

	private static final int REQUEST_CODE_SCAN = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = getWalletApplication();
		wallet = application.getWallet();
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		setContentView(R.layout.wallet_content);

		if (savedInstanceState == null)
			checkAlerts();

		touchLastUsed();

		handleIntent(getIntent());
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		getWalletApplication().startBlockchainService(true);

		checkLowStorageAlert();
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	private void handleIntent(@Nonnull final Intent intent)
	{
		final String action = intent.getAction();

		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
		{
			final String inputType = intent.getType();
			final NdefMessage ndefMessage = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
			final byte[] input = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

			new BinaryInputParser(inputType, input)
			{
				@Override
				protected void bitcoinRequest(@Nonnull final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
				{
					cannotClassify(inputType);
				}

				@Override
				protected void directTransaction(@Nonnull final Transaction transaction)
				{
					processDirectTransaction(transaction);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(WalletActivity.this, null, 0, messageResId, messageArgs);
				}

                @Override
                protected void handlePrivateKey(@Nonnull ECKey key) {
                    final Address address = new Address(Constants.NETWORK_PARAMETERS, key.getPubKeyHash());
                    bitcoinRequest(address, null, null, null);
                }
			}.parse();
		}
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
        final String input;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        /* Check if user wants to use internal scanner */
        if(prefs.getString(Constants.PREFS_KEY_QR_SCANNER, "").equals("internal"))
        {
            input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
        }
        else
        {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult != null)
                input = scanResult.getContents();
            else
                input = null;
        }

        new StringInputParser(input)
        {
            @Override
            protected void bitcoinRequest(@Nonnull final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
            {
                SendCoinsActivity.start(WalletActivity.this, address.toString(), addressLabel, amount, bluetoothMac);
            }

            @Override
            protected void directTransaction(@Nonnull final Transaction tx)
            {
                processDirectTransaction(tx);
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs)
            {
                dialog(WalletActivity.this, null, R.string.button_scan, messageResId, messageArgs);
            }

            @Override
            protected void handlePrivateKey(@Nonnull final ECKey key) {
                // We actually want to add this key to the wallet here.
                // Set the creation time to now
                key.setCreationTimeSeconds(System.currentTimeMillis() / 1000);
                final Address address = new Address(Constants.NETWORK_PARAMETERS, key.getPubKeyHash());
                new AlertDialog.Builder(WalletActivity.this)
                        .setTitle("Import Private Key")
                        .setMessage("Would you like to add " +
                                address.toString() +
                                " to your wallet?  If there are currently funds on it, they will only be accessible " +
                                "by resetting the blockchain, which can take a very long time.  If it is a new " +
                                "empty address, everything should work fine.")
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                    }
                                })
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        wallet.addKey(key);
                                    }
                                })
                        .show();
            }
        }.parse();
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
		menu.findItem(R.id.wallet_options_disconnect).setVisible(true);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_options_request:
				handleRequestCoins();
				return true;

			case R.id.wallet_options_send:
				handleSendCoins();
				return true;

			case R.id.wallet_options_scan:
				handleScan();
				return true;

			case R.id.wallet_options_address_book:
				AddressBookActivity.start(this, true);
				return true;

			case R.id.wallet_options_exchange_rates:
				startActivity(new Intent(this, ExchangeRatesActivity.class));
				return true;

			case R.id.wallet_options_network_monitor:
				startActivity(new Intent(this, NetworkMonitorActivity.class));
				return true;

			case R.id.wallet_options_import_keys:
				showDialog(DIALOG_IMPORT_KEYS);
				return true;

			case R.id.wallet_options_export_keys:
				handleExportKeys();
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
				HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_safety);
				return true;

			case R.id.wallet_options_donate:
				SendCoinsActivity.start(this, Constants.DONATION_ADDRESS, getString(R.string.wallet_donate_address_label), null, null);
				return true;

			case R.id.wallet_options_help:
				HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_wallet);
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void handleRequestCoins()
	{
		startActivity(new Intent(this, RequestCoinsActivity.class));
	}

	public void handleSendCoins()
	{
		startActivity(new Intent(this, SendCoinsActivity.class));
	}

	public void handleScan()
    {
        if(prefs.getString(Constants.PREFS_KEY_QR_SCANNER, "").equals("internal")) {
            startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
        } else {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.initiateScan();
        }
    }

	public void handleExportKeys()
	{
		showDialog(DIALOG_EXPORT_KEYS);

		prefs.edit().putBoolean(Constants.PREFS_KEY_REMIND_BACKUP, false).commit();
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
		final View view = getLayoutInflater().inflate(R.layout.import_keys_from_storage_dialog, null);
		final Spinner fileView = (Spinner) view.findViewById(R.id.import_keys_from_storage_file);
		final EditText passwordView = (EditText) view.findViewById(R.id.import_keys_from_storage_password);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.import_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener()
		{
			@Override
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
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		builder.setOnCancelListener(new OnCancelListener()
		{
			@Override
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

		final List<File> files = new LinkedList<File>();

		// external storage
		if (Constants.EXTERNAL_WALLET_BACKUP_DIR.exists() && Constants.EXTERNAL_WALLET_BACKUP_DIR.isDirectory())
			for (final File file : Constants.EXTERNAL_WALLET_BACKUP_DIR.listFiles())
				if (WalletUtils.KEYS_FILE_FILTER.accept(file) || Crypto.OPENSSL_FILE_FILTER.accept(file))
					files.add(file);

		// internal storage
		for (final String filename : fileList())
			if (filename.startsWith(Constants.WALLET_KEY_BACKUP_BASE58 + '.'))
				files.add(new File(getFilesDir(), filename));

		// sort
		Collections.sort(files, new Comparator<File>()
		{
			@Override
			public int compare(final File lhs, final File rhs)
			{
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});

		final FileAdapter adapter = new FileAdapter(this, files)
		{
			@Override
			public View getDropDownView(final int position, View row, final ViewGroup parent)
			{
				final File file = getItem(position);
				final boolean isExternal = Constants.EXTERNAL_WALLET_BACKUP_DIR.equals(file.getParentFile());
				final boolean isEncrypted = Crypto.OPENSSL_FILE_FILTER.accept(file);

				if (row == null)
					row = inflater.inflate(R.layout.wallet_import_keys_file_row, null);

				final TextView filenameView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_filename);
				filenameView.setText(file.getName());

				final TextView securityView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_security);
				final String encryptedStr = context.getString(isEncrypted ? R.string.import_keys_dialog_file_security_encrypted
						: R.string.import_keys_dialog_file_security_unencrypted);
				final String storageStr = context.getString(isExternal ? R.string.import_keys_dialog_file_security_external
						: R.string.import_keys_dialog_file_security_internal);
				securityView.setText(encryptedStr + ", " + storageStr);

				final TextView createdView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_created);
				createdView
						.setText(context.getString(isExternal ? R.string.import_keys_dialog_file_created_manual
								: R.string.import_keys_dialog_file_created_automatic, DateUtils.getRelativeTimeSpanString(context,
								file.lastModified(), true)));

				return row;
			}
		};

		final Spinner fileView = (Spinner) alertDialog.findViewById(R.id.import_keys_from_storage_file);
		fileView.setAdapter(adapter);
		fileView.setEnabled(!adapter.isEmpty());

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.import_keys_from_storage_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog)
		{
			@Override
			protected boolean hasFile()
			{
				return fileView.getSelectedItem() != null;
			}

			@Override
			protected boolean needsPassword()
			{
				final File selectedFile = (File) fileView.getSelectedItem();
				return selectedFile != null ? Crypto.OPENSSL_FILE_FILTER.accept(selectedFile) : false;
			}
		};
		passwordView.addTextChangedListener(dialogButtonEnabler);
		fileView.setOnItemSelectedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.import_keys_from_storage_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private Dialog createExportKeysDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.export_keys_dialog, null);
		final EditText passwordView = (EditText) view.findViewById(R.id.export_keys_dialog_password);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.export_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.export_keys_dialog_button_export, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				exportPrivateKeys(password);
			}
		});
		builder.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		builder.setOnCancelListener(new OnCancelListener()
		{
			@Override
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

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.export_keys_dialog_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog);
		passwordView.addTextChangedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.export_keys_dialog_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
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
			@Override
			public void onClick(final DialogInterface dialog, final int id)
			{
				prefs.edit().putBoolean(Constants.PREFS_KEY_ALERT_OLD_SDK_DISMISSED, true).commit();
				finish();
			}
		});
		return builder.create();
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
				@Override
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
		final PackageInfo packageInfo = getWalletApplication().packageInfo();
		final int versionNameSplit = packageInfo.versionName.indexOf('-');
		final String base = Constants.VERSION_URL + (versionNameSplit >= 0 ? packageInfo.versionName.substring(versionNameSplit) : "");
		final String url = base + "?current=" + packageInfo.versionCode;

		new HttpGetThread(getAssets(), url)
		{
			@Override
			protected void handleLine(final String line, final long serverTime)
			{
				final int serverVersionCode = Integer.parseInt(line.split("\\s+")[0]);

				log.info("according to \"" + url + "\", strongly recommended minimum app version is " + serverVersionCode);

				if (serverTime > 0)
				{
					final long diffMinutes = Math.abs((System.currentTimeMillis() - serverTime) / DateUtils.MINUTE_IN_MILLIS);

					if (diffMinutes >= 60)
					{
						log.info("according to \"" + url + "\", system clock is off by " + diffMinutes + " minutes");

						runOnUiThread(new Runnable()
						{
							@Override
							public void run()
							{
								if (!isFinishing())
									timeskewAlert(diffMinutes);
							}
						});

						return;
					}
				}

				if (serverVersionCode > packageInfo.versionCode)
				{
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							if (!isFinishing())
								versionAlert(serverVersionCode);
						}
					});

					return;
				}
			}

			@Override
			protected void handleException(final Exception x)
			{
				if (x instanceof UnknownHostException || x instanceof SocketException || x instanceof SocketTimeoutException)
				{
					// swallow
					log.debug("problem reading", x);
				}
				else
				{
					CrashReporter.saveBackgroundTrace(new RuntimeException(url, x), packageInfo);
				}
			}
		}.start();

		if (CrashReporter.hasSavedCrashTrace())
		{
			final StringBuilder stackTrace = new StringBuilder();
			final StringBuilder applicationLog = new StringBuilder();

			try
			{
				CrashReporter.appendSavedCrashTrace(stackTrace);
			}
			catch (final IOException x)
			{
				log.info("problem appending crash info", x);
			}

			final ReportIssueDialogBuilder dialog = new ReportIssueDialogBuilder(this, R.string.report_issue_dialog_title_crash,
					R.string.report_issue_dialog_message_crash)
			{
				@Override
				protected CharSequence subject()
				{
					return Constants.REPORT_SUBJECT_CRASH + " " + packageInfo.versionName;
				}

				@Override
				protected CharSequence collectApplicationInfo() throws IOException
				{
					final StringBuilder applicationInfo = new StringBuilder();
					CrashReporter.appendApplicationInfo(applicationInfo, application);
					return applicationInfo;
				}

				@Override
				protected CharSequence collectStackTrace() throws IOException
				{
					if (stackTrace.length() > 0)
						return stackTrace;
					else
						return null;
				}

				@Override
				protected CharSequence collectDeviceInfo() throws IOException
				{
					final StringBuilder deviceInfo = new StringBuilder();
					CrashReporter.appendDeviceInfo(deviceInfo, WalletActivity.this);
					return deviceInfo;
				}

				@Override
				protected CharSequence collectWalletDump()
				{
					return wallet.toString(false, true, true, null);
				}
			};

			dialog.show();
		}
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
				@Override
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

		final Intent marketIntent;
        if(GenericUtils.isBlackberry())
            marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.BB_MARKET_APP_URL, getPackageName())));
        else
            marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.ANDROID_MARKET_APP_URL, getPackageName())));
		final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_version_dialog_title);
		builder.setMessage(getString(R.string.wallet_version_dialog_msg));

		if (pm.resolveActivity(marketIntent, 0) != null)
		{
			builder.setPositiveButton(R.string.wallet_version_dialog_button_market, new DialogInterface.OnClickListener()
			{
				@Override
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
				@Override
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

	private void importPrivateKeys(@Nonnull final File file, @Nonnull final String password)
	{
		try
		{
			final Reader plainReader;
			if (Crypto.OPENSSL_FILE_FILTER.accept(file))
			{
				final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), Constants.UTF_8));
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
				plainReader = new StringReader(plainText);
			}
			else if (WalletUtils.KEYS_FILE_FILTER.accept(file))
			{
				plainReader = new InputStreamReader(new FileInputStream(file), Constants.UTF_8);
			}
			else
			{
				throw new IllegalStateException(file.getAbsolutePath());
			}

			final BufferedReader keyReader = new BufferedReader(plainReader);
			final List<ECKey> importedKeys = WalletUtils.readKeys(keyReader);
            final BufferedReader noteReader = new BufferedReader(plainReader);
            final Map<String, String> notes = WalletUtils.readNotes(noteReader);

			final int numKeysToImport = importedKeys.size();
			final int numKeysImported = wallet.addKeys(importedKeys);

            // Write each transaction to shared preferences
            Iterator it = notes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                prefs.edit().putString((String)pair.getKey(), (String)pair.getValue()).commit();
                it.remove(); // avoids a ConcurrentModificationException
            }

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
				dialog.setNegativeButton(R.string.button_dismiss, null);
			}
			else
			{
				dialog.setNeutralButton(R.string.button_dismiss, null);
			}
			dialog.show();

			log.info("imported " + numKeysImported + " of " + numKeysToImport + " private keys");
		}
		catch (final IOException x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.import_export_keys_dialog_failure_title)
					.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage())).setNeutralButton(R.string.button_dismiss, null)
					.show();

			log.info("problem reading private keys", x);
		}
	}

	private void exportPrivateKeys(@Nonnull final String password)
	{
		try
		{
			if(!Constants.EXTERNAL_WALLET_BACKUP_DIR.mkdirs()) {
                Log.d("WalletActivity", "mkdirs() returned false.  This isn't critical, but might be interesting");
            }
			final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
			dateFormat.setTimeZone(TimeZone.getDefault());
			final File file = new File(Constants.EXTERNAL_WALLET_BACKUP_DIR, Constants.EXTERNAL_WALLET_KEY_BACKUP + "-"
					+ dateFormat.format(new Date()));

			final List<ECKey> keys = new LinkedList<ECKey>();
			for (final ECKey key : wallet.getKeys())
				if (!wallet.isKeyRotating(key))
					keys.add(key);

			final StringWriter plainOut = new StringWriter();
			WalletUtils.writeKeys(plainOut, keys);
			plainOut.close();
			final String plainText = plainOut.toString();

			final String cipherText = Crypto.encrypt(plainText, password.toCharArray());

			final Writer cipherOut = new OutputStreamWriter(new FileOutputStream(file), Constants.UTF_8);
			cipherOut.write(cipherText);
			cipherOut.close();

			final AlertDialog.Builder dialog = new AlertDialog.Builder(this).setInverseBackgroundForced(true).setMessage(
					getString(R.string.export_keys_dialog_success, file));
			dialog.setPositiveButton(R.string.export_keys_dialog_button_archive, new OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int which)
				{
					mailPrivateKeys(file);
				}
			});
			dialog.setNegativeButton(R.string.button_dismiss, null);
			dialog.show();

			log.info("exported " + keys.size() + " private keys to " + file);
		}
		catch (final IOException x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.import_export_keys_dialog_failure_title)
					.setMessage(getString(R.string.export_keys_dialog_failure, x.getMessage())).setNeutralButton(R.string.button_dismiss, null)
					.show();

			log.error("problem writing private keys", x);
		}
	}

	private void mailPrivateKeys(@Nonnull final File file)
	{
		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_keys_dialog_mail_subject));
        if(GenericUtils.isBlackberry()) {
            intent.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.export_keys_dialog_mail_text) + "\n\n" + String.format(Constants.BB_WEBMARKET_APP_URL, getPackageName()) + "\n\n"
                        + Constants.SOURCE_URL + '\n');
        } else {
		    intent.putExtra(Intent.EXTRA_TEXT,
				getString(R.string.export_keys_dialog_mail_text) + "\n\n" + String.format(Constants.ANDROID_WEBMARKET_APP_URL, getPackageName()) + "\n\n"
						+ Constants.SOURCE_URL + '\n');
        }
		intent.setType("x-litecoin/private-keys");
		intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
		startActivity(Intent.createChooser(intent, getString(R.string.export_keys_dialog_mail_intent_chooser)));

		log.info("invoked archive private keys chooser");
	}
}
