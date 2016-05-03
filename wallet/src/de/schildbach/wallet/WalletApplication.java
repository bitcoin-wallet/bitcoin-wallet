/*
 * Copyright 2011-2015 the original author or authors.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.crypto.LinuxSecureRandom;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletFiles;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import android.widget.Toast;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Io;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class WalletApplication extends Application
{
	private Configuration config;
	private ActivityManager activityManager;

	private Intent blockchainServiceIntent;
	private Intent blockchainServiceCancelCoinsReceivedIntent;
	private Intent blockchainServiceResetBlockchainIntent;

	private File walletFile;
	private Wallet wallet;
	private PackageInfo packageInfo;

	public static final String ACTION_WALLET_REFERENCE_CHANGED = WalletApplication.class.getPackage().getName() + ".wallet_reference_changed";

	public static final int VERSION_CODE_SHOW_BACKUP_REMINDER = 205;

	private static final Logger log = LoggerFactory.getLogger(WalletApplication.class);

	@Override
	public void onCreate()
	{
		new LinuxSecureRandom(); // init proper random number generator

		initLogging();

		StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites().penaltyLog().build());

		Threading.throwOnLockCycles();
		org.bitcoinj.core.Context.enableStrictMode();
		org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

		log.info("=== starting app using configuration: {}, {}", Constants.TEST ? "test" : "prod", Constants.NETWORK_PARAMETERS.getId());

		super.onCreate();

		packageInfo = packageInfoFromContext(this);

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

		initMnemonicCode();

		config = new Configuration(PreferenceManager.getDefaultSharedPreferences(this), getResources());
		activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

		blockchainServiceIntent = new Intent(this, BlockchainServiceImpl.class);
		blockchainServiceCancelCoinsReceivedIntent = new Intent(BlockchainService.ACTION_CANCEL_COINS_RECEIVED, null, this,
				BlockchainServiceImpl.class);
		blockchainServiceResetBlockchainIntent = new Intent(BlockchainService.ACTION_RESET_BLOCKCHAIN, null, this, BlockchainServiceImpl.class);

		walletFile = getFileStreamPath(Constants.Files.WALLET_FILENAME_PROTOBUF);

		loadWalletFromProtobuf();

		if (config.versionCodeCrossed(packageInfo.versionCode, VERSION_CODE_SHOW_BACKUP_REMINDER) && !wallet.getImportedKeys().isEmpty())
		{
			log.info("showing backup reminder once, because of imported keys being present");
			config.armBackupReminder();
		}

		config.updateLastVersionCode(packageInfo.versionCode);

		afterLoadWallet();

		cleanupFiles();
	}

	private void afterLoadWallet()
	{
		wallet.autosaveToFile(walletFile, 10, TimeUnit.SECONDS, new WalletAutosaveEventListener());

		// clean up spam
		wallet.cleanup();

		migrateBackup();
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

	private static final String BIP39_WORDLIST_FILENAME = "bip39-wordlist.txt";

	private void initMnemonicCode()
	{
		try
		{
			final long start = System.currentTimeMillis();
			MnemonicCode.INSTANCE = new MnemonicCode(getAssets().open(BIP39_WORDLIST_FILENAME), null);
			log.info("BIP39 wordlist loaded from: '" + BIP39_WORDLIST_FILENAME + "', took " + (System.currentTimeMillis() - start) + "ms");
		}
		catch (final IOException x)
		{
			throw new Error(x);
		}
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

	public Configuration getConfiguration()
	{
		return config;
	}

	public Wallet getWallet()
	{
		return wallet;
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

				if (!wallet.getParams().equals(Constants.NETWORK_PARAMETERS))
					throw new UnreadableWalletException("bad wallet network parameters: " + wallet.getParams().getId());

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
			wallet = new Wallet(Constants.NETWORK_PARAMETERS);

			backupWallet();

			config.armBackupReminder();

			log.info("new wallet created");
		}
	}

	private Wallet restoreWalletFromBackup()
	{
		InputStream is = null;

		try
		{
			is = openFileInput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF);

			final Wallet wallet = new WalletProtobufSerializer().readWallet(is, true, null);

			if (!wallet.isConsistent())
				throw new Error("inconsistent backup");

			resetBlockchain();

			Toast.makeText(this, R.string.toast_wallet_reset, Toast.LENGTH_LONG).show();

			log.info("wallet restored from backup: '" + Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + "'");

			return wallet;
		}
		catch (final IOException x)
		{
			throw new Error("cannot read backup", x);
		}
		catch (final UnreadableWalletException x)
		{
			throw new Error("cannot read backup", x);
		}
		finally
		{
			try
			{
				is.close();
			}
			catch (final IOException x)
			{
				// swallow
			}
		}
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
			Io.chmod(walletFile, 0777);

		log.debug("wallet saved to: '" + walletFile + "', took " + (System.currentTimeMillis() - start) + "ms");
	}

	public void backupWallet()
	{
		final Protos.Wallet.Builder builder = new WalletProtobufSerializer().walletToProto(wallet).toBuilder();

		// strip redundant
		builder.clearTransaction();
		builder.clearLastSeenBlockHash();
		builder.setLastSeenBlockHeight(-1);
		builder.clearLastSeenBlockTimeSecs();
		final Protos.Wallet walletProto = builder.build();

		OutputStream os = null;

		try
		{
			os = openFileOutput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF, Context.MODE_PRIVATE);
			walletProto.writeTo(os);
		}
		catch (final IOException x)
		{
			log.error("problem writing key backup", x);
		}
		finally
		{
			try
			{
				os.close();
			}
			catch (final IOException x)
			{
				// swallow
			}
		}
	}

	private void migrateBackup()
	{
		if (!getFileStreamPath(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF).exists())
		{
			log.info("migrating automatic backup to protobuf");

			// make sure there is at least one recent backup
			backupWallet();
		}
	}

	private void cleanupFiles()
	{
		for (final String filename : fileList())
		{
			if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_BASE58)
					|| filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + '.') || filename.endsWith(".tmp"))
			{
				final File file = new File(getFilesDir(), filename);
				log.info("removing obsolete file: '{}'", file);
				file.delete();
			}
		}
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
		// implicitly stops blockchain service
		startService(blockchainServiceResetBlockchainIntent);
	}

	public void replaceWallet(final Wallet newWallet)
	{
		resetBlockchain();
		wallet.shutdownAutosaveAndWait();

		wallet = newWallet;
		config.maybeIncrementBestChainHeightEver(newWallet.getLastBlockSeenHeight());
		afterLoadWallet();

		final Intent broadcast = new Intent(ACTION_WALLET_REFERENCE_CHANGED);
		broadcast.setPackage(getPackageName());
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	public void processDirectTransaction(final Transaction tx) throws VerificationException
	{
		if (wallet.isTransactionRelevant(tx))
		{
			wallet.receivePending(tx, null);
			broadcastTransaction(tx);
		}
	}

	public void broadcastTransaction(final Transaction tx)
	{
		final Intent intent = new Intent(BlockchainService.ACTION_BROADCAST_TRANSACTION, null, this, BlockchainServiceImpl.class);
		intent.putExtra(BlockchainService.ACTION_BROADCAST_TRANSACTION_HASH, tx.getHash().getBytes());
		startService(intent);
	}

	public static PackageInfo packageInfoFromContext(final Context context)
	{
		try
		{
			return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		}
		catch (final NameNotFoundException x)
		{
			throw new RuntimeException(x);
		}
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

	public static String httpUserAgent(final String versionName)
	{
		final VersionMessage versionMessage = new VersionMessage(Constants.NETWORK_PARAMETERS, 0);
		versionMessage.appendToSubVer(Constants.USER_AGENT, versionName, null);
		return versionMessage.subVer;
	}

	public String httpUserAgent()
	{
		return httpUserAgent(packageInfo().versionName);
	}

	public int maxConnectedPeers()
	{
		final int memoryClass = activityManager.getMemoryClass();
		if (memoryClass <= Constants.MEMORY_CLASS_LOWEND)
			return 4;
		else
			return 6;
	}

	public static void scheduleStartBlockchainService(final Context context)
	{
		final Configuration config = new Configuration(PreferenceManager.getDefaultSharedPreferences(context), context.getResources());
		final long lastUsedAgo = config.getLastUsedAgo();

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

		// workaround for no inexact set() before KitKat
		final long now = System.currentTimeMillis();
		alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, now + alarmInterval, AlarmManager.INTERVAL_DAY, alarmIntent);
	}
}
