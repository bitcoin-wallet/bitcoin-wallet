/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.text.format.DateUtils;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.google.common.base.Stopwatch;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.addressbook.AddressBookDao;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.SelectedExchangeRateLiveData;
import de.schildbach.wallet.data.WalletBalanceLiveData;
import de.schildbach.wallet.data.WalletLiveData;
import de.schildbach.wallet.exchangerate.ExchangeRateEntry;
import de.schildbach.wallet.service.BlockchainState.Impediment;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.ui.preference.ResolveDnsTask;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.core.listeners.AbstractPeerDataEventListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static androidx.core.util.Preconditions.checkState;

/**
 * @author Andreas Schildbach
 */
public class BlockchainService extends LifecycleService {
    private PowerManager pm;
    private NotificationManager nm;

    private WalletApplication application;
    private Configuration config;
    private AddressBookDao addressBookDao;
    private WalletLiveData wallet;

    private BlockStore blockStore;
    private File blockChainFile;
    private BlockChain blockChain;
    @Nullable
    private PeerGroup peerGroup;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler delayHandler = new Handler();
    private WakeLock wakeLock;

    private final NotificationCompat.Builder connectivityNotification = new NotificationCompat.Builder(BlockchainService.this,
            Constants.NOTIFICATION_CHANNEL_ID_ONGOING);
    private PeerConnectivityListener peerConnectivityListener;
    private ImpedimentsLiveData impediments;
    private int notificationCount = 0;
    private Coin notificationAccumulatedAmount = Coin.ZERO;
    private final List<Address> notificationAddresses = new LinkedList<>();
    private Stopwatch serviceUpTime;
    private boolean resetBlockchainOnShutdown = false;
    private final AtomicBoolean isBound = new AtomicBoolean(false);

    private static final int CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS = 144 * 2; // approx. 2 days
    private static final long BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;

    private static final String ACTION_CANCEL_COINS_RECEIVED = BlockchainService.class.getPackage().getName()
            + ".cancel_coins_received";
    private static final String ACTION_RESET_BLOCKCHAIN = BlockchainService.class.getPackage().getName()
            + ".reset_blockchain";

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    public static void start(final Context context, final boolean cancelCoinsReceived) {
        if (cancelCoinsReceived)
            ContextCompat.startForegroundService(context,
                    new Intent(BlockchainService.ACTION_CANCEL_COINS_RECEIVED, null, context, BlockchainService.class));
        else
            ContextCompat.startForegroundService(context, new Intent(context, BlockchainService.class));
    }

    public static void resetBlockchain(final Context context) {
        // implicitly stops blockchain service
        ContextCompat.startForegroundService(context,
                new Intent(BlockchainService.ACTION_RESET_BLOCKCHAIN, null, context, BlockchainService.class));
    }

    private static class NewTransactionLiveData extends LiveData<Transaction> {
        private final Wallet wallet;

