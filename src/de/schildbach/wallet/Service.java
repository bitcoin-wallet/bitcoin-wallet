package de.schildbach.wallet;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkConnection;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.discovery.IrcDiscovery;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.discovery.PeerDiscoveryException;
import com.google.bitcoin.store.BlockStoreException;

public class Service extends android.app.Service
{
	private Application application;
	private Peer peer;
	private NetworkConnection connection;

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
		public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			try
			{
				final TransactionInput input = tx.getInputs().get(0);
				final Address from = input.getFromAddress();
				final BigInteger value = tx.getValueSentToMe(w);

				System.out.println("!!!!!!!!!!!!! got bitcoins: " + from + " " + value + " " + Thread.currentThread().getName());

				handler.post(new Runnable()
				{
					public void run()
					{
						final String msg = "Received " + Utils.bitcoinValueToFriendlyString(value) + " BTC";
						final Notification notification = new Notification(R.drawable.stat_notify_received, msg, System.currentTimeMillis());
						notification.flags |= Notification.FLAG_AUTO_CANCEL;
						notification.defaults |= Notification.DEFAULT_SOUND;
						notification.setLatestEventInfo(Service.this, msg, "From " + from,
								PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
						nm.notify(notificationIdCount.getAndIncrement(), notification);
					}
				});
			}
			catch (final ScriptException x)
			{
				throw new RuntimeException(x);
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

		// background thread
		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				try
				{
					final PeerDiscovery peerDiscovery = new IrcDiscovery(Constants.PEER_DISCOVERY_IRC_CHANNEL);

					final List<InetSocketAddress> peers = Arrays.asList(peerDiscovery.getPeers());
					Collections.shuffle(peers);

					for (final InetSocketAddress inetSocketAddress : peers)
					{
						try
						{
							connection = new NetworkConnection(inetSocketAddress.getAddress(), Constants.NETWORK_PARAMS, application.getBlockStore()
									.getChainHead().getHeight(), 5000);
							break;
						}
						catch (final IOException x)
						{
							System.out.println(x);
						}
						catch (final ProtocolException x)
						{
							System.out.println(x);
						}
					}

					if (connection != null)
					{
						handler.post(new Runnable()
						{
							public void run()
							{
								peer = new Peer(Constants.NETWORK_PARAMS, connection, application.getBlockChain());
								peer.start();

								final String msg = "Peer " + connection.getRemoteIp().getHostAddress() + " connected";
								final Notification notification = new Notification(android.R.drawable.stat_notify_error, msg, System
										.currentTimeMillis());
								notification.flags |= Notification.FLAG_ONGOING_EVENT;
								notification.setLatestEventInfo(Service.this, "Bitcoin Wallet", msg,
										PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
								nm.notify(NOTIFICATION_ID_CONNECTED, notification);

								sync();
							}
						});
					}
				}
				catch (final PeerDiscoveryException x)
				{
					throw new RuntimeException(x);
				}
				catch (final BlockStoreException x)
				{
					throw new RuntimeException(x);
				}
			}
		});

		application.getWallet().addEventListener(walletEventListener);
	}

	@Override
	public void onDestroy()
	{
		System.out.println("service onDestroy()");

		nm.cancel(NOTIFICATION_ID_CONNECTED);
		nm.cancel(NOTIFICATION_ID_SYNCING);

		application.getWallet().removeEventListener(walletEventListener);

		if (peer != null)
		{
			peer.disconnect();
			peer = null;
		}

		// cancel background thread
		backgroundThread.getLooper().quit();

		super.onDestroy();
	}

	public void sync()
	{
		System.out.println("sync");

		final Notification notification = new Notification(android.R.drawable.stat_notify_sync, "Blockchain sync started", System.currentTimeMillis());
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.setLatestEventInfo(this, "Bitcoin Wallet", "Blockchain sync running",
				PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
		nm.notify(NOTIFICATION_ID_SYNCING, notification);

		try
		{
			final CountDownLatch latch = peer.startBlockChainDownload();

			new Thread()
			{
				@Override
				public void run()
				{
					try
					{
						latch.await();

						nm.cancel(NOTIFICATION_ID_SYNCING);
					}
					catch (final Exception x)
					{
						x.printStackTrace();
					}
				}
			}.start(); // FIXME no graceful shutdown possible
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	public Transaction sendCoins(final Address receivingAddress, final BigInteger amount) throws IOException
	{
		System.out.println("about to send " + amount + " (BTC " + Utils.bitcoinValueToFriendlyString(amount) + ") to " + receivingAddress);

		return application.getWallet().sendCoins(peer, receivingAddress, amount);
	}
}
