/*
 * Copyright 2011-2012 the original author or authors.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.google.bitcoin.core.AbstractPeerEventListener;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.PeerEventListener;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.IrcDiscovery;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.discovery.PeerDiscoveryException;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.BoundedOverheadBlockStore;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class BlockchainServiceImpl extends android.app.Service implements BlockchainService
{
	private WalletApplication application;
	private SharedPreferences prefs;

	private BlockStore blockStore;
	private BlockChain blockChain;
	private PeerGroup peerGroup;

	private final Handler handler = new Handler();
	private final Handler delayHandler = new Handler();
	private WakeLock wakeLock;
	private WifiLock wifiLock;

	private NotificationManager nm;
	private static final int NOTIFICATION_ID_CONNECTED = 0;
	private static final int NOTIFICATION_ID_COINS_RECEIVED = 1;

	private int notificationCount = 0;
	private BigInteger notificationAccumulatedAmount = BigInteger.ZERO;
	private final List<Address> notificationAddresses = new LinkedList<Address>();

	private static final int MAX_LAST_CHAIN_HEIGHTS = 10;
	private static final int IDLE_TIMEOUT_MIN = 2;

	private final WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{
		@Override
		public void onCoinsReceived(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			try
			{
				final Address from;
				if (!tx.isCoinBase())
				{
					final TransactionInput input = tx.getInputs().get(0);
					from = input.getFromAddress();
				}
				else
				{
					from = null;
				}

				final BigInteger amount = tx.getValue(wallet);

				handler.post(new Runnable()
				{
					public void run()
					{
						if (amount.signum() > 0)
							notifyCoinsReceived(from, amount);

						notifyWidgets();
					}
				});
			}
			catch (final ScriptException x)
			{
				throw new RuntimeException(x);
			}
		}
	};

	private void notifyCoinsReceived(final Address from, final BigInteger amount)
	{
		if (notificationCount == 1)
			nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);

		notificationCount++;
		notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
		if (from != null && !notificationAddresses.contains(from))
			notificationAddresses.add(from);

		final String tickerMsg = getString(R.string.notification_coins_received_msg, WalletUtils.formatValue(amount))
				+ (Constants.TEST ? " [testnet3]" : "");

		final String msg = getString(R.string.notification_coins_received_msg, WalletUtils.formatValue(notificationAccumulatedAmount))
				+ (Constants.TEST ? " [testnet3]" : "");

		final StringBuilder text = new StringBuilder();
		for (final Address address : notificationAddresses)
		{
			if (text.length() > 0)
				text.append(", ");
			text.append(address.toString());
		}

		if (text.length() == 0)
			text.append("unknown");

		text.insert(0, "From ");

		final NotificationCompat.Builder notification = new NotificationCompat.Builder(this);
		notification.setSmallIcon(R.drawable.stat_notify_received);
		notification.setTicker(tickerMsg);
		notification.setContentTitle(msg);
		notification.setContentText(text);
		notification.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
		notification.setNumber(notificationCount == 1 ? 0 : notificationCount);
		notification.setWhen(System.currentTimeMillis());
		notification.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received));
		nm.notify(NOTIFICATION_ID_COINS_RECEIVED, notification.getNotification());
	}

	private final PeerEventListener peerEventListener = new AbstractPeerEventListener()
	{
		@Override
		public void onPeerConnected(final Peer peer, final int peerCount)
		{
			System.out.println("Peer connected, count " + peerCount);

			changed(peerCount);
		}

		@Override
		public void onPeerDisconnected(final Peer peer, final int peerCount)
		{
			System.out.println("Peer disconnected, count " + peerCount);

			changed(peerCount);
		}

		private void changed(final int numPeers)
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					if (numPeers == 0)
					{
						nm.cancel(NOTIFICATION_ID_CONNECTED);
					}
					else
					{
						final NotificationCompat.Builder notification = new NotificationCompat.Builder(BlockchainServiceImpl.this);
						notification.setSmallIcon(R.drawable.stat_sys_peers, numPeers > 4 ? 4 : numPeers);
						notification.setContentTitle(getString(R.string.app_name) + (Constants.TEST ? " [testnet3]" : ""));
						notification.setContentText(getString(R.string.notification_peers_connected_msg, numPeers));
						notification.setContentIntent(PendingIntent.getActivity(BlockchainServiceImpl.this, 0, new Intent(BlockchainServiceImpl.this,
								WalletActivity.class), 0));
						notification.setWhen(System.currentTimeMillis());
						notification.setOngoing(true);
						nm.notify(NOTIFICATION_ID_CONNECTED, notification.getNotification());
					}

					// send broadcast
					sendBroadcastPeerState(numPeers);
				}
			});
		}
	};

	private final PeerEventListener blockchainDownloadListener = new AbstractPeerEventListener()
	{
		private final AtomicLong lastMessageTime = new AtomicLong(0);

		@Override
		public void onBlocksDownloaded(final Peer peer, final Block block, final int blocksLeft)
		{
			delayHandler.removeCallbacksAndMessages(null);

			final long now = System.currentTimeMillis();

			if (now - lastMessageTime.get() > Constants.BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS)
				delayHandler.post(runnable);
			else
				delayHandler.postDelayed(runnable, Constants.BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS);
		}

		private final Runnable runnable = new Runnable()
		{
			public void run()
			{
				lastMessageTime.set(System.currentTimeMillis());

				final Date bestChainDate = new Date(blockChain.getChainHead().getHeader().getTimeSeconds() * 1000);
				final int bestChainHeight = blockChain.getBestChainHeight();

				sendBroadcastBlockchainState(bestChainDate, bestChainHeight, ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
			}
		};
	};

	private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver()
	{
		private boolean hasConnectivity;
		private boolean hasPower;
		private boolean hasStorage = true;

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final String action = intent.getAction();

			if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action))
			{
				hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
				final String reason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
				// final boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);
				System.out.println("network is " + (hasConnectivity ? "up" : "down") + (reason != null ? ": " + reason : ""));

				check();
			}
			else if (Intent.ACTION_BATTERY_CHANGED.equals(action))
			{
				final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				final int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
				hasPower = plugged != 0 || level > scale / 10;
				System.out.println("battery changed: level=" + level + "/" + scale + " plugged=" + plugged);

				check();
			}
			else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action))
			{
				hasStorage = false;
				System.out.println("device storage low");

				check();
			}
			else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action))
			{
				hasStorage = true;
				System.out.println("device storage ok");

				check();
			}
		}

		private void check()
		{
			final Wallet wallet = application.getWallet();
			final boolean hasEverything = hasConnectivity && hasPower && hasStorage;

			if (hasEverything && peerGroup == null)
			{
				System.out.println("acquiring wakelock");
				wakeLock.acquire();

				System.out.println("starting peergroup");
				peerGroup = new PeerGroup(Constants.NETWORK_PARAMETERS, blockChain, 1000);
				peerGroup.addWallet(wallet);
				peerGroup.setUserAgent(Constants.USER_AGENT, application.applicationVersionName());
				peerGroup.setFastCatchupTimeSecs(wallet.getEarliestKeyCreationTime());
				peerGroup.addEventListener(peerEventListener);

				final String trustedPeerHost = prefs.getString(Constants.PREFS_KEY_TRUSTED_PEER, "").trim();
				if (trustedPeerHost.length() == 0)
				{
					peerGroup.setMaxConnections(Constants.MAX_CONNECTED_PEERS);
					peerGroup.addPeerDiscovery(Constants.TEST ? new IrcDiscovery(Constants.PEER_DISCOVERY_IRC_CHANNEL_TEST) : new DnsDiscovery(
							Constants.NETWORK_PARAMETERS));
				}
				else
				{
					peerGroup.setMaxConnections(1);
					peerGroup.addPeerDiscovery(new PeerDiscovery()
					{
						public InetSocketAddress[] getPeers() throws PeerDiscoveryException
						{
							return new InetSocketAddress[] { new InetSocketAddress(trustedPeerHost, Constants.NETWORK_PARAMETERS.port) };
						}

						public void shutdown()
						{
						}
					});
				}
				peerGroup.start();

				peerGroup.startBlockChainDownload(blockchainDownloadListener);
			}
			else if (!hasEverything && peerGroup != null)
			{
				System.out.println("stopping peergroup");
				peerGroup.removeEventListener(peerEventListener);
				peerGroup.removeWallet(wallet);
				peerGroup.stop();
				peerGroup = null;

				System.out.println("releasing wakelock");
				wakeLock.release();
			}

			final Date bestChainDate = new Date(blockChain.getChainHead().getHeader().getTimeSeconds() * 1000);
			final int bestChainHeight = blockChain.getBestChainHeight();
			final int download = (hasConnectivity ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM)
					| (hasPower ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM)
					| (hasStorage ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM);

			sendBroadcastBlockchainState(bestChainDate, bestChainHeight, download);
		}
	};

	private final BroadcastReceiver tickReceiver = new BroadcastReceiver()
	{
		private int lastChainHeight = 0;
		private final List<Integer> lastDownloadedHistory = new LinkedList<Integer>();

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final int chainHeight = blockChain.getBestChainHeight();

			if (lastChainHeight > 0)
			{
				final int downloaded = chainHeight - lastChainHeight;

				// push number of downloaded blocks
				lastDownloadedHistory.add(0, downloaded);

				// trim
				while (lastDownloadedHistory.size() > MAX_LAST_CHAIN_HEIGHTS)
					lastDownloadedHistory.remove(lastDownloadedHistory.size() - 1);

				// print
				final StringBuilder builder = new StringBuilder();
				for (final int lastDownloaded : lastDownloadedHistory)
				{
					if (builder.length() > 0)
						builder.append(',');
					builder.append(lastDownloaded);
				}
				System.out.println("Number of blocks downloaded: " + builder);

				// determine if download is idling
				boolean isIdle = false;
				if (lastDownloadedHistory.size() >= IDLE_TIMEOUT_MIN)
				{
					isIdle = true;
					for (int i = 0; i < IDLE_TIMEOUT_MIN; i++)
					{
						if (lastDownloadedHistory.get(i) > 0)
						{
							isIdle = false;
							break;
						}
					}
				}

				// if idling, shutdown service
				if (isIdle)
				{
					System.out.println("end of block download detected, stopping service");
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
		System.out.println(getClass().getName() + ".onBind()");

		return mBinder;
	}

	@Override
	public boolean onUnbind(final Intent intent)
	{
		System.out.println(getClass().getName() + ".onUnbind()");

		return super.onUnbind(intent);
	}

	@Override
	public void onCreate()
	{
		System.out.println(getClass().getName() + ".onCreate()");

		super.onCreate();

		nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.LOCK_NAME);

		final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, Constants.LOCK_NAME);
		wifiLock.setReferenceCounted(false);

		application = (WalletApplication) getApplication();
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final Wallet wallet = application.getWallet();

		sendBroadcastPeerState(0);

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
		registerReceiver(connectivityReceiver, intentFilter);

		final int versionCode = application.applicationVersionCode();
		prefs.edit().putInt(Constants.PREFS_KEY_LAST_VERSION, versionCode).commit();

		final File blockChainFile = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE),
				Constants.BLOCKCHAIN_FILENAME);
		final boolean blockchainDoesNotExist = !blockChainFile.exists();

		if (blockchainDoesNotExist)
			copyBlockchainSnapshot(blockChainFile);

		try
		{
			try
			{
				blockStore = new BoundedOverheadBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile);
				blockStore.getChainHead(); // detect corruptions as early as possible
			}
			catch (final BlockStoreException x)
			{
				wallet.clearTransactions(0);
				blockChainFile.delete();

				x.printStackTrace();
				throw new Error("blockstore cannot be created", x);
			}
			catch (final IllegalStateException x)
			{
				wallet.clearTransactions(0);
				blockChainFile.delete();

				x.printStackTrace();
				throw new Error("blockstore cannot be created", x);
			}

			blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, blockStore);

			application.getWallet().addEventListener(walletEventListener);
		}
		catch (final BlockStoreException x)
		{
			throw new Error("blockchain cannot be created", x);
		}

		registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		if (BlockchainService.ACTION_CANCEL_COINS_RECEIVED.equals(intent.getAction()))
		{
			notificationCount = 0;
			notificationAccumulatedAmount = BigInteger.ZERO;
			notificationAddresses.clear();

			nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);
		}

		if (BlockchainService.ACTION_HOLD_WIFI_LOCK.equals(intent.getAction()))
		{
			System.out.println("acquiring wifilock");
			wifiLock.acquire();
		}
		else
		{
			System.out.println("releasing wifilock");
			wifiLock.release();
		}

		return START_NOT_STICKY;
	}

	private void copyBlockchainSnapshot(final File file)
	{
		try
		{
			final long t = System.currentTimeMillis();

			final String blockchainSnapshotFilename = Constants.BLOCKCHAIN_SNAPSHOT_FILENAME;
			final InputStream is = getAssets().open(blockchainSnapshotFilename);
			final OutputStream os = new FileOutputStream(file);

			System.out.println("copying blockchain snapshot");
			final byte[] buf = new byte[8192];
			int read;
			while (-1 != (read = is.read(buf)))
				os.write(buf, 0, read);
			os.close();
			is.close();
			System.out.println("finished copying, took " + (System.currentTimeMillis() - t) + " ms");
		}
		catch (final IOException x)
		{
			System.out.println("failed copying, starting from genesis");
			file.delete();
		}
	}

	@Override
	public void onDestroy()
	{
		System.out.println(getClass().getName() + ".onDestroy()");

		unregisterReceiver(tickReceiver);

		application.getWallet().removeEventListener(walletEventListener);

		if (peerGroup != null)
		{
			peerGroup.removeEventListener(peerEventListener);
			peerGroup.stop();
		}

		unregisterReceiver(connectivityReceiver);

		removeBroadcastPeerState();
		removeBroadcastBlockchainState();

		delayHandler.removeCallbacksAndMessages(null);

		try
		{
			blockStore.close();
		}
		catch (final BlockStoreException x)
		{
			throw new RuntimeException(x);
		}

		nm.cancel(NOTIFICATION_ID_CONNECTED);

		if (wakeLock.isHeld())
		{
			System.out.println("wakelock still held, releasing");
			wakeLock.release();
		}

		if (wifiLock.isHeld())
		{
			System.out.println("wifilock still held, releasing");
			wifiLock.release();
		}

		super.onDestroy();
	}

	public Transaction sendCoins(final Address to, final BigInteger amount, final BigInteger fee)
	{
		final Wallet wallet = application.getWallet();
		final SendRequest sendRequest = SendRequest.to(to, amount);
		sendRequest.fee = fee;

		final Transaction tx;
		if (peerGroup != null)
			tx = wallet.sendCoins(peerGroup, sendRequest).tx;
		else
			tx = wallet.sendCoinsOffline(sendRequest);
		return tx;
	}

	public List<Peer> getConnectedPeers()
	{
		if (peerGroup != null)
			return peerGroup.getConnectedPeers();
		else
			return null;
	}

	private void sendBroadcastPeerState(final int numPeers)
	{
		final Intent broadcast = new Intent(ACTION_PEER_STATE);
		broadcast.putExtra(ACTION_PEER_STATE_NUM_PEERS, numPeers);
		sendStickyBroadcast(broadcast);
	}

	private void removeBroadcastPeerState()
	{
		removeStickyBroadcast(new Intent(ACTION_PEER_STATE));
	}

	private void sendBroadcastBlockchainState(final Date chainheadDate, final int chainheadHeight, final int download)
	{
		final Intent broadcast = new Intent(ACTION_BLOCKCHAIN_STATE);
		broadcast.putExtra(ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE, chainheadDate);
		broadcast.putExtra(ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_HEIGHT, chainheadHeight);
		broadcast.putExtra(ACTION_BLOCKCHAIN_STATE_DOWNLOAD, download);

		sendStickyBroadcast(broadcast);
	}

	private void removeBroadcastBlockchainState()
	{
		removeStickyBroadcast(new Intent(ACTION_BLOCKCHAIN_STATE));
	}

	public void notifyWidgets()
	{
		final Context context = getApplicationContext();

		// notify widgets
		final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		for (final AppWidgetProviderInfo providerInfo : appWidgetManager.getInstalledProviders())
		{
			// limit to own widgets
			if (providerInfo.provider.getPackageName().equals(context.getPackageName()))
			{
				final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
				intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetManager.getAppWidgetIds(providerInfo.provider));
				context.sendBroadcast(intent);
			}
		}
	}
}
