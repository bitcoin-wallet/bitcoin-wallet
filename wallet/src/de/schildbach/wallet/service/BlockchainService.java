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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.listeners.AbstractPeerDataEventListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.net.discovery.MultiplexingDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.data.TimeLiveData;
import de.schildbach.wallet.data.WalletBalanceLiveData;
import de.schildbach.wallet.service.BlockchainState.Impediment;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.arch.lifecycle.LifecycleService;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;

/**
 * @author Andreas Schildbach
 */
public class BlockchainService extends LifecycleService {
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
    private ImpedimentsLiveData impediments;
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
    private static final long BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;

    public static final String ACTION_PEER_STATE = BlockchainService.class.getPackage().getName() + ".peer_state";
    public static final String ACTION_PEER_STATE_NUM_PEERS = "num_peers";

    public static final String ACTION_BLOCKCHAIN_STATE = BlockchainService.class.getPackage().getName()
            + ".blockchain_state";

    private static final String ACTION_CANCEL_COINS_RECEIVED = BlockchainService.class.getPackage().getName()
            + ".cancel_coins_received";
    private static final String ACTION_RESET_BLOCKCHAIN = BlockchainService.class.getPackage().getName()
            + ".reset_blockchain";
    private static final String ACTION_BROADCAST_TRANSACTION = BlockchainService.class.getPackage().getName()
            + ".broadcast_transaction";
    private static final String ACTION_BROADCAST_TRANSACTION_HASH = "hash";

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    public static void start(final Context context, final boolean cancelCoinsReceived) {
        if (cancelCoinsReceived)
            context.startService(
                    new Intent(BlockchainService.ACTION_CANCEL_COINS_RECEIVED, null, context, BlockchainService.class));
        else
            context.startService(new Intent(context, BlockchainService.class));
    }

    public static void stop(final Context context) {
        context.stopService(new Intent(context, BlockchainService.class));
    }

