/*
 * Copyright 2010 the original author or authors.
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;

import com.google.bitcoin.core.AbstractPeerEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.DownloadListener;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.PeerAddress;
import com.google.bitcoin.core.PeerEventListener;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.IrcDiscovery;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.BoundedOverheadBlockStore;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class Service extends android.app.Service
{
	private Application application;
	private SharedPreferences prefs;

	private BlockStore blockStore;
	private BlockChain blockChain;
	private PeerGroup peerGroup;
	private List<Sha256Hash> transactionsSeen = new ArrayList<Sha256Hash>();

	private HandlerThread backgroundThread;
	private Handler backgroundHandler;
	private final Handler handler = new Handler();

	private NotificationManager nm;
	private static final int NOTIFICATION_ID_CONNECTED = 0;
	private static final int NOTIFICATION_ID_SYNCING = 1;
	private static final AtomicInteger notificationIdCount = new AtomicInteger(10);

	private final WalletEventListener walletEventListener = new WalletEventListener()
	{
		@Override
		public void onPendingCoinsReceived(final Wallet wallet, final Transaction tx)
		{
			try
			{
				final TransactionInput input = tx.getInputs().get(0);
				final Address from = input.getFromAddress();
				final BigInteger value = tx.getValueSentToMe(wallet);

				handler.post(new Runnable()
				{
					public void run()
					{
						System.out.println("!!! got pending bitcoins: " + from + " " + value);

						notifyTransaction(tx.getHash(), from, value);
						notifyWidgets();
					}
				});
			}
			catch (final ScriptException x)
			{
				throw new RuntimeException(x);
			}
		}

		@Override
		public void onCoinsReceived(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			try
			{
				final TransactionInput input = tx.getInputs().get(0);
				final Address from = input.getFromAddress();
				final BigInteger value = tx.getValueSentToMe(wallet);

				handler.post(new Runnable()
				{
					public void run()
					{
						System.out.println("!!! got confirmed bitcoins: " + from + " " + value);

						notifyTransaction(tx.getHash(), from, value);
						notifyWidgets();
					}
				});
			}
			catch (final ScriptException x)
			{
				throw new RuntimeException(x);
			}
		}

		private void notifyTransaction(final Sha256Hash txHash, final Address from, final BigInteger value)
		{
			if (!transactionsSeen.contains(txHash))
			{
				transactionsSeen.add(txHash);

				final String msg = getString(R.string.notification_coins_received_msg, Utils.bitcoinValueToFriendlyString(value));
				final Notification notification = new Notification(R.drawable.stat_notify_received, msg, System.currentTimeMillis());
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				notification.sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received);
				notification.setLatestEventInfo(Service.this, msg, "From " + from + (Constants.TEST ? " [testnet]" : ""),
						PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
				nm.notify(notificationIdCount.getAndIncrement(), notification);
			}
		}
	};

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

					// send pending transactions, TODO find better time
					if (peerGroup != null && numPeers >= peerGroup.getMaxConnections())
					{
						final Wallet wallet = application.getWallet();
						for (final Transaction transaction : wallet.pending.values())
						{
							if (transaction.sent(wallet))
								broadcastTransaction(transaction);
						}
					}
				}
			});
		}
	};

	private final DownloadListener blockchainDownloadListener = new DownloadListener()
	{
		@Override
		protected void progress(final double percent, final Date date)
		{
			final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(Service.this);
			final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(Service.this);
			final long t = date.getTime();

			handler.post(new Runnable()
			{
				public void run()
				{
					final String eventTitle = getString(R.string.notification_blockchain_sync_started_msg) + (Constants.TEST ? " [testnet]" : "");
					final String eventText = getString(R.string.notification_blockchain_sync_progress_msg, percent,
							DateUtils.isToday(t) ? timeFormat.format(t) : dateFormat.format(t));

					final Notification notification = new Notification(R.drawable.stat_notify_sync, "Bitcoin blockchain sync started", 0);
					notification.flags |= Notification.FLAG_ONGOING_EVENT;
					// notification.iconLevel = blocksLeft % 2;
					notification.setLatestEventInfo(Service.this, eventTitle, eventText,
							PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
					nm.notify(NOTIFICATION_ID_SYNCING, notification);
				}
			});
		}

		@Override
		protected void doneDownload()
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					nm.cancel(NOTIFICATION_ID_SYNCING);

					System.out.println("sync finished");
				}
			});
		}
	};

	private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
			final String reason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
			// final boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);
			System.out.println("network has gone " + (noConnectivity ? "down" : "up") + (reason != null ? ": " + reason : ""));

			if (!noConnectivity && peerGroup == null)
			{
				final Wallet wallet = application.getWallet();
				final NetworkParameters networkParameters = application.getNetworkParameters();

				peerGroup = new PeerGroup(blockStore, networkParameters, blockChain, wallet);
				peerGroup.addEventListener(peerEventListener);

				final String trustedPeerHost = prefs.getString(Constants.PREFS_KEY_TRUSTED_PEER, "").trim();
				if (trustedPeerHost.length() == 0)
				{
					peerGroup.setMaxConnections(Constants.MAX_CONNECTED_PEERS);

					// work around http://code.google.com/p/bitcoinj/issues/detail?id=52
					backgroundHandler.post(new Runnable()
					{
						public void run()
						{
							peerGroup.addPeerDiscovery(Constants.TEST ? new IrcDiscovery(Constants.PEER_DISCOVERY_IRC_CHANNEL_TEST)
									: new DnsDiscovery(networkParameters));
						}
					});
				}
				else
				{
					peerGroup.setMaxConnections(1);

					// work around similar issue as http://code.google.com/p/bitcoinj/issues/detail?id=52
					backgroundHandler.post(new Runnable()
					{
						public void run()
						{
							peerGroup.addAddress(new PeerAddress(new InetSocketAddress(trustedPeerHost, networkParameters.port)));
						}
					});
				}
				peerGroup.start();

				peerGroup.startBlockChainDownload(blockchainDownloadListener);
			}
			else if (noConnectivity && peerGroup != null)
			{
				peerGroup.removeEventListener(peerEventListener);
				peerGroup.stop();
				peerGroup = null;
			}
			else
			{
				System.out.println("ignored event");
			}
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
		final NetworkParameters networkParameters = application.getNetworkParameters();

		// background thread
		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

		final int versionCode = application.versionCode();
		final int lastVersionCode = prefs.getInt(Constants.PREFS_KEY_LAST_VERSION, 0);
		final boolean blockchainNeedsRescan = lastVersionCode <= 23 && versionCode > 23;

		final String initiateReset = prefs.getString(Constants.PREFS_KEY_INITIATE_RESET, null);
		final boolean blockchainResetInitiated;
		if ("transactions".equals(initiateReset))
		{
			blockchainResetInitiated = true;
			wallet.removeAllTransactions();
		}
		else if ("blockchain".equals(initiateReset))
		{
			blockchainResetInitiated = true;
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
				blockStore = new BoundedOverheadBlockStore(networkParameters, file);
				blockStore.getChainHead(); // detect corruptions as early as possible
			}
			catch (final BlockStoreException x)
			{
				x.printStackTrace();

				copyBlockchainSnapshot(file);
				blockStore = new BoundedOverheadBlockStore(networkParameters, file);
			}

			blockChain = new BlockChain(networkParameters, wallet, blockStore);

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

			final String blockchainSnapshotFilename = Constants.TEST ? Constants.BLOCKCHAIN_SNAPSHOT_FILENAME_TEST
					: Constants.BLOCKCHAIN_SNAPSHOT_FILENAME_PROD;
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

		unregisterReceiver(connectivityReceiver);

		backgroundThread.getLooper().quit();

		handler.postDelayed(new Runnable()
		{
			public void run()
			{
				nm.cancel(NOTIFICATION_ID_CONNECTED);
				nm.cancel(NOTIFICATION_ID_SYNCING);
			}
		}, 5000);

		super.onDestroy();
	}

	public void sendTransaction(final Transaction transaction)
	{
		broadcastTransaction(transaction);
	}

	private void broadcastTransaction(final Transaction tx)
	{
		System.out.println("broadcasting transaction: " + tx);

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				if (peerGroup != null)
				{
					final boolean success = peerGroup.broadcastTransaction(tx);
					if (success)
					{
						application.getWallet().confirmSend(tx);
						application.saveWallet();
					}
				}
			}
		});
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
