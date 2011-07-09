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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.NetworkConnection;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Sha256Hash;
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

/**
 * @author Andreas Schildbach
 */
public class Service extends android.app.Service
{
	private Application application;
	private final List<Peer> peers = new ArrayList<Peer>(Constants.MAX_CONNECTED_PEERS);
	private BlockStore blockStore;
	private BlockChain blockChain;
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

				final String msg = "Received " + Utils.bitcoinValueToFriendlyString(value) + " BTC";
				final Notification notification = new Notification(R.drawable.stat_notify_received, msg, System.currentTimeMillis());
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
				notification.sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received);
				notification.setLatestEventInfo(Service.this, msg, "From " + from,
						PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
				nm.notify(notificationIdCount.getAndIncrement(), notification);
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

		try
		{
			final String blockchainFilename = Constants.TEST ? Constants.BLOCKCHAIN_FILENAME_TEST : Constants.BLOCKCHAIN_FILENAME_PROD;
			final File file = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE), blockchainFilename);

			if (!file.exists())
			{
				// copy snapshot
				try
				{
					final long t = System.currentTimeMillis();

					final String blockchainSnapshotFilename = Constants.TEST ? Constants.BLOCKCHAIN_SNAPSHOT_FILENAME_TEST
							: Constants.BLOCKCHAIN_SNAPSHOT_FILENAME_PROD;
					final InputStream is = new BufferedInputStream(getAssets().open(blockchainSnapshotFilename));
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
					file.delete();
				}
			}

			blockStore = new BoundedOverheadBlockStore(application.getNetworkParameters(), file);

			blockChain = new BlockChain(application.getNetworkParameters(), application.getWallet(), blockStore);
		}
		catch (final BlockStoreException x)
		{
			throw new Error("blockstore cannot be created", x);
		}

		checkPeers();

		application.getWallet().addEventListener(walletEventListener);
	}

	@Override
	public void onDestroy()
	{
		System.out.println("service onDestroy()");

		nm.cancel(NOTIFICATION_ID_CONNECTED);
		nm.cancel(NOTIFICATION_ID_SYNCING);

		application.getWallet().removeEventListener(walletEventListener);

		for (final Peer peer : peers)
			peer.disconnect();

		// cancel background thread
		backgroundThread.getLooper().quit();

		super.onDestroy();
	}

	public void sendTransaction(final Transaction transaction)
	{
		checkPeers();

		broadcastTransaction(transaction);
	}

	private void broadcastTransaction(final Transaction tx)
	{
		final AtomicBoolean alreadyConfirmed = new AtomicBoolean(false);

		for (final Iterator<Peer> i = peers.iterator(); i.hasNext();)
		{
			final Peer peer = i.next();

			backgroundHandler.post(new Runnable()
			{
				public void run()
				{
					try
					{
						System.out.println("broadcasting to " + peer);
						peer.broadcastTransaction(tx);

						handler.post(new Runnable()
						{
							public void run()
							{
								if (!alreadyConfirmed.getAndSet(true))
								{
									application.getWallet().confirmSend(tx);
									application.saveWallet();
								}
							}
						});
					}
					catch (final IOException x)
					{
						x.printStackTrace();
						peer.disconnect();
					}
				}
			});
		}
	}

	private void checkPeers()
	{
		// remove dead peers
		for (final Iterator<Peer> i = peers.iterator(); i.hasNext();)
		{
			final Peer peer = i.next();
			if (!peer.isRunning())
			{
				System.out.println("removing " + peer);
				i.remove();
			}
		}

		if (peers.isEmpty())
			nm.cancel(NOTIFICATION_ID_CONNECTED);

		backgroundHandler.post(new Runnable()
		{
			public void run()
			{
				try
				{
					System.out.println("discovering peers");
					final long t = System.currentTimeMillis();

					final List<InetSocketAddress> peerAddresses = discoverPeers();
					Collections.shuffle(peerAddresses);
					System.out.println(peerAddresses.size() + " peers discovered, took " + (System.currentTimeMillis() - t) + " ms");

					for (final InetSocketAddress peerAddress : peerAddresses)
					{
						if (peers.size() >= Constants.MAX_CONNECTED_PEERS)
							break;

						try
						{
							final NetworkConnection connection = new NetworkConnection(peerAddress.getAddress(), application.getNetworkParameters(),
									blockStore.getChainHead().getHeight(), 5000);

							if (connection != null)
							{
								handler.post(new Runnable()
								{
									public void run()
									{
										final Peer peer = new Peer(application.getNetworkParameters(), connection, blockChain, application
												.getWallet());
										peer.start();

										if (peers.isEmpty())
										{
											// client was unconnected for a while
											blockChainDownload(peer);
										}

										peers.add(peer);

										final String msg = peers.size() + " peers connected";
										System.out.println("Peer " + connection.getRemoteIp().getHostAddress() + " connected, " + msg);

										final Notification notification = new Notification(R.drawable.stat_sys_peers, null, System
												.currentTimeMillis());
										notification.flags |= Notification.FLAG_ONGOING_EVENT;
										notification.iconLevel = peers.size() > 4 ? 4 : peers.size();
										notification.setLatestEventInfo(Service.this, "Bitcoin Wallet", msg,
												PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
										nm.notify(NOTIFICATION_ID_CONNECTED, notification);
									}
								});
							}
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

					// send pending transactions
					handler.post(new Runnable()
					{
						public void run()
						{
							if (!peers.isEmpty())
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
				catch (final BlockStoreException x)
				{
					throw new RuntimeException(x);
				}
			}

			private List<InetSocketAddress> discoverPeers()
			{
				try
				{
					final PeerDiscovery peerDiscovery = Constants.TEST ? new IrcDiscovery(Constants.PEER_DISCOVERY_IRC_CHANNEL_TEST)
							: new DnsDiscovery(application.getNetworkParameters());

					return Arrays.asList(peerDiscovery.getPeers());
				}
				catch (final PeerDiscoveryException x)
				{
					x.printStackTrace();
					return new LinkedList<InetSocketAddress>();
				}
			}
		});
	}

	private void blockChainDownload(final Peer peer)
	{
		try
		{
			final CountDownLatch latch = peer.startBlockChainDownload();

			if (latch != null)
			{
				System.out.println("sync");

				new Thread()
				{
					@Override
					public void run()
					{
						try
						{
							final long maxCount = latch.getCount();

							while (true)
							{
								latch.await(1, TimeUnit.SECONDS);

								final long count = latch.getCount();
								if (count == 0)
								{
									handler.post(new Runnable()
									{
										public void run()
										{
											System.out.println("sync finished");
											nm.cancel(NOTIFICATION_ID_SYNCING);
										}
									});
									return;
								}
								else
								{
									final float percent = 100f - (100f * (count / (float) maxCount));

									handler.post(new Runnable()
									{
										public void run()
										{
											final Notification notification = new Notification(R.drawable.stat_notify_sync,
													"Bitcoin Blockchain Sync started", System.currentTimeMillis());
											notification.flags |= Notification.FLAG_ONGOING_EVENT;
											notification.iconLevel = (int) (count % 2l);
											notification.setLatestEventInfo(Service.this, "Bitcoin Blockchain Sync",
													String.format("%.1f%% finished", percent),
													PendingIntent.getActivity(Service.this, 0, new Intent(Service.this, WalletActivity.class), 0));
											nm.notify(NOTIFICATION_ID_SYNCING, notification);
										}
									});
								}
							}
						}
						catch (final Exception x)
						{
							x.printStackTrace();
						}
					}
				}.start(); // FIXME no graceful shutdown possible
			}
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}
}