    public static void scheduleStart(final WalletApplication application) {
        final Configuration config = application.getConfiguration();
        final long lastUsedAgo = config.getLastUsedAgo();

        // apply some backoff
        final long alarmInterval;
        if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_JUST_MS)
            alarmInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        else if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_RECENTLY_MS)
            alarmInterval = AlarmManager.INTERVAL_HALF_DAY;
        else
            alarmInterval = AlarmManager.INTERVAL_DAY;

        log.info("last used {} minutes ago, rescheduling blockchain sync in roughly {} minutes",
                lastUsedAgo / DateUtils.MINUTE_IN_MILLIS, alarmInterval / DateUtils.MINUTE_IN_MILLIS);

        final AlarmManager alarmManager = (AlarmManager) application.getSystemService(Context.ALARM_SERVICE);
        final PendingIntent alarmIntent = PendingIntent.getService(application, 0,
                new Intent(application, BlockchainService.class), 0);
        alarmManager.cancel(alarmIntent);

        // workaround for no inexact set() before KitKat
        final long now = System.currentTimeMillis();
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, now + alarmInterval, AlarmManager.INTERVAL_DAY,
                alarmIntent);
    }

    public static void resetBlockchain(final Context context) {
        // implicitly stops blockchain service
        context.startService(
                new Intent(BlockchainService.ACTION_RESET_BLOCKCHAIN, null, context, BlockchainService.class));
    }

    public static void broadcastTransaction(final Context context, final Transaction tx) {
        final Intent intent = new Intent(BlockchainService.ACTION_BROADCAST_TRANSACTION, null, context,
                BlockchainService.class);
        intent.putExtra(BlockchainService.ACTION_BROADCAST_TRANSACTION_HASH, tx.getHash().getBytes());
        context.startService(intent);
    }

    private static class NewTransactionLiveData extends LiveData<Transaction> {
        private final Wallet wallet;

        public NewTransactionLiveData(final WalletApplication application) {
            this.wallet = application.getWallet();
        }

        @Override
        protected void onActive() {
            wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletListener);
        }

        @Override
        protected void onInactive() {
            wallet.removeCoinsSentEventListener(walletListener);
            wallet.removeCoinsReceivedEventListener(walletListener);
        }

        private final WalletListener walletListener = new WalletListener();

        private class WalletListener implements WalletCoinsReceivedEventListener, WalletCoinsSentEventListener {
            @Override
            public void onCoinsReceived(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                postValue(tx);
            }

            @Override
            public void onCoinsSent(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                postValue(tx);
            }
        }
    }

    private void notifyCoinsReceived(@Nullable final Address address, final Coin amount,
            final Sha256Hash transactionHash) {
        notificationCount++;
        notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
        if (address != null && !notificationAddresses.contains(address))
            notificationAddresses.add(address);

        final MonetaryFormat btcFormat = config.getFormat();
        final String packageFlavor = application.applicationPackageFlavor();
        final String msgSuffix = packageFlavor != null ? " [" + packageFlavor + "]" : "";

        // summary notification
        final NotificationCompat.Builder summaryNotification = new NotificationCompat.Builder(this,
                Constants.NOTIFICATION_CHANNEL_ID_RECEIVED);
        summaryNotification.setGroup(Constants.NOTIFICATION_GROUP_KEY_RECEIVED);
        summaryNotification.setGroupSummary(true);
        summaryNotification.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);
        summaryNotification.setWhen(System.currentTimeMillis());
        summaryNotification.setSmallIcon(R.drawable.stat_notify_received_24dp);
        summaryNotification.setContentTitle(
                getString(R.string.notification_coins_received_msg, btcFormat.format(notificationAccumulatedAmount))
                        + msgSuffix);
        if (!notificationAddresses.isEmpty()) {
            final StringBuilder text = new StringBuilder();
            for (final Address notificationAddress : notificationAddresses) {
                if (text.length() > 0)
                    text.append(", ");
                final String addressStr = notificationAddress.toBase58();
                final String label = AddressBookProvider.resolveLabel(getApplicationContext(), addressStr);
                text.append(label != null ? label : addressStr);
            }
            summaryNotification.setContentText(text);
        }
        summaryNotification
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
        nm.notify(Constants.NOTIFICATION_ID_COINS_RECEIVED, summaryNotification.build());

        // child notification
        final NotificationCompat.Builder childNotification = new NotificationCompat.Builder(this,
                Constants.NOTIFICATION_CHANNEL_ID_RECEIVED);
        childNotification.setGroup(Constants.NOTIFICATION_GROUP_KEY_RECEIVED);
        childNotification.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);
        childNotification.setWhen(System.currentTimeMillis());
        childNotification.setSmallIcon(R.drawable.stat_notify_received_24dp);
        final String msg = getString(R.string.notification_coins_received_msg, btcFormat.format(amount)) + msgSuffix;
        childNotification.setTicker(msg);
        childNotification.setContentTitle(msg);
        if (address != null) {
            final String addressStr = address.toBase58();
            final String addressLabel = AddressBookProvider.resolveLabel(getApplicationContext(), addressStr);
            if (addressLabel != null)
                childNotification.setContentText(addressLabel);
            else
                childNotification.setContentText(addressStr);
        }
        childNotification
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
        childNotification.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received));
        nm.notify(transactionHash.toString(), Constants.NOTIFICATION_ID_COINS_RECEIVED, childNotification.build());
    }

    private final class PeerConnectivityListener
            implements PeerConnectedEventListener, PeerDisconnectedEventListener, OnSharedPreferenceChangeListener {
        private int peerCount;
        private AtomicBoolean stopped = new AtomicBoolean(false);

        public PeerConnectivityListener() {
            config.registerOnSharedPreferenceChangeListener(this);
        }

        public void stop() {
            stopped.set(true);

            config.unregisterOnSharedPreferenceChangeListener(this);

            nm.cancel(Constants.NOTIFICATION_ID_CONNECTED);
        }

        @Override
        public void onPeerConnected(final Peer peer, final int peerCount) {
            this.peerCount = peerCount;
            changed(peerCount);
        }

        @Override
        public void onPeerDisconnected(final Peer peer, final int peerCount) {
            this.peerCount = peerCount;
            changed(peerCount);
        }

        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
            if (Configuration.PREFS_KEY_CONNECTIVITY_NOTIFICATION.equals(key))
                changed(peerCount);
        }

        private void changed(final int numPeers) {
            if (stopped.get())
                return;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    final boolean connectivityNotificationEnabled = config.getConnectivityNotificationEnabled();

                    if (!connectivityNotificationEnabled || numPeers == 0) {
                        stopForeground(true);
                    } else {
                        final NotificationCompat.Builder notification = new NotificationCompat.Builder(
                                BlockchainService.this, Constants.NOTIFICATION_CHANNEL_ID_ONGOING);
                        notification.setSmallIcon(R.drawable.stat_notify_peers, Math.min(numPeers, 4));
                        notification.setContentTitle(getString(R.string.app_name));
                        notification.setContentText(getString(R.string.notification_peers_connected_msg, numPeers));
                        notification.setContentIntent(PendingIntent.getActivity(BlockchainService.this, 0,
                                new Intent(BlockchainService.this, WalletActivity.class), 0));
                        notification.setWhen(System.currentTimeMillis());
                        notification.setOngoing(true);
                        startForeground(Constants.NOTIFICATION_ID_CONNECTED, notification.build());
                    }

                    // send broadcast
                    broadcastPeerState(numPeers);
                }
            });
        }
    }

    private final PeerDataEventListener blockchainDownloadListener = new AbstractPeerDataEventListener() {
        private final AtomicLong lastMessageTime = new AtomicLong(0);

        @Override
        public void onBlocksDownloaded(final Peer peer, final Block block, final FilteredBlock filteredBlock,
                final int blocksLeft) {
            delayHandler.removeCallbacksAndMessages(null);

            final long now = System.currentTimeMillis();
            if (now - lastMessageTime.get() > BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS)
                delayHandler.post(runnable);
            else
                delayHandler.postDelayed(runnable, BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS);
        }

        private final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                lastMessageTime.set(System.currentTimeMillis());

                config.maybeIncrementBestChainHeightEver(blockChain.getChainHead().getHeight());
                broadcastBlockchainState();
            }
        };
    };

    private static class ImpedimentsLiveData extends LiveData<Set<Impediment>> {
        private final WalletApplication application;
        private final ConnectivityManager connectivityManager;
        private final Set<Impediment> impediments = EnumSet.noneOf(Impediment.class);

        public ImpedimentsLiveData(final WalletApplication application) {
            this.application = application;
            this.connectivityManager = (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
            setValue(impediments);
        }

        @Override
        protected void onActive() {
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
            // implicitly start PeerGroup
            final Intent intent = application.registerReceiver(connectivityReceiver, intentFilter);
            if (intent != null)
                handleIntent(intent);
        }

        @Override
        protected void onInactive() {
            application.unregisterReceiver(connectivityReceiver);
        }

        private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                handleIntent(intent);
            }
        };

        private void handleIntent(final Intent intent) {
            final String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                final boolean hasConnectivity = networkInfo != null && networkInfo.isConnected();
                if (hasConnectivity)
                    impediments.remove(Impediment.NETWORK);
                else
                    impediments.add(Impediment.NETWORK);

                if (log.isInfoEnabled()) {
                    final StringBuilder s = new StringBuilder("active network is ")
                            .append(hasConnectivity ? "up" : "down");
                    if (networkInfo != null) {
                        s.append(", type: ").append(networkInfo.getTypeName());
                        s.append(", state: ").append(networkInfo.getState()).append('/')
                                .append(networkInfo.getDetailedState());
                        final String extraInfo = networkInfo.getExtraInfo();
                        if (extraInfo != null)
                            s.append(", extraInfo: ").append(extraInfo);
                        final String reason = networkInfo.getReason();
                        if (reason != null)
                            s.append(", reason: ").append(reason);
                    }
                    log.info(s.toString());
                }
            } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
                impediments.add(Impediment.STORAGE);
                log.info("device storage low");
            } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                impediments.remove(Impediment.STORAGE);
                log.info("device storage ok");
            }
            setValue(impediments);
        }
    }

    public class LocalBinder extends Binder {
        public BlockchainService getService() {
            return BlockchainService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(final Intent intent) {
        log.debug(".onBind()");

        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        log.debug(".onUnbind()");

        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        serviceCreatedAt = System.currentTimeMillis();
        log.debug(".onCreate()");

        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        application = (WalletApplication) getApplication();
        config = application.getConfiguration();
        final Wallet wallet = application.getWallet();

        peerConnectivityListener = new PeerConnectivityListener();

        broadcastPeerState(0);

        blockChainFile = new File(getDir("blockstore", Context.MODE_PRIVATE), Constants.Files.BLOCKCHAIN_FILENAME);
        final boolean blockChainFileExists = blockChainFile.exists();

        if (!blockChainFileExists) {
            log.info("blockchain does not exist, resetting wallet");
            wallet.reset();
        }

        try {
            blockStore = new SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile);
            blockStore.getChainHead(); // detect corruptions as early as possible

            final long earliestKeyCreationTime = wallet.getEarliestKeyCreationTime();

            if (!blockChainFileExists && earliestKeyCreationTime > 0) {
                try {
                    final Stopwatch watch = Stopwatch.createStarted();
                    final InputStream checkpointsInputStream = getAssets().open(Constants.Files.CHECKPOINTS_FILENAME);
                    CheckpointManager.checkpoint(Constants.NETWORK_PARAMETERS, checkpointsInputStream, blockStore,
                            earliestKeyCreationTime);
                    watch.stop();
                    log.info("checkpoints loaded from '{}', took {}", Constants.Files.CHECKPOINTS_FILENAME, watch);
                } catch (final IOException x) {
                    log.error("problem reading checkpoints, continuing without", x);
                }
            }
        } catch (final BlockStoreException x) {
            blockChainFile.delete();

            final String msg = "blockstore cannot be created";
            log.error(msg, x);
            throw new Error(msg, x);
        }

        try {
            blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, blockStore);
        } catch (final BlockStoreException x) {
            throw new Error("blockchain cannot be created", x);
        }

        final WalletBalanceLiveData walletBalance = new WalletBalanceLiveData(application);
        walletBalance.observe(this, new Observer<Coin>() {
            @Override
            public void onChanged(final Coin walletBalance) {
                WalletBalanceWidgetProvider.updateWidgets(BlockchainService.this, walletBalance);
            }
        });
        final NewTransactionLiveData newTransaction = new NewTransactionLiveData(application);
        newTransaction.observe(this, new Observer<Transaction>() {
            @Override
            public void onChanged(final Transaction tx) {
                transactionsReceived.incrementAndGet();
                final Coin amount = tx.getValue(wallet);
                if (amount.isPositive()) {
                    final Address address = WalletUtils.getWalletAddressOfReceived(tx, wallet);
                    final ConfidenceType confidenceType = tx.getConfidence().getConfidenceType();
                    final Sha256Hash hash = tx.getHash();
                    final boolean replaying = blockChain.getBestChainHeight() < config.getBestChainHeightEver();
                    final boolean isReplayedTx = confidenceType == ConfidenceType.BUILDING && replaying;
                    if (!isReplayedTx)
                        notifyCoinsReceived(address, amount, hash);
                }
            }
        });
        final TimeLiveData time = new TimeLiveData(application);
        time.observe(this, new Observer<Date>() {
            private int lastChainHeight = 0;
            private final List<ActivityHistoryEntry> activityHistory = new LinkedList<ActivityHistoryEntry>();

            @Override
            public void onChanged(final Date time) {
                final int chainHeight = blockChain.getBestChainHeight();

                if (lastChainHeight > 0) {
                    final int numBlocksDownloaded = chainHeight - lastChainHeight;
                    final int numTransactionsReceived = transactionsReceived.getAndSet(0);

                    // push history
                    activityHistory.add(0, new ActivityHistoryEntry(numTransactionsReceived, numBlocksDownloaded));

                    // trim
                    while (activityHistory.size() > MAX_HISTORY_SIZE)
                        activityHistory.remove(activityHistory.size() - 1);

                    // print
                    final StringBuilder builder = new StringBuilder();
                    for (final ActivityHistoryEntry entry : activityHistory) {
                        if (builder.length() > 0)
                            builder.append(", ");
                        builder.append(entry);
                    }
                    log.info("History of transactions/blocks: " + builder);

                    // determine if block and transaction activity is idling
                    boolean isIdle = false;
                    if (activityHistory.size() >= MIN_COLLECT_HISTORY) {
                        isIdle = true;
                        for (int i = 0; i < activityHistory.size(); i++) {
                            final ActivityHistoryEntry entry = activityHistory.get(i);
                            final boolean blocksActive = entry.numBlocksDownloaded > 0 && i <= IDLE_BLOCK_TIMEOUT_MIN;
                            final boolean transactionsActive = entry.numTransactionsReceived > 0
                                    && i <= IDLE_TRANSACTION_TIMEOUT_MIN;

                            if (blocksActive || transactionsActive) {
                                isIdle = false;
                                break;
                            }
                        }
                    }

                    // if idling, shutdown service
                    if (isIdle) {
                        log.info("idling detected, stopping service");
                        stopSelf();
                    }
                }

                lastChainHeight = chainHeight;
            }

            final class ActivityHistoryEntry {
                public final int numTransactionsReceived;
                public final int numBlocksDownloaded;

                public ActivityHistoryEntry(final int numTransactionsReceived, final int numBlocksDownloaded) {
                    this.numTransactionsReceived = numTransactionsReceived;
                    this.numBlocksDownloaded = numBlocksDownloaded;
                }

                @Override
                public String toString() {
                    return numTransactionsReceived + "/" + numBlocksDownloaded;
                }
            }
        });
        impediments = new ImpedimentsLiveData(application);
        impediments.observe(this, new Observer<Set<Impediment>>() {
            @Override
            public void onChanged(final Set<Impediment> impediments) {
                if (impediments.isEmpty() && peerGroup == null && Constants.ENABLE_BLOCKCHAIN_SYNC)
                    startup();
                else if (!impediments.isEmpty() && peerGroup != null)
                    shutdown();
                broadcastBlockchainState();
            }

            private void startup() {
                log.debug("acquiring wakelock");
                wakeLock.acquire();

                // consistency check
                final int walletLastBlockSeenHeight = wallet.getLastBlockSeenHeight();
                final int bestChainHeight = blockChain.getBestChainHeight();
                if (walletLastBlockSeenHeight != -1 && walletLastBlockSeenHeight != bestChainHeight) {
                    final String message = "wallet/blockchain out of sync: " + walletLastBlockSeenHeight + "/"
                            + bestChainHeight;
                    log.error(message);
                    CrashReporter.saveBackgroundTrace(new RuntimeException(message), application.packageInfo());
                }

                log.info("starting peergroup");
                peerGroup = new PeerGroup(Constants.NETWORK_PARAMETERS, blockChain);
                peerGroup.setDownloadTxDependencies(0); // recursive implementation causes StackOverflowError
                peerGroup.addWallet(wallet);
                peerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
                peerGroup.addConnectedEventListener(peerConnectivityListener);
                peerGroup.addDisconnectedEventListener(peerConnectivityListener);

                final int maxConnectedPeers = application.maxConnectedPeers();

                final String trustedPeerHost = config.getTrustedPeerHost();
                final boolean hasTrustedPeer = trustedPeerHost != null;

                final boolean connectTrustedPeerOnly = hasTrustedPeer && config.getTrustedPeerOnly();
                peerGroup.setMaxConnections(connectTrustedPeerOnly ? 1 : maxConnectedPeers);
                peerGroup.setConnectTimeoutMillis(Constants.PEER_TIMEOUT_MS);
                peerGroup.setPeerDiscoveryTimeoutMillis(Constants.PEER_DISCOVERY_TIMEOUT_MS);

                peerGroup.addPeerDiscovery(new PeerDiscovery() {
                    private final PeerDiscovery normalPeerDiscovery = MultiplexingDiscovery
                            .forServices(Constants.NETWORK_PARAMETERS, 0);

                    @Override
                    public InetSocketAddress[] getPeers(final long services, final long timeoutValue,
                            final TimeUnit timeoutUnit) throws PeerDiscoveryException {
                        final List<InetSocketAddress> peers = new LinkedList<InetSocketAddress>();

                        boolean needsTrimPeersWorkaround = false;

                        if (hasTrustedPeer) {
                            log.info(
                                    "trusted peer '" + trustedPeerHost + "'" + (connectTrustedPeerOnly ? " only" : ""));

                            final InetSocketAddress addr = new InetSocketAddress(trustedPeerHost,
                                    Constants.NETWORK_PARAMETERS.getPort());
                            if (addr.getAddress() != null) {
                                peers.add(addr);
                                needsTrimPeersWorkaround = true;
                            }
                        }

                        if (!connectTrustedPeerOnly)
                            peers.addAll(
                                    Arrays.asList(normalPeerDiscovery.getPeers(services, timeoutValue, timeoutUnit)));

                        // workaround because PeerGroup will shuffle peers
                        if (needsTrimPeersWorkaround)
                            while (peers.size() >= maxConnectedPeers)
                                peers.remove(peers.size() - 1);

                        return peers.toArray(new InetSocketAddress[0]);
                    }

                    @Override
                    public void shutdown() {
                        normalPeerDiscovery.shutdown();
                    }
                });

                // start peergroup
                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(blockchainDownloadListener);
            }

            private void shutdown() {
                log.info("stopping peergroup");
                peerGroup.removeDisconnectedEventListener(peerConnectivityListener);
                peerGroup.removeConnectedEventListener(peerConnectivityListener);
                peerGroup.removeWallet(wallet);
                peerGroup.stopAsync();
                peerGroup = null;

                log.debug("releasing wakelock");
                wakeLock.release();
            }
        });
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            log.info("service start command: " + intent + (intent.hasExtra(Intent.EXTRA_ALARM_COUNT)
                    ? " (alarm count: " + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) + ")" : ""));

            final String action = intent.getAction();

            if (BlockchainService.ACTION_CANCEL_COINS_RECEIVED.equals(action)) {
                notificationCount = 0;
                notificationAccumulatedAmount = Coin.ZERO;
                notificationAddresses.clear();

                nm.cancel(Constants.NOTIFICATION_ID_COINS_RECEIVED);
            } else if (BlockchainService.ACTION_RESET_BLOCKCHAIN.equals(action)) {
                log.info("will remove blockchain on service shutdown");

                resetBlockchainOnShutdown = true;
                stopSelf();
            } else if (BlockchainService.ACTION_BROADCAST_TRANSACTION.equals(action)) {
                final Sha256Hash hash = Sha256Hash
                        .wrap(intent.getByteArrayExtra(BlockchainService.ACTION_BROADCAST_TRANSACTION_HASH));
                final Transaction tx = application.getWallet().getTransaction(hash);

                if (peerGroup != null) {
                    log.info("broadcasting transaction " + tx.getHashAsString());
                    peerGroup.broadcastTransaction(tx);
                } else {
                    log.info("peergroup not available, not broadcasting transaction " + tx.getHashAsString());
                }
            }
        } else {
            log.warn("service restart, although it was started as non-sticky");
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        log.debug(".onDestroy()");

        scheduleStart(application);

        if (peerGroup != null) {
            peerGroup.removeDisconnectedEventListener(peerConnectivityListener);
            peerGroup.removeConnectedEventListener(peerConnectivityListener);
            peerGroup.removeWallet(application.getWallet());
            peerGroup.stop();

            log.info("peergroup stopped");
        }

        peerConnectivityListener.stop();

        delayHandler.removeCallbacksAndMessages(null);

        try {
            blockStore.close();
        } catch (final BlockStoreException x) {
            throw new RuntimeException(x);
        }

        application.saveWallet();

        if (wakeLock.isHeld()) {
            log.debug("wakelock still held, releasing");
            wakeLock.release();
        }

        if (resetBlockchainOnShutdown) {
            log.info("removing blockchain");
            blockChainFile.delete();
        }

        stopForeground(true);

        super.onDestroy();

        log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
    }

    @Override
    public void onTrimMemory(final int level) {
        log.info("onTrimMemory({}) called", level);

        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            log.warn("low memory detected, stopping service");
            stopSelf();
        }
    }

    public BlockchainState getBlockchainState() {
        final StoredBlock chainHead = blockChain.getChainHead();
        final Date bestChainDate = chainHead.getHeader().getTime();
        final int bestChainHeight = chainHead.getHeight();
        final boolean replaying = chainHead.getHeight() < config.getBestChainHeightEver();

        return new BlockchainState(bestChainDate, bestChainHeight, replaying, impediments.getValue());
    }

    public List<Peer> getConnectedPeers() {
        if (peerGroup != null)
            return peerGroup.getConnectedPeers();
        else
            return null;
    }

    public List<StoredBlock> getRecentBlocks(final int maxBlocks) {
        final List<StoredBlock> blocks = new ArrayList<StoredBlock>(maxBlocks);

        try {
            StoredBlock block = blockChain.getChainHead();

            while (block != null) {
                blocks.add(block);

                if (blocks.size() >= maxBlocks)
                    break;

                block = block.getPrev(blockStore);
            }
        } catch (final BlockStoreException x) {
            // swallow
        }

        return blocks;
    }

    private void broadcastPeerState(final int numPeers) {
        final Intent broadcast = new Intent(ACTION_PEER_STATE);
        broadcast.putExtra(ACTION_PEER_STATE_NUM_PEERS, numPeers);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private void broadcastBlockchainState() {
        final Intent broadcast = new Intent(ACTION_BLOCKCHAIN_STATE);
        getBlockchainState().putExtras(broadcast);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }
}
