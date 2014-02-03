/*
 * Copyright 2011-2014 the original author or authors.
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.bitcoinj.wallet.Protos;
import org.litecoin.LitecoinWallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.widget.Toast;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.WalletFiles;
import com.google.bitcoin.core.NetworkParameters;

import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Io;
import de.schildbach.wallet.util.LinuxSecureRandom;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public class WalletApplication extends Application
{
	private SharedPreferences prefs;
	private ActivityManager activityManager;

	private Intent blockchainServiceIntent;
	private Intent blockchainServiceCancelCoinsReceivedIntent;
	private Intent blockchainServiceResetBlockchainIntent;

	private File walletFile;
	private Wallet wallet;
	private PackageInfo packageInfo;

	private static final int KEY_ROTATION_VERSION_CODE = 135;

	private static final Logger log = LoggerFactory.getLogger(WalletApplication.class);

	@Override
	public void onCreate()
	{
		new LinuxSecureRandom(); // init proper random number generator

		initLogging();

		StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites().penaltyLog().build());

		Threading.throwOnLockCycles();

		log.info("configuration: " + (Constants.TEST ? "test" : "prod") + ", " + Constants.NETWORK_PARAMETERS.getId());

		super.onCreate();

		try
		{
			packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
		}
		catch (final NameNotFoundException x)
		{
			throw new RuntimeException(x);
		}

		CrashReporter.init(getCacheDir());

		Threading.uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler()
		{
			@Override
			public void uncaughtException(final Thread thread, final Throwable throwable)
			{
				log.info("bitcoinj uncaught exception", throwable);
				CrashReporter.saveBackgroundTrace(throwable, packageInfo);
			}
		};

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

		blockchainServiceIntent = new Intent(this, BlockchainServiceImpl.class);
		blockchainServiceCancelCoinsReceivedIntent = new Intent(BlockchainService.ACTION_CANCEL_COINS_RECEIVED, null, this,
				BlockchainServiceImpl.class);
		blockchainServiceResetBlockchainIntent = new Intent(BlockchainService.ACTION_RESET_BLOCKCHAIN, null, this, BlockchainServiceImpl.class);

		walletFile = getFileStreamPath(Constants.WALLET_FILENAME_PROTOBUF);

		migrateWalletToProtobuf();

		loadWalletFromProtobuf();
		wallet.autosaveToFile(walletFile, 1, TimeUnit.SECONDS, new WalletAutosaveEventListener());

		final int lastVersionCode = prefs.getInt(Constants.PREFS_KEY_LAST_VERSION, 0);
		prefs.edit().putInt(Constants.PREFS_KEY_LAST_VERSION, packageInfo.versionCode).commit();

		if (packageInfo.versionCode > lastVersionCode)
			log.info("detected app upgrade: " + lastVersionCode + " -> " + packageInfo.versionCode);
		else if (packageInfo.versionCode < lastVersionCode)
			log.warn("detected app downgrade: " + lastVersionCode + " -> " + packageInfo.versionCode);

		if (lastVersionCode > 0 && lastVersionCode < KEY_ROTATION_VERSION_CODE && packageInfo.versionCode >= KEY_ROTATION_VERSION_CODE)
		{
			log.info("detected version jump crossing key rotation");
			wallet.setKeyRotationTime(System.currentTimeMillis() / 1000);
		}

		ensureKey();
	}

	private void initLogging()
	{
		final File logDir = getDir("log", Constants.TEST ? Context.MODE_WORLD_READABLE : MODE_PRIVATE);
		final File logFile = new File(logDir, "wallet.log");

		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

		final PatternLayoutEncoder filePattern = new PatternLayoutEncoder();
		filePattern.setContext(context);
		filePattern.setPattern("%d{HH:mm:ss.SSS} [%thread] %logger{0} - %msg%n");
		filePattern.start();

		final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<ILoggingEvent>();
		fileAppender.setContext(context);
		fileAppender.setFile(logFile.getAbsolutePath());

		final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
		rollingPolicy.setContext(context);
		rollingPolicy.setParent(fileAppender);
		rollingPolicy.setFileNamePattern(logDir.getAbsolutePath() + "/wallet.%d.log.gz");
		rollingPolicy.setMaxHistory(7);
		rollingPolicy.start();

		fileAppender.setEncoder(filePattern);
		fileAppender.setRollingPolicy(rollingPolicy);
		fileAppender.start();

		final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
		logcatTagPattern.setContext(context);
		logcatTagPattern.setPattern("%logger{0}");
		logcatTagPattern.start();

		final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
		logcatPattern.setContext(context);
		logcatPattern.setPattern("[%thread] %msg%n");
		logcatPattern.start();

		final LogcatAppender logcatAppender = new LogcatAppender();
		logcatAppender.setContext(context);
		logcatAppender.setTagEncoder(logcatTagPattern);
		logcatAppender.setEncoder(logcatPattern);
		logcatAppender.start();

		final ch.qos.logback.classic.Logger log = context.getLogger(Logger.ROOT_LOGGER_NAME);
		log.addAppender(fileAppender);
		log.addAppender(logcatAppender);
		log.setLevel(Level.INFO);
	}

	private static final class WalletAutosaveEventListener implements WalletFiles.Listener
	{
		@Override
		public void onBeforeAutoSave(final File file)
		{
		}

		@Override
		public void onAfterAutoSave(final File file)
		{
			// make wallets world accessible in test mode
			if (Constants.TEST)
				Io.chmod(file, 0777);
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
			log.info("found wallet to migrate");

			final long start = System.currentTimeMillis();

			// read
			wallet = restoreWalletFromBackup();

			try
			{
				// write
				protobufSerializeWallet(wallet);

				// delete
				oldWalletFile.delete();

				log.info("wallet migrated: '" + oldWalletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
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

                try {
                    WalletProtobufSerializer ser = new WalletProtobufSerializer();
                    Protos.Wallet walletProto = ser.parseToProto(walletStream);
                    final String paramsID = walletProto.getNetworkIdentifier();
                    NetworkParameters params = NetworkParameters.fromID(paramsID);
                    if (params == null)
                        throw new UnreadableWalletException("Unknown network parameters ID " + paramsID);
                    wallet = new LitecoinWallet(params);
                    ser.readWallet(walletProto, wallet);
                } catch (IOException e) {
                    throw new UnreadableWalletException("Could not parse input stream to protobuf", e);
                }

				log.info("wallet loaded from: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
			}
			catch (final FileNotFoundException x)
			{
				log.error("problem loading wallet", x);

				Toast.makeText(WalletApplication.this, x.getClass().getName(), Toast.LENGTH_LONG).show();

				wallet = restoreWalletFromBackup();
			}
			catch (final UnreadableWalletException x)
			{
				log.error("problem loading wallet", x);

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
						// swallow
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
			wallet = new LitecoinWallet(Constants.NETWORK_PARAMETERS);

			log.info("new wallet created");
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

			log.info("wallet restored from backup: '" + Constants.WALLET_KEY_BACKUP_BASE58 + "'");

			return wallet;
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	private static Wallet readKeys(@Nonnull final InputStream is) throws IOException
	{
		final BufferedReader in = new BufferedReader(new InputStreamReader(is, Constants.UTF_8));
		final List<ECKey> keys = WalletUtils.readKeys(in);
		in.close();

		final Wallet wallet = new LitecoinWallet(Constants.NETWORK_PARAMETERS);
		for (final ECKey key : keys)
			wallet.addKey(key);

		return wallet;
	}

	private void ensureKey()
	{
		for (final ECKey key : wallet.getKeys())
			if (!wallet.isKeyRotating(key))
				return; // found

		log.info("wallet has no usable key - creating");
		addNewKeyToWallet();
	}

	public void addNewKeyToWallet()
	{
		wallet.addKey(new ECKey());

		backupKeys();

		prefs.edit().putBoolean(Constants.PREFS_KEY_REMIND_BACKUP, true).commit();
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

	private void protobufSerializeWallet(@Nonnull final Wallet wallet) throws IOException
	{
		final long start = System.currentTimeMillis();

		wallet.saveToFile(walletFile);

		// make wallets world accessible in test mode
		if (Constants.TEST)
			Io.chmod(walletFile, 0777);

		log.debug("wallet saved to: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
	}

    // Changed to append date/time stamp to prevent key overwriting
	private void backupKeys()
	{
		try
		{
			final String filename = String.format(Locale.US, "%s.%02d", Constants.WALLET_KEY_BACKUP_BASE58,
					(System.currentTimeMillis() / DateUtils.DAY_IN_MILLIS) % 100l);
			writeKeys(openFileOutput(filename, Context.MODE_PRIVATE));
            // If we
		}
		catch (final IOException x)
		{
			log.error("problem writing key backup", x);
		}
	}

	private void writeKeys(@Nonnull final OutputStream os) throws IOException
	{
		final List<ECKey> keys = new LinkedList<ECKey>();
		for (final ECKey key : wallet.getKeys())
			if (!wallet.isKeyRotating(key))
				keys.add(key);

		final Writer out = new OutputStreamWriter(os, Constants.UTF_8);
		WalletUtils.writeKeys(out, keys);
		out.close();
	}

	public Address determineSelectedAddress()
	{
		final String selectedAddress = prefs.getString(Constants.PREFS_KEY_SELECTED_ADDRESS, null);

		Address firstAddress = null;
		for (final ECKey key : wallet.getKeys())
		{
			if (!wallet.isKeyRotating(key))
			{
				final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);

				if (address.toString().equals(selectedAddress))
					return address;

				if (firstAddress == null)
					firstAddress = address;
			}
		}

		return firstAddress;
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

	public void broadcastTransaction(@Nonnull final Transaction tx)
	{
		final Intent intent = new Intent(BlockchainService.ACTION_BROADCAST_TRANSACTION, null, this, BlockchainServiceImpl.class);
		intent.putExtra(BlockchainService.ACTION_BROADCAST_TRANSACTION_HASH, tx.getHash().getBytes());
		startService(intent);
	}

	public boolean isServiceRunning(final Class<? extends Service> serviceClass)
	{
		final String packageName = getPackageName();

		for (final RunningServiceInfo serviceInfo : activityManager.getRunningServices(Integer.MAX_VALUE))
			if (packageName.equals(serviceInfo.service.getPackageName()) && serviceClass.getName().equals(serviceInfo.service.getClassName()))
				return true;

		return false;
	}

	public PackageInfo packageInfo()
	{
		return packageInfo;
	}

	public final String applicationPackageFlavor()
	{
		final String packageName = getPackageName();
		final int index = packageName.lastIndexOf('_');

		if (index != -1)
			return packageName.substring(index + 1);
		else
			return null;
	}

	public int maxConnectedPeers()
	{
		final int memoryClass = activityManager.getMemoryClass();
		if (memoryClass <= Constants.MEMORY_CLASS_LOWEND)
			return 4;
		else
			return 6;
	}

	public static void scheduleStartBlockchainService(@Nonnull final Context context)
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final long prefsLastUsed = prefs.getLong(Constants.PREFS_KEY_LAST_USED, 0);

		final long now = System.currentTimeMillis();
		final long lastUsedAgo = now - prefsLastUsed;

		// apply some backoff
		final long alarmInterval;
		if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_JUST_MS)
			alarmInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
		else if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_RECENTLY_MS)
			alarmInterval = AlarmManager.INTERVAL_HALF_DAY;
		else
			alarmInterval = AlarmManager.INTERVAL_DAY;

		log.info("last used {} minutes ago, rescheduling blockchain sync in roughly {} minutes", lastUsedAgo / DateUtils.MINUTE_IN_MILLIS,
				alarmInterval / DateUtils.MINUTE_IN_MILLIS);

		final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		final PendingIntent alarmIntent = PendingIntent.getService(context, 0, new Intent(context, BlockchainServiceImpl.class), 0);
		alarmManager.cancel(alarmIntent);
		if (Build.VERSION.SDK_INT >= Constants.SDK_KITKAT)
			// as of KitKat, set() is inexact
			alarmManager.set(AlarmManager.RTC_WAKEUP, now + alarmInterval, alarmIntent);
		else
			// workaround for no inexact set() before KitKat
			alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, now + alarmInterval, AlarmManager.INTERVAL_DAY, alarmIntent);
	}
}