        public NewTransactionLiveData(final Wallet wallet) {
            this.wallet = wallet;
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
                final String addressStr = notificationAddress.toString();
                final String label = addressBookDao.resolveLabel(addressStr);
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
        childNotification.setColor(getColor(R.color.fg_network_significant));
        childNotification.setSmallIcon(R.drawable.stat_notify_received_24dp);
        final String msg = getString(R.string.notification_coins_received_msg, btcFormat.format(amount)) + msgSuffix;
        childNotification.setTicker(msg);
        childNotification.setContentTitle(msg);
        if (address != null) {
            final String addressStr = address.toString();
            final String addressLabel = addressBookDao.resolveLabel(addressStr);
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
            implements PeerConnectedEventListener, PeerDisconnectedEventListener {
        private AtomicBoolean stopped = new AtomicBoolean(false);

        public void stop() {
            stopped.set(true);
        }

        @Override
        public void onPeerConnected(final Peer peer, final int peerCount) {
            postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS / 2);
            changed(peerCount);
        }

        @Override
        public void onPeerDisconnected(final Peer peer, final int peerCount) {
            changed(peerCount);
        }

        private void changed(final int numPeers) {
            if (stopped.get())
                return;

            handler.post(() -> {
                startForeground(numPeers);
                broadcastPeerState(numPeers);
            });
        }
    }

    private final PeerDataEventListener blockchainDownloadListener = new BlockchainDownloadListener();

    private class BlockchainDownloadListener extends AbstractPeerDataEventListener implements Runnable {
        private final AtomicLong lastMessageTime = new AtomicLong(0);
        private final AtomicInteger blocksToDownload = new AtomicInteger();
        private final AtomicInteger blocksLeft = new AtomicInteger();

        @Override
        public void onChainDownloadStarted(final Peer peer, final int blocksToDownload) {
            postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS / 2);
            this.blocksToDownload.set(blocksToDownload);
            if (blocksToDownload >= CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS) {
                config.maybeIncrementBestChainHeightEver(blockChain.getChainHead().getHeight() + blocksToDownload);
                startForegroundProgress(blocksToDownload, blocksToDownload);
            }
        }

        @Override
        public void onBlocksDownloaded(final Peer peer, final Block block, final FilteredBlock filteredBlock,
                final int blocksLeft) {
            this.blocksLeft.set(blocksLeft);

            delayHandler.removeCallbacks(this);
            final long now = System.currentTimeMillis();
            if (now - lastMessageTime.get() > BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS)
                delayHandler.post(this);
            else
                delayHandler.postDelayed(this, BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS);
        }

        @Override
        public void run() {
            lastMessageTime.set(System.currentTimeMillis());

            postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS / 2);
            final int blocksToDownload = this.blocksToDownload.get();
            final int blocksLeft = this.blocksLeft.get();
            if (blocksToDownload >= CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS)
                startForegroundProgress(blocksToDownload, blocksLeft);

            config.maybeIncrementBestChainHeightEver(blockChain.getChainHead().getHeight());
            broadcastBlockchainState();
        }
    }

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
                final boolean isMetered = hasConnectivity && connectivityManager.isActiveNetworkMetered();
                if (hasConnectivity)
                    impediments.remove(Impediment.NETWORK);
                else
                    impediments.add(Impediment.NETWORK);

