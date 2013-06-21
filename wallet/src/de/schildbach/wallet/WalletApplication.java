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

package de.schildbach.wallet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.AutosaveEventListener;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.bitcoin.utils.Locks;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletApplication extends Application
{
	private File walletFile;
	private Wallet wallet;
	private Intent blockchainServiceIntent;
	private Intent blockchainServiceCancelCoinsReceivedIntent;
	private Intent blockchainServiceResetBlockchainIntent;
	private ActivityManager activityManager;

	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final String TAG = WalletApplication.class.getSimpleName();

	@Override
	public void onCreate()
	{
		final StrictMode.ThreadPolicy.Builder threadPolicy = new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites()
				.penaltyLog();
		final StrictMode.VmPolicy.Builder vmPolicy = new StrictMode.VmPolicy.Builder().detectAll().penaltyLog();
		if (Constants.TEST)
		{
			threadPolicy.penaltyDeath();
			vmPolicy.penaltyDeath();
		}
		StrictMode.setThreadPolicy(threadPolicy.build());
		StrictMode.setVmPolicy(vmPolicy.build());

		Locks.throwOnLockCycles();

		Log.d(TAG, ".onCreate()");

		super.onCreate();

		CrashReporter.init(getCacheDir());

		activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

		blockchainServiceIntent = new Intent(this, BlockchainServiceImpl.class);
		blockchainServiceCancelCoinsReceivedIntent = new Intent(BlockchainService.ACTION_CANCEL_COINS_RECEIVED, null, this,
				BlockchainServiceImpl.class);
		blockchainServiceResetBlockchainIntent = new Intent(BlockchainService.ACTION_RESET_BLOCKCHAIN, null, this, BlockchainServiceImpl.class);

		walletFile = getFileStreamPath(Constants.WALLET_FILENAME_PROTOBUF);

		migrateWalletToProtobuf();

		loadWalletFromProtobuf();

		backupKeys();

		for (final String filename : fileList())
			if (filename.endsWith(".tmp"))
				new File(getFilesDir(), filename).delete();

		wallet.autosaveToFile(walletFile, 1, TimeUnit.SECONDS, new WalletAutosaveEventListener());
	}

	private static final class WalletAutosaveEventListener implements AutosaveEventListener
	{
		public boolean caughtException(final Throwable throwable)
		{
			CrashReporter.saveBackgroundTrace(throwable);
			return true;
		}

		public void onBeforeAutoSave(final File file)
		{
		}

		public void onAfterAutoSave(final File file)
		{
			// make wallets world accessible in test mode
			if (Constants.TEST)
				WalletUtils.chmod(file, 0777);
		}
	}

	public Wallet getWallet()
	{
		return wallet;
	}

	private void migrateWalletToProtobuf()
	{
		final File oldWalletFile = getFileStreamPath(Constants.WALLET_FILENAME);

		if (oldWalletFile.exists())
		{
			Log.i(TAG, "found wallet to migrate");

			final long start = System.currentTimeMillis();

			// read
			wallet = restoreWalletFromBackup();

			try
			{
				// write
				protobufSerializeWallet(wallet);

				// delete
				oldWalletFile.delete();

				Log.i(TAG, "wallet migrated: '" + oldWalletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
			}
			catch (final IOException x)
			{
				throw new Error("cannot migrate wallet", x);
			}
		}
	}

	private void loadWalletFromProtobuf()
	{
		if (walletFile.exists())
		{
			final long start = System.currentTimeMillis();

			FileInputStream walletStream = null;

			try
			{
				walletStream = new FileInputStream(walletFile);

				wallet = new WalletProtobufSerializer().readWallet(walletStream);

				Log.i(TAG, "wallet loaded from: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
			}
			catch (final IOException x)
			{
				x.printStackTrace();

				Toast.makeText(WalletApplication.this, x.getClass().getName(), Toast.LENGTH_LONG).show();

				wallet = restoreWalletFromBackup();
			}
			catch (final RuntimeException x)
			{
				x.printStackTrace();

				Toast.makeText(WalletApplication.this, x.getClass().getName(), Toast.LENGTH_LONG).show();

				wallet = restoreWalletFromBackup();
			}
			finally
			{
				if (walletStream != null)
				{
					try
					{
						walletStream.close();
					}
					catch (final IOException x)
					{
						x.printStackTrace();
					}
				}
			}

			if (!wallet.isConsistent())
			{
				Toast.makeText(this, "inconsistent wallet: " + walletFile, Toast.LENGTH_LONG).show();

				wallet = restoreWalletFromBackup();
			}

			if (!wallet.getParams().equals(Constants.NETWORK_PARAMETERS))
				throw new Error("bad wallet network parameters: " + wallet.getParams().getId());
		}
		else
		{
			wallet = new Wallet(Constants.NETWORK_PARAMETERS);
			wallet.addKey(new ECKey());

			try
			{
				protobufSerializeWallet(wallet);
				Log.i(TAG, "wallet created: '" + walletFile + "'");
			}
			catch (final IOException x2)
			{
				throw new Error("wallet cannot be created", x2);
			}
		}

		// this check is needed so encrypted wallets won't get their private keys removed accidently
		for (final ECKey key : wallet.getKeys())
			if (key.getPrivKeyBytes() == null)
				throw new Error("found read-only key, but wallet is likely an encrypted wallet from the future");
	}

	private Wallet restoreWalletFromBackup()
	{
		try
		{
			final Wallet wallet = readKeys(openFileInput(Constants.WALLET_KEY_BACKUP_BASE58));

			resetBlockchain();

			Toast.makeText(this, R.string.toast_wallet_reset, Toast.LENGTH_LONG).show();

			Log.i(TAG, "wallet restored from backup: '" + Constants.WALLET_KEY_BACKUP_BASE58 + "'");

			return wallet;
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	private static Wallet readKeys(final InputStream is) throws IOException
	{
		final BufferedReader in = new BufferedReader(new InputStreamReader(is, UTF_8));
		final List<ECKey> keys = WalletUtils.readKeys(in);
		in.close();

		final Wallet wallet = new Wallet(Constants.NETWORK_PARAMETERS);
		for (final ECKey key : keys)
			wallet.addKey(key);

		return wallet;
	}

	public void addNewKeyToWallet()
	{
		wallet.addKey(new ECKey());

		backupKeys();
	}

	public void saveWallet()
	{
		try
		{
			protobufSerializeWallet(wallet);
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	private void protobufSerializeWallet(final Wallet wallet) throws IOException
	{
		final long start = System.currentTimeMillis();

		wallet.saveToFile(walletFile);

		// make wallets world accessible in test mode
		if (Constants.TEST)
			WalletUtils.chmod(walletFile, 0777);

		Log.d(TAG, "wallet saved to: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
	}

	private void backupKeys()
	{
		try
		{
			writeKeys(openFileOutput(Constants.WALLET_KEY_BACKUP_BASE58, Context.MODE_PRIVATE));
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}

		try
		{
			final String filename = String.format(Locale.US, "%s.%02d", Constants.WALLET_KEY_BACKUP_BASE58,
					(System.currentTimeMillis() / DateUtils.DAY_IN_MILLIS) % 100l);
			writeKeys(openFileOutput(filename, Context.MODE_PRIVATE));
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}
	}

	private void writeKeys(final OutputStream os) throws IOException
	{
		final Writer out = new OutputStreamWriter(os, UTF_8);
		WalletUtils.writeKeys(out, wallet.getKeys());
		out.close();
	}

	public Address determineSelectedAddress()
	{
		final List<ECKey> keys = wallet.getKeys();

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String selectedAddress = prefs.getString(Constants.PREFS_KEY_SELECTED_ADDRESS, null);

		if (selectedAddress != null)
		{
			for (final ECKey key : keys)
			{
				final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
				if (address.toString().equals(selectedAddress))
					return address;
			}
		}

		return keys.get(0).toAddress(Constants.NETWORK_PARAMETERS);
	}

	public void startBlockchainService(final boolean cancelCoinsReceived)
	{
		if (cancelCoinsReceived)
			startService(blockchainServiceCancelCoinsReceivedIntent);
		else
			startService(blockchainServiceIntent);
	}

	public void stopBlockchainService()
	{
		stopService(blockchainServiceIntent);
	}

	public void resetBlockchain()
	{
		// actually stops the service
		startService(blockchainServiceResetBlockchainIntent);
	}

	public final int applicationVersionCode()
	{
		try
		{
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		}
		catch (NameNotFoundException x)
		{
			return 0;
		}
	}

	public final String applicationVersionName()
	{
		try
		{
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException x)
		{
			return "unknown";
		}
	}

	public int maxConnectedPeers()
	{
		final int memoryClass = activityManager.getMemoryClass();
		if (memoryClass <= Constants.MEMORY_CLASS_LOWEND)
			return 4;
		else
			return 6;
	}
}
