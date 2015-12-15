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

package de.schildbach.wallet.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.bitcoinj.core.AbstractPeerEventListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerEventListener;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletEventListener;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.service.BlockchainState.Impediment;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class BlockchainServiceImpl extends android.app.Service implements BlockchainService
{
	private WalletApplication application;
	private Configuration config;

	private BlockStore blockStore;
	private File blockChainFile;
	private BlockChain blockChain;
	@Nullable
	private PeerGroup peerGroup;

	private final Handler handler = new Handler();
	private final Handler delayHandler = new Handler();
	private WakeLock wakeLock;

	private PeerConnectivityListener peerConnectivityListener;
	private NotificationManager nm;
	private static final int NOTIFICATION_ID_CONNECTED = 0;
	private static final int NOTIFICATION_ID_COINS_RECEIVED = 1;

	private final Set<Impediment> impediments = EnumSet.noneOf(Impediment.class);
	private int notificationCount = 0;
	private Coin notificationAccumulatedAmount = Coin.ZERO;
	private final List<Address> notificationAddresses = new LinkedList<Address>();
	private AtomicInteger transactionsReceived = new AtomicInteger();
	private long serviceCreatedAt;
	private boolean resetBlockchainOnShutdown = false;

	private static final int MIN_COLLECT_HISTORY = 2;
	private static final int IDLE_BLOCK_TIMEOUT_MIN = 2;
	private static final int IDLE_TRANSACTION_TIMEOUT_MIN = 9;
	private static final int MAX_HISTORY_SIZE = Math.max(IDLE_TRANSACTION_TIMEOUT_MIN, IDLE_BLOCK_TIMEOUT_MIN);
	private static final long APPWIDGET_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;
	private static final long BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;

	private static final Logger log = LoggerFactory.getLogger(BlockchainServiceImpl.class);

	private final WalletEventListener walletEventListener = new ThrottlingWalletChangeListener(APPWIDGET_THROTTLE_MS)
	{
		@Override
		public void onThrottledWalletChanged()
		{
			WalletBalanceWidgetProvider.updateWidgets(BlockchainServiceImpl.this, application.getWallet());
		}

		@Override
		public void onCoinsReceived(final Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance)
		{
			transactionsReceived.incrementAndGet();

			final int bestChainHeight = blockChain.getBestChainHeight();

			final Address address = WalletUtils.getWalletAddressOfReceived(tx, wallet);
			final Coin amount = tx.getValue(wallet);
			final ConfidenceType confidenceType = tx.getConfidence().getConfidenceType();

			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					final boolean isReceived = amount.signum() > 0;
					final boolean replaying = bestChainHeight < config.getBestChainHeightEver();
					final boolean isReplayedTx = confidenceType == ConfidenceType.BUILDING && replaying;

					if (isReceived && !isReplayedTx)
						notifyCoinsReceived(address, amount);
				}
			});
		}

		@Override
		public void onCoinsSent(final Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance)
		{
			transactionsReceived.incrementAndGet();
		}
	};

	private void notifyCoinsReceived(@Nullable final Address address, final Coin amount)
	{
		if (notificationCount == 1)
			nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);

		notificationCount++;
		notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
		if (address != null && !notificationAddresses.contains(address))
			notificationAddresses.add(address);

		final MonetaryFormat btcFormat = config.getFormat();

		final String packageFlavor = application.applicationPackageFlavor();
		final String msgSuffix = packageFlavor != null ? " [" + packageFlavor + "]" : "";

		final String tickerMsg = getString(R.string.notification_coins_received_msg, btcFormat.format(amount)) + msgSuffix;
		final String msg = getString(R.string.notification_coins_received_msg, btcFormat.format(notificationAccumulatedAmount)) + msgSuffix;

		final StringBuilder text = new StringBuilder();
		for (final Address notificationAddress : notificationAddresses)
		{
			if (text.length() > 0)
				text.append(", ");

			final String addressStr = notificationAddress.toString();
			final String label = AddressBookProvider.resolveLabel(getApplicationContext(), addressStr);
			text.append(label != null ? label : addressStr);
		}

		final Notification.Builder notification = new Notification.Builder(this);
		notification.setSmallIcon(R.drawable.stat_notify_received);
		notification.setTicker(tickerMsg);
		notification.setContentTitle(msg);
		if (text.length() > 0)
			notification.setContentText(text);
		notification.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
		notification.setNumber(notificationCount == 1 ? 0 : notificationCount);
		notification.setWhen(System.currentTimeMillis());
		notification.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received));
		nm.notify(NOTIFICATION_ID_COINS_RECEIVED, notification.getNotification());
	}

	private final class PeerConnectivityListener extends AbstractPeerEventListener implements OnSharedPreferenceChangeListener
	{
		private int peerCount;
		private AtomicBoolean stopped = new AtomicBoolean(false);

		public PeerConnectivityListener()
		{
			config.registerOnSharedPreferenceChangeListener(this);
		}

		public void stop()
		{
			stopped.set(true);

			config.unregisterOnSharedPreferenceChangeListener(this);

			nm.cancel(NOTIFICATION_ID_CONNECTED);
		}

		@Override
		public void onPeerConnected(final Peer peer, final int peerCount)
		{
			this.peerCount = peerCount;
			changed(peerCount);
		}

		@Override
		public void onPeerDisconnected(final Peer peer, final int peerCount)
		{
			this.peerCount = peerCount;
			changed(peerCount);
		}

		@Override
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (Configuration.PREFS_KEY_CONNECTIVITY_NOTIFICATION.equals(key))
				changed(peerCount);
		}

		private void changed(final int numPeers)
		{
			if (stopped.get())
				return;

			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					final boolean connectivityNotificationEnabled = config.getConnectivityNotificationEnabled();

					if (!connectivityNotificationEnabled || numPeers == 0)
					{
						nm.cancel(NOTIFICATION_ID_CONNECTED);
					}
					else
					{
						final Notification.Builder notification = new Notification.Builder(BlockchainServiceImpl.this);
						notification.setSmallIcon(R.drawable.stat_sys_peers, numPeers > 4 ? 4 : numPeers);
						notification.setContentTitle(getString(R.string.app_name));
						notification.setContentText(getString(R.string.notification_peers_connected_msg, numPeers));
						notification.setContentIntent(PendingIntent.getActivity(BlockchainServiceImpl.this, 0, new Intent(BlockchainServiceImpl.this,
								WalletActivity.class), 0));
						notification.setWhen(System.currentTimeMillis());
						notification.setOngoing(true);
						nm.notify(NOTIFICATION_ID_CONNECTED, notification.getNotification());
					}

					// send broadcast
					broadcastPeerState(numPeers);
				}
			});
		}
	}

	private final PeerEventListener blockchainDownloadListener = new AbstractPeerEventListener()
	{
		private final AtomicLong lastMessageTime = new AtomicLong(0);

		@Override
		public void onBlocksDownloaded(final Peer peer, final Block block, final FilteredBlock filteredBlock, final int blocksLeft)
		{
			config.maybeIncrementBestChainHeightEver(blockChain.getChainHead().getHeight());

			delayHandler.removeCallbacksAndMessages(null);

			final long now = System.currentTimeMillis();

			if (now - lastMessageTime.get() > BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS)
				delayHandler.post(runnable);
			else
				delayHandler.postDelayed(runnable, BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS);
		}

		private final Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				lastMessageTime.set(System.currentTimeMillis());

				broadcastBlockchainState();
			}
		};
	};

	private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final String action = intent.getAction();

			if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action))
			{
				final boolean hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
				log.info("network is " + (hasConnectivity ? "up" : "down"));

				if (hasConnectivity)
					impediments.remove(Impediment.NETWORK);
				else
					impediments.add(Impediment.NETWORK);
				check();
			}
			else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action))
			{
				log.info("device storage low");

				impediments.add(Impediment.STORAGE);
				check();
			}
			else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action))
			{
				log.info("device storage ok");

				impediments.remove(Impediment.STORAGE);
				check();
			}
		}

		@SuppressLint("Wakelock")
		private void check()
		{
			final Wallet wallet = application.getWallet();

			if (impediments.isEmpty() && peerGroup == null)
			{
				log.debug("acquiring wakelock");
				wakeLock.acquire();

				// consistency check
				final int walletLastBlockSeenHeight = wallet.getLastBlockSeenHeight();
				final int bestChainHeight = blockChain.getBestChainHeight();
				if (walletLastBlockSeenHeight != -1 && walletLastBlockSeenHeight != bestChainHeight)
				{
					final String message = "wallet/blockchain out of sync: " + walletLastBlockSeenHeight + "/" + bestChainHeight;
					log.error(message);
					CrashReporter.saveBackgroundTrace(new RuntimeException(message), application.packageInfo());
				}

				log.info("starting peergroup");
				peerGroup = new PeerGroup(Constants.NETWORK_PARAMETERS, blockChain);
				peerGroup.setDownloadTxDependencies(false); // recursive implementation causes StackOverflowError
				peerGroup.addWallet(wallet);
				peerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
				peerGroup.addEventListener(peerConnectivityListener);

				final int maxConnectedPeers = application.maxConnectedPeers();

				final String trustedPeerHost = config.getTrustedPeerHost();
				final boolean hasTrustedPeer = !trustedPeerHost.isEmpty();

				final boolean connectTrustedPeerOnly = hasTrustedPeer && config.getTrustedPeerOnly();
				peerGroup.setMaxConnections(connectTrustedPeerOnly ? 1 : maxConnectedPeers);
				peerGroup.setConnectTimeoutMillis(Constants.PEER_TIMEOUT_MS);

				peerGroup.addPeerDiscovery(new PeerDiscovery()
				{
					private final PeerDiscovery normalPeerDiscovery = new DnsDiscovery(Constants.NETWORK_PARAMETERS);

					@Override
					public InetSocketAddress[] getPeers(final long timeoutValue, final TimeUnit timeoutUnit) throws PeerDiscoveryException
					{
						final List<InetSocketAddress> peers = new LinkedList<InetSocketAddress>();

						boolean needsTrimPeersWorkaround = false;

						if (hasTrustedPeer)
						{
							log.info("trusted peer '" + trustedPeerHost + "'" + (connectTrustedPeerOnly ? " only" : ""));

							final InetSocketAddress addr = new InetSocketAddress(trustedPeerHost, Constants.NETWORK_PARAMETERS.getPort());
							if (addr.getAddress() != null)
							{
								peers.add(addr);
								needsTrimPeersWorkaround = true;
							}
						}

						if (!connectTrustedPeerOnly)
							peers.addAll(Arrays.asList(normalPeerDiscovery.getPeers(timeoutValue, timeoutUnit)));

						// workaround because PeerGroup will shuffle peers
						if (needsTrimPeersWorkaround)
							while (peers.size() >= maxConnectedPeers)
								peers.remove(peers.size() - 1);

						return peers.toArray(new InetSocketAddress[0]);
					}

					@Override
					public void shutdown()
					{
						normalPeerDiscovery.shutdown();
					}
				});

				// start peergroup
				peerGroup.startAsync();
				peerGroup.startBlockChainDownload(blockchainDownloadListener);
			}
			else if (!impediments.isEmpty() && peerGroup != null)
			{
				log.info("stopping peergroup");
				peerGroup.removeEventListener(peerConnectivityListener);
				peerGroup.removeWallet(wallet);
				peerGroup.stopAsync();
				peerGroup = null;

				log.debug("releasing wakelock");
				wakeLock.release();
			}

			broadcastBlockchainState();
		}
	};

	private final static class ActivityHistoryEntry
	{
		public final int numTransactionsReceived;
		public final int numBlocksDownloaded;

		public ActivityHistoryEntry(final int numTransactionsReceived, final int numBlocksDownloaded)
		{
			this.numTransactionsReceived = numTransactionsReceived;
			this.numBlocksDownloaded = numBlocksDownloaded;
		}

		@Override
		public String toString()
		{
			return numTransactionsReceived + "/" + numBlocksDownloaded;
		}
	}

	private final BroadcastReceiver tickReceiver = new BroadcastReceiver()
	{
		private int lastChainHeight = 0;
		private final List<ActivityHistoryEntry> activityHistory = new LinkedList<ActivityHistoryEntry>();

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final int chainHeight = blockChain.getBestChainHeight();

			if (lastChainHeight > 0)
			{
				final int numBlocksDownloaded = chainHeight - lastChainHeight;
				final int numTransactionsReceived = transactionsReceived.getAndSet(0);

				// push history
				activityHistory.add(0, new ActivityHistoryEntry(numTransactionsReceived, numBlocksDownloaded));

				// trim
				while (activityHistory.size() > MAX_HISTORY_SIZE)
					activityHistory.remove(activityHistory.size() - 1);

				// print
				final StringBuilder builder = new StringBuilder();
				for (final ActivityHistoryEntry entry : activityHistory)
				{
					if (builder.length() > 0)
						builder.append(", ");
					builder.append(entry);
				}
				log.info("History of transactions/blocks: " + builder);

				// determine if block and transaction activity is idling
				boolean isIdle = false;
				if (activityHistory.size() >= MIN_COLLECT_HISTORY)
				{
					isIdle = true;
					for (int i = 0; i < activityHistory.size(); i++)
					{
						final ActivityHistoryEntry entry = activityHistory.get(i);
						final boolean blocksActive = entry.numBlocksDownloaded > 0 && i <= IDLE_BLOCK_TIMEOUT_MIN;
						final boolean transactionsActive = entry.numTransactionsReceived > 0 && i <= IDLE_TRANSACTION_TIMEOUT_MIN;

						if (blocksActive || transactionsActive)
						{
							isIdle = false;
							break;
						}
					}
				}

				// if idling, shutdown service
				if (isIdle)
				{
					log.info("idling detected, stopping service");
					stopSelf();
				}
			}

			lastChainHeight = chainHeight;
		}
	};

	public class LocalBinder extends Binder
	{
		public BlockchainService getService()
		{
			return BlockchainServiceImpl.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(final Intent intent)
	{
		log.debug(".onBind()");

		return mBinder;
	}

	@Override
	public boolean onUnbind(final Intent intent)
	{
		log.debug(".onUnbind()");

		return super.onUnbind(intent);
	}

	@Override
	public void onCreate()
	{
		serviceCreatedAt = System.currentTimeMillis();
		log.debug(".onCreate()");

		super.onCreate();

		nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		final String lockName = getPackageName() + " blockchain sync";

		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);

		application = (WalletApplication) getApplication();
		config = application.getConfiguration();
		final Wallet wallet = application.getWallet();

		peerConnectivityListener = new PeerConnectivityListener();

		broadcastPeerState(0);

		blockChainFile = new File(getDir("blockstore", Context.MODE_PRIVATE), Constants.Files.BLOCKCHAIN_FILENAME);
		final boolean blockChainFileExists = blockChainFile.exists();

		if (!blockChainFileExists)
		{
			log.info("blockchain does not exist, resetting wallet");
			wallet.reset();
		}

		try
		{
			blockStore = new SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile);
			blockStore.getChainHead(); // detect corruptions as early as possible

			final long earliestKeyCreationTime = wallet.getEarliestKeyCreationTime();

			if (!blockChainFileExists && earliestKeyCreationTime > 0)
			{
				try
				{
					final long start = System.currentTimeMillis();
					final InputStream checkpointsInputStream = getAssets().open(Constants.Files.CHECKPOINTS_FILENAME);
					CheckpointManager.checkpoint(Constants.NETWORK_PARAMETERS, checkpointsInputStream, blockStore, earliestKeyCreationTime);
					log.info("checkpoints loaded from '{}', took {}ms", Constants.Files.CHECKPOINTS_FILENAME, System.currentTimeMillis() - start);
				}
				catch (final IOException x)
				{
					log.error("problem reading checkpoints, continuing without", x);
				}
			}
		}
		catch (final BlockStoreException x)
		{
			blockChainFile.delete();

			final String msg = "blockstore cannot be created";
			log.error(msg, x);
			throw new Error(msg, x);
		}

		try
		{
			blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, blockStore);
		}
		catch (final BlockStoreException x)
		{
			throw new Error("blockchain cannot be created", x);
		}

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
		registerReceiver(connectivityReceiver, intentFilter); // implicitly start PeerGroup

		application.getWallet().addEventListener(walletEventListener, Threading.SAME_THREAD);

		registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		if (intent != null)
		{
			log.info("service start command: " + intent
					+ (intent.hasExtra(Intent.EXTRA_ALARM_COUNT) ? " (alarm count: " + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) + ")" : ""));

			final String action = intent.getAction();

			if (BlockchainService.ACTION_CANCEL_COINS_RECEIVED.equals(action))
			{
				notificationCount = 0;
				notificationAccumulatedAmount = Coin.ZERO;
				notificationAddresses.clear();

				nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);
			}
			else if (BlockchainService.ACTION_RESET_BLOCKCHAIN.equals(action))
			{
				log.info("will remove blockchain on service shutdown");

				resetBlockchainOnShutdown = true;
				stopSelf();
			}
			else if (BlockchainService.ACTION_BROADCAST_TRANSACTION.equals(action))
			{
				final Sha256Hash hash = Sha256Hash.wrap(intent.getByteArrayExtra(BlockchainService.ACTION_BROADCAST_TRANSACTION_HASH));
				final Transaction tx = application.getWallet().getTransaction(hash);

				if (peerGroup != null)
				{
					log.info("broadcasting transaction " + tx.getHashAsString());
					peerGroup.broadcastTransaction(tx);
				}
				else
				{
					log.info("peergroup not available, not broadcasting transaction " + tx.getHashAsString());
				}
			}
		}
		else
		{
			log.warn("service restart, although it was started as non-sticky");
		}

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy()
	{
		log.debug(".onDestroy()");

		WalletApplication.scheduleStartBlockchainService(this);

		unregisterReceiver(tickReceiver);

		application.getWallet().removeEventListener(walletEventListener);

		unregisterReceiver(connectivityReceiver);

		if (peerGroup != null)
		{
			peerGroup.removeEventListener(peerConnectivityListener);
			peerGroup.removeWallet(application.getWallet());
			peerGroup.stop();

			log.info("peergroup stopped");
		}

		peerConnectivityListener.stop();

		delayHandler.removeCallbacksAndMessages(null);

		try
		{
			blockStore.close();
		}
		catch (final BlockStoreException x)
		{
			throw new RuntimeException(x);
		}

		application.saveWallet();

		if (wakeLock.isHeld())
		{
			log.debug("wakelock still held, releasing");
			wakeLock.release();
		}

		if (resetBlockchainOnShutdown)
		{
			log.info("removing blockchain");
			blockChainFile.delete();
		}

		super.onDestroy();

		log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
	}

	@Override
	public void onTrimMemory(final int level)
	{
		log.info("onTrimMemory({}) called", level);

		if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
		{
			log.warn("low memory detected, stopping service");
			stopSelf();
		}
	}

	@Override
	public BlockchainState getBlockchainState()
	{
		final StoredBlock chainHead = blockChain.getChainHead();
		final Date bestChainDate = chainHead.getHeader().getTime();
		final int bestChainHeight = chainHead.getHeight();
		final boolean replaying = chainHead.getHeight() < config.getBestChainHeightEver();

		return new BlockchainState(bestChainDate, bestChainHeight, replaying, impediments);
	}

	@Override
	public List<Peer> getConnectedPeers()
	{
		if (peerGroup != null)
			return peerGroup.getConnectedPeers();
		else
			return null;
	}

	@Override
	public List<StoredBlock> getRecentBlocks(final int maxBlocks)
	{
		final List<StoredBlock> blocks = new ArrayList<StoredBlock>(maxBlocks);

		try
		{
			StoredBlock block = blockChain.getChainHead();

			while (block != null)
			{
				blocks.add(block);

				if (blocks.size() >= maxBlocks)
					break;

				block = block.getPrev(blockStore);
			}
		}
		catch (final BlockStoreException x)
		{
			// swallow
		}

		return blocks;
	}

	private void broadcastPeerState(final int numPeers)
	{
		final Intent broadcast = new Intent(ACTION_PEER_STATE);
		broadcast.setPackage(getPackageName());
		broadcast.putExtra(ACTION_PEER_STATE_NUM_PEERS, numPeers);

		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	private void broadcastBlockchainState()
	{
		final Intent broadcast = new Intent(ACTION_BLOCKCHAIN_STATE);
		broadcast.setPackage(getPackageName());
		getBlockchainState().putExtras(broadcast);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}
}