                if (log.isInfoEnabled()) {
                    final StringBuilder s = new StringBuilder("active network is ").append(hasConnectivity ? "up" :
                            "down");
                    if (isMetered)
                        s.append(", metered");
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

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            (sharedPreferences, key) -> {
                if (Configuration.PREFS_KEY_SYNC_MODE.equals(key) || Configuration.PREFS_KEY_TRUSTED_PEERS.equals(key) ||
                        Configuration.PREFS_KEY_TRUSTED_PEERS_ONLY.equals(key))
                    stopSelf();
            };

    private Runnable delayedStopSelfRunnable = () -> {
        log.info("service idling detected, trying to stop");
        stopSelf();
        if (isBound.get())
            log.info("stop is deferred because service still bound");
    };

    private void postDelayedStopSelf(final long ms) {
        delayHandler.removeCallbacks(delayedStopSelfRunnable);
        delayHandler.postDelayed(delayedStopSelfRunnable, ms);
    }

    private final BroadcastReceiver deviceIdleModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            log.info("device {} idle mode", pm.isDeviceIdleMode() ? "entering" : "exiting");
        }
    };

    public class LocalBinder extends Binder {
        public BlockchainService getService() {
            return BlockchainService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(final Intent intent) {
        log.info("onBind: {}", intent);
        super.onBind(intent);
        isBound.set(true);
        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        log.info("onUnbind: {}", intent);
        isBound.set(false);
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        serviceUpTime = Stopwatch.createStarted();
        log.debug(".onCreate()");
        super.onCreate();

        application = (WalletApplication) getApplication();
        config = application.getConfiguration();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        log.info("acquiring {}", wakeLock);
        wakeLock.acquire();

        connectivityNotification.setColor(getColor(R.color.fg_network_significant));
        connectivityNotification.setContentTitle(getString(config.isTrustedPeersOnly() ?
                R.string.notification_connectivity_syncing_trusted_peer :
                R.string.notification_connectivity_syncing_message));
        connectivityNotification.setContentIntent(PendingIntent.getActivity(BlockchainService.this, 0,
                new Intent(BlockchainService.this, WalletActivity.class), 0));
        connectivityNotification.setWhen(System.currentTimeMillis());
        connectivityNotification.setOngoing(true);
        connectivityNotification.setPriority(NotificationCompat.PRIORITY_LOW);
        startForeground(0);

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        addressBookDao = AddressBookDatabase.getDatabase(application).addressBookDao();
        blockChainFile = new File(getDir("blockstore", Context.MODE_PRIVATE), Constants.Files.BLOCKCHAIN_FILENAME);

        config.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        registerReceiver(deviceIdleModeReceiver, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));

        peerConnectivityListener = new PeerConnectivityListener();

        broadcastPeerState(0);

        final WalletBalanceLiveData walletBalance = new WalletBalanceLiveData(application);
        final SelectedExchangeRateLiveData exchangeRate = new SelectedExchangeRateLiveData(application);
        walletBalance.observe(this, balance -> {
            final ExchangeRateEntry rate = exchangeRate.getValue();
            if (balance != null)
                WalletBalanceWidgetProvider.updateWidgets(BlockchainService.this, balance,
                        rate != null ? rate.exchangeRate() : null);
        });
        exchangeRate.observe(this, rate -> {
            final Coin balance = walletBalance.getValue();
            if (balance != null)
                WalletBalanceWidgetProvider.updateWidgets(BlockchainService.this, balance,
                        rate != null ? rate.exchangeRate() : null);
        });
        wallet = new WalletLiveData(application);
        wallet.observe(this, new Observer<Wallet>() {
            @Override
            public void onChanged(final Wallet wallet) {
                BlockchainService.this.wallet.removeObserver(this);
                final boolean blockChainFileExists = blockChainFile.exists();
                if (!blockChainFileExists) {
                    log.info("blockchain does not exist, resetting wallet");
                    wallet.reset();
                }

                try {
                    blockStore = new SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile,
                            Constants.Files.BLOCKCHAIN_STORE_CAPACITY, true);
                    blockStore.getChainHead(); // detect corruptions as early as possible

                    final long earliestKeyCreationTimeSecs = wallet.getEarliestKeyCreationTime();

                    if (!blockChainFileExists && earliestKeyCreationTimeSecs > 0) {
                        try {
                            log.info("loading checkpoints for birthdate {} from '{}'",
                                    Utils.dateTimeFormat(earliestKeyCreationTimeSecs * 1000),
                                    Constants.Files.CHECKPOINTS_ASSET);
                            final Stopwatch watch = Stopwatch.createStarted();
                            final InputStream checkpointsInputStream = getAssets()
                                    .open(Constants.Files.CHECKPOINTS_ASSET);
                            CheckpointManager.checkpoint(Constants.NETWORK_PARAMETERS, checkpointsInputStream,
                                    blockStore, earliestKeyCreationTimeSecs);
                            watch.stop();
                            log.info("checkpoints loaded, took {}", watch);
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

                observeLiveDatasThatAreDependentOnWalletAndBlockchain();
            }
        });
    }

    private void observeLiveDatasThatAreDependentOnWalletAndBlockchain() {
        final NewTransactionLiveData newTransaction = new NewTransactionLiveData(wallet.getValue());
        newTransaction.observe(this, tx -> {
            final Wallet wallet = BlockchainService.this.wallet.getValue();
            postDelayedStopSelf(5 * DateUtils.MINUTE_IN_MILLIS);
            final Coin amount = tx.getValue(wallet);
            if (amount.isPositive()) {
                final Address address = WalletUtils.getWalletAddressOfReceived(tx, wallet);
                final ConfidenceType confidenceType = tx.getConfidence().getConfidenceType();
                final boolean replaying = blockChain.getBestChainHeight() < config.getBestChainHeightEver();
                final boolean isReplayedTx = confidenceType == ConfidenceType.BUILDING && replaying;
                if (!isReplayedTx)
                    notifyCoinsReceived(address, amount, tx.getTxId());
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
                final Wallet wallet = BlockchainService.this.wallet.getValue();

                // consistency check
                final int walletLastBlockSeenHeight = wallet.getLastBlockSeenHeight();
                final int bestChainHeight = blockChain.getBestChainHeight();
                if (walletLastBlockSeenHeight != -1 && walletLastBlockSeenHeight != bestChainHeight) {
                    final String message = "wallet/blockchain out of sync: " + walletLastBlockSeenHeight + "/"
                            + bestChainHeight;
                    log.error(message);
                    CrashReporter.saveBackgroundTrace(new RuntimeException(message), application.packageInfo());
                }

                final Configuration.SyncMode syncMode = config.getSyncMode();
                peerGroup = new PeerGroup(Constants.NETWORK_PARAMETERS, blockChain);
                log.info("creating {}, sync mode: {}", peerGroup, syncMode);
                peerGroup.setDownloadTxDependencies(0); // recursive implementation causes StackOverflowError
                peerGroup.addWallet(wallet);
                peerGroup.setBloomFilteringEnabled(syncMode == Configuration.SyncMode.CONNECTION_FILTER);
                peerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
                peerGroup.addConnectedEventListener(peerConnectivityListener);
                peerGroup.addDisconnectedEventListener(peerConnectivityListener);

                final int maxConnectedPeers = application.maxConnectedPeers();
                final Set<HostAndPort> trustedPeers = config.getTrustedPeers();
                final boolean trustedPeerOnly = config.isTrustedPeersOnly();

                peerGroup.setMaxConnections(trustedPeerOnly ? 0 : maxConnectedPeers);
                peerGroup.setConnectTimeoutMillis(Constants.PEER_TIMEOUT_MS);
                peerGroup.setPeerDiscoveryTimeoutMillis(Constants.PEER_DISCOVERY_TIMEOUT_MS);
                peerGroup.setStallThreshold(20, Block.HEADER_SIZE * 10);

                final ResolveDnsTask resolveDnsTask = new ResolveDnsTask(backgroundHandler) {
                    @Override
                    protected void onSuccess(final HostAndPort hostAndPort, final InetSocketAddress socketAddress) {
                        log.info("trusted peer '{}' resolved to {}", hostAndPort,
                                socketAddress.getAddress().getHostAddress());
                        if (socketAddress != null) {
                            peerGroup.addAddress(new PeerAddress(Constants.NETWORK_PARAMETERS, socketAddress), 10);
                            if (peerGroup.getMaxConnections() > maxConnectedPeers)
                                peerGroup.setMaxConnections(maxConnectedPeers);
                        }
                    }

                    @Override
                    protected void onUnknownHost(final HostAndPort hostAndPort) {
                        log.info("trusted peer '{}' unknown host", hostAndPort);
                    }
                };
                for (final HostAndPort trustedPeer : trustedPeers)
                    resolveDnsTask.resolve(trustedPeer);

                if (trustedPeerOnly) {
                    log.info("trusted peers only – not adding any random nodes from the P2P network");
                } else {
                    log.info("adding random peers from the P2P network");
                    if (syncMode == Configuration.SyncMode.CONNECTION_FILTER)
                        peerGroup.setRequiredServices(VersionMessage.NODE_BLOOM | VersionMessage.NODE_WITNESS);
                    else
                        peerGroup.setRequiredServices(VersionMessage.NODE_WITNESS);
                }

                // start peergroup
                log.info("starting {} asynchronously", peerGroup);
                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(blockchainDownloadListener);

                postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS / 2);
            }

            private void shutdown() {
                final Wallet wallet = BlockchainService.this.wallet.getValue();

                peerGroup.removeDisconnectedEventListener(peerConnectivityListener);
                peerGroup.removeConnectedEventListener(peerConnectivityListener);
                peerGroup.removeWallet(wallet);
                log.info("stopping {} asynchronously", peerGroup);
                peerGroup.stopAsync();
                peerGroup = null;
            }
        });
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);
        postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS);

        if (intent != null) {
            final String action = intent.getAction();
            log.info("service start command: {}", action);

            if (BlockchainService.ACTION_CANCEL_COINS_RECEIVED.equals(action)) {
                notificationCount = 0;
                notificationAccumulatedAmount = Coin.ZERO;
                notificationAddresses.clear();

                nm.cancel(Constants.NOTIFICATION_ID_COINS_RECEIVED);
            } else if (BlockchainService.ACTION_RESET_BLOCKCHAIN.equals(action)) {
                log.info("will remove blockchain on service shutdown");
                resetBlockchainOnShutdown = true;
                stopSelf();
                if (isBound.get())
                    log.info("stop is deferred because service still bound");
            }
        } else {
            log.warn("service restart, although it was started as non-sticky");
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        log.debug(".onDestroy()");

        if (peerGroup != null) {
            peerGroup.removeDisconnectedEventListener(peerConnectivityListener);
            peerGroup.removeConnectedEventListener(peerConnectivityListener);
            peerGroup.removeWallet(wallet.getValue());
            peerGroup.stopAsync();
            log.info("stopping {} asynchronously", peerGroup);
        }

        peerConnectivityListener.stop();

        delayHandler.removeCallbacksAndMessages(null);

        backgroundHandler.removeCallbacksAndMessages(null);
        backgroundThread.getLooper().quit();

        if (blockStore != null) {
            try {
                blockStore.close();
            } catch (final BlockStoreException x) {
                throw new RuntimeException(x);
            }
        }

        application.autosaveWalletNow();

        if (resetBlockchainOnShutdown) {
            log.info("removing blockchain");
            blockChainFile.delete();
        }

        unregisterReceiver(deviceIdleModeReceiver);

        config.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);

        final boolean expectLargeData =
                blockChain != null && (config.getBestChainHeightEver() - blockChain.getBestChainHeight()) > CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS;
        StartBlockchainService.schedule(application, expectLargeData);

        wakeLock.release();
        log.info("released {}", wakeLock);
        checkState(!wakeLock.isHeld(), "still held: " + wakeLock);

        super.onDestroy();

        log.info("service was up for {}", serviceUpTime.stop());
    }

    @Override
    public void onTrimMemory(final int level) {
        log.info("onTrimMemory({}) called", level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            log.warn("low memory detected, trying to stop");
            stopSelf();
            if (isBound.get())
                log.info("stop is deferred because service still bound");
        }
    }

    @Nullable
    public TransactionBroadcast broadcastTransaction(final Transaction tx) {
        if (peerGroup != null) {
            log.info("broadcasting transaction {}", tx.getTxId());
            return peerGroup.broadcastTransaction(tx);
        } else {
            log.info("peergroup not available, not broadcasting transaction {}", tx.getTxId());
            return null;
        }
    }

    @Nullable
    public BlockchainState getBlockchainState() {
        if (blockChain == null)
            return null;

        final StoredBlock chainHead = blockChain.getChainHead();
        final Date bestChainDate = chainHead.getHeader().getTime();
        final int bestChainHeight = chainHead.getHeight();
        final boolean replaying = chainHead.getHeight() < config.getBestChainHeightEver();

        return new BlockchainState(bestChainDate, bestChainHeight, replaying, impediments.getValue());
    }

    @Nullable
    public List<Peer> getConnectedPeers() {
        if (peerGroup == null)
            return null;

        return peerGroup.getConnectedPeers();
    }

    public void dropAllPeers() {
        if (peerGroup == null)
            return;
        peerGroup.dropAllPeers();
    }

    @Nullable
    public List<StoredBlock> getRecentBlocks(final int maxBlocks) {
        if (blockChain == null || blockStore == null)
            return null;

        final List<StoredBlock> blocks = new ArrayList<>(maxBlocks);
        StoredBlock block = blockChain.getChainHead();
        while (block != null) {
            blocks.add(block);
            if (blocks.size() >= maxBlocks)
                break;
            try {
                block = block.getPrev(blockStore);
            } catch (final BlockStoreException x) {
                log.info("skipping blocks because of exception", x);
                break;
            }
        }
        return blocks.isEmpty() ? null : blocks;
    }

    private void startForeground(final int numPeers) {
        if (config.isTrustedPeersOnly()) {
            connectivityNotification.setSmallIcon(R.drawable.stat_notify_peers, numPeers > 0 ? 4 : 0);
            connectivityNotification.setContentText(getString(numPeers > 0 ? R.string.notification_peer_connected :
                    R.string.notification_peer_not_connected));
        } else {
            connectivityNotification.setSmallIcon(R.drawable.stat_notify_peers, Math.min(numPeers, 4));
            connectivityNotification.setContentText(getString(R.string.notification_peers_connected_msg, numPeers));
        }
        startForeground(Constants.NOTIFICATION_ID_CONNECTIVITY, connectivityNotification.build());
    }

    private void startForegroundProgress(final int blocksToDownload, final int blocksLeft) {
        connectivityNotification.setProgress(blocksToDownload, blocksToDownload - blocksLeft, false);
        startForeground(Constants.NOTIFICATION_ID_CONNECTIVITY, connectivityNotification.build());
    }

    @MainThread
    private void broadcastPeerState(final int numPeers) {
        application.peerState.setValue(numPeers);
    }

    @MainThread
    private void broadcastBlockchainState() {
        final BlockchainState blockchainState = getBlockchainState();
        application.blockchainState.setValue(blockchainState);
    }
}
