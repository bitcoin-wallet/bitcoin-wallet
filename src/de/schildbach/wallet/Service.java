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

package de.schildbach.wallet;

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

import android.app.Notification;
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
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;

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
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.IrcDiscovery;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.discovery.PeerDiscoveryException;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.BoundedOverheadBlockStore;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class Service extends android.app.Service
{
	public static final String ACTION_PEER_STATE = Service.class.getName() + ".peer_state";
	public static final String ACTION_PEER_STATE_NUM_PEERS = "num_peers";

	public static final String ACTION_BLOCKCHAIN_STATE = Service.class.getName() + ".blockchain_state";
	public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE = "best_chain_date";
	public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_HEIGHT = "best_chain_height";
	public static final String ACTION_BLOCKCHAIN_STATE_DOWNLOAD = "download";
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK = 0;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM = 1;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM = 2;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM = 4;

	private Application application;
	private SharedPreferences prefs;

	private BlockStore blockStore;
	private BlockChain blockChain;
	private PeerGroup peerGroup;

	private final Handler handler = new Handler();

	private NotificationManager nm;
	private static final int NOTIFICATION_ID_CONNECTED = 0;
	private static final int NOTIFICATION_ID_COINS_RECEIVED = 1;

	private int notificationCount = 0;
	private BigInteger notificationAccumulatedAmount;
	private final List<Address> notificationAddresses = new LinkedList<Address>();

	private final WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{
		@Override
		public void onCoinsReceived(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			onReceived(wallet, tx);
		}

		private void onReceived(final Wallet wallet, final Transaction tx)
		{
			try
			{
				final TransactionInput input = tx.getInputs().get(0);
				final Address from = input.getFromAddress();
				final BigInteger amount = tx.amount(wallet);

				handler.post(new Runnable()
				{
					public void run()
					{
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
		notificationCount++;
		notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
		if (from != null && !notificationAddresses.contains(from))
			notificationAddresses.add(from);

		final String msg = getString(R.string.notification_coins_received_msg, Utils.bitcoinValueToFriendlyString(notificationAccumulatedAmount))
				+ (Constants.TEST ? " [testnet]" : "");

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

		final Notification notification = new Notification(R.drawable.stat_notify_received, msg, System.currentTimeMillis());
		notification.sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received);
		notification.setLatestEventInfo(Service.this, msg, text,
				PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
		notification.number = notificationCount;

		nm.notify(NOTIFICATION_ID_COINS_RECEIVED, notification);
	}

	public void cancelCoinsReceived()
	{
		notificationCount = 0;
		notificationAccumulatedAmount = BigInteger.ZERO;
		notificationAddresses.clear();

		nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);
	}

	private final PeerEventListener peerEventListener = new AbstractPeerEventListener()
	{
		@Override
		public void onPeerConnected(final Peer peer, final int peerCount)
		{
			changed(peerCount);
		}

		@Override
		public void onPeerDisconnected(final Peer peer, final int peerCount)
		{
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
						final String msg = getString(R.string.notification_peers_connected_msg, numPeers);
						System.out.println("Peer connected, " + msg);

						final Notification notification = new Notification(R.drawable.stat_sys_peers, null, 0);
						notification.flags |= Notification.FLAG_ONGOING_EVENT;
						notification.iconLevel = numPeers > 4 ? 4 : numPeers;
						notification.setLatestEventInfo(Service.this, getString(R.string.app_name) + (Constants.TEST ? " [testnet]" : ""), msg,
								PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
						nm.notify(NOTIFICATION_ID_CONNECTED, notification);
					}

					// send broadcast
					sendBroadcastPeerState(numPeers);
				}
			});
		}
	};

	private final PeerEventListener blockchainDownloadListener = new AbstractPeerEventListener()
	{
		@Override
		public void onBlocksDownloaded(final Peer peer, final Block block, final int blocksLeft)
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					final Date bestChainDate = new Date(blockChain.getChainHead().getHeader().getTimeSeconds() * 1000);
					final int bestChainHeight = blockChain.getBestChainHeight();

					sendBroadcastBlockchainState(bestChainDate, bestChainHeight, ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
				}
			});
		}
	};

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
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
			}

			final Date bestChainDate = new Date(blockChain.getChainHead().getHeader().getTimeSeconds() * 1000);
			final int bestChainHeight = blockChain.getBestChainHeight();
			final int download = (hasConnectivity ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM)
					| (hasPower ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM)
					| (hasStorage ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM);

			sendBroadcastBlockchainState(bestChainDate, bestChainHeight, download);
		}
	};

	public class LocalBinder extends Binder
	{
		public Service getService()
		{
			return Service.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(final Intent intent)
	{
		return mBinder;
	}

	@Override
	public void onCreate()
	{
		System.out.println("service onCreate()");

		super.onCreate();

		nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		application = (Application) getApplication();
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final Wallet wallet = application.getWallet();

		sendBroadcastPeerState(0);

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
		registerReceiver(broadcastReceiver, intentFilter);

		final int versionCode = application.applicationVersionCode();
		final int lastVersionCode = prefs.getInt(Constants.PREFS_KEY_LAST_VERSION, 0);
		final boolean blockchainNeedsRescan = lastVersionCode <= 23 && versionCode > 23;

		final String initiateReset = prefs.getString(Constants.PREFS_KEY_INITIATE_RESET, null);
		final boolean blockchainResetInitiated;
		if ("blockchain".equals(initiateReset))
		{
			blockchainResetInitiated = true;
			wallet.clearTransactions(0);
			application.saveWallet();
		}
		else
		{
			blockchainResetInitiated = false;
		}
		prefs.edit().putInt(Constants.PREFS_KEY_LAST_VERSION, versionCode).remove(Constants.PREFS_KEY_INITIATE_RESET).commit();

		final File file = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE), Constants.BLOCKCHAIN_FILENAME);
		final boolean blockchainDoesNotExist = !file.exists() || file.length() < Constants.BLOCKCHAIN_SNAPSHOT_COPY_THRESHOLD;

		if (blockchainResetInitiated || blockchainNeedsRescan || blockchainDoesNotExist)
			copyBlockchainSnapshot(file);

		try
		{
			try
			{
				blockStore = new BoundedOverheadBlockStore(Constants.NETWORK_PARAMETERS, file);
				blockStore.getChainHead(); // detect corruptions as early as possible
			}
			catch (final BlockStoreException x)
			{
				x.printStackTrace();

				copyBlockchainSnapshot(file);
				blockStore = new BoundedOverheadBlockStore(Constants.NETWORK_PARAMETERS, file);
			}

			blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, blockStore);

			application.getWallet().addEventListener(walletEventListener);
		}
		catch (final BlockStoreException x)
		{
			throw new Error("blockstore cannot be created", x);
		}
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
		System.out.println("service onDestroy()");

		application.getWallet().removeEventListener(walletEventListener);

		if (peerGroup != null)
		{
			peerGroup.removeEventListener(peerEventListener);
			peerGroup.stop();
		}

		unregisterReceiver(broadcastReceiver);

		removeBroadcastPeerState();
		removeBroadcastBlockchainState();

		handler.postDelayed(new Runnable()
		{
			public void run()
			{
				nm.cancel(NOTIFICATION_ID_CONNECTED);
			}
		}, 2000);

		super.onDestroy();
	}

	public Transaction sendCoins(final Address to, final BigInteger amount, final BigInteger fee)
	{
		final Wallet wallet = application.getWallet();
		try
		{
			final Transaction tx = wallet.sendCoinsAsync(peerGroup, to, amount, fee);
			application.saveWallet();
			return tx;
		}
		catch (final IOException x)
		{
			x.printStackTrace();
			return null;
		}
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
