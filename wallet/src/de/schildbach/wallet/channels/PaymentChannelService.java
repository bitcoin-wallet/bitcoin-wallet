/*
 * Copyright 2016 the original author or authors.
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
package de.schildbach.wallet.channels;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.SettableFuture;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet_test.R;

/**
 * An Android service for exposing the bitcoinj payment channel system to other apps, such that they
 * can request and send micropayments to and from the device's local wallet.
 */
public class PaymentChannelService extends Service {

    private static final Logger log = LoggerFactory.getLogger(PaymentChannelService.class);

    public static final String BROADCAST_CONFIRM_CHANNEL =
            PaymentChannelService.class.getCanonicalName() + ".confirm_channel";
    public static final String BROADCAST_CONFIRM_CHANNEL_EXTRA_CHANNEL_ID =
            PaymentChannelService.class.getCanonicalName() + ".channel_id";
    public static final String BROADCAST_CONFIRM_CHANNEL_EXTRA_PASSWORD =
            PaymentChannelService.class.getCanonicalName() + ".password";
    public static final String BROADCAST_CONFIRM_CHANNEL_EXTRA_CONFIRMED =
            PaymentChannelService.class.getCanonicalName() + ".confirmed";

    public static final String BROADCAST_CONFIRM_INCREMENT =
            PaymentChannelService.class.getCanonicalName() + ".confirm_increment";
    public static final String BROADCAST_CONFIRM_INCREMENT_EXTRA_CHANNEL_ID =
            PaymentChannelService.class.getCanonicalName() + ".channel_id";
    public static final String BROADCAST_CONFIRM_INCREMENT_EXTRA_PASSWORD =
            PaymentChannelService.class.getCanonicalName() + ".password";
    public static final String BROADCAST_CONFIRM_INCREMENT_EXTRA_CONFIRMED =
            PaymentChannelService.class.getCanonicalName() + ".confirmed";
    public static final String BROADCAST_CONFIRM_INCREMENT_EXTRA_INCREMENT_ID =
            PaymentChannelService.class.getCanonicalName() + ".increment_id";

    private SettableFuture<BlockchainService> blockchainServiceFuture;

    private static final AtomicInteger CLIENT_CHANNEL_ID = new AtomicInteger(0);
    private Map<Integer, PaymentChannelClientInstanceBinder> openClientChannels;

    private Handler handler;
    private ContentResolver contentResolver;

    private static final int NOTIFICATION_ID_CHANNEL_INCREMENT = 1000001;

    private NotificationManager nm;
    private int notificationCount = 0;
    private Coin notificationAccumulatedAmount = Coin.ZERO;
    private final List<String> notificationAppNames = new LinkedList<String>();

    @Override
    public void onCreate() {
        super.onCreate();
        Intent blockchainServiceIntent = new Intent(this, BlockchainServiceImpl.class);
        if (bindService(blockchainServiceIntent, blockchainServiceConn, BIND_AUTO_CREATE)) {
            log.debug("Binding to blockchain service");
            blockchainServiceFuture = SettableFuture.create();
        } else {
            log.warn("Failed to connect to blockchain service");
        }

        handler = new Handler();
        contentResolver = getContentResolver();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        openClientChannels = new MapMaker().weakValues().makeMap();

        IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(BROADCAST_CONFIRM_CHANNEL);
        broadcastFilter.addAction(BROADCAST_CONFIRM_INCREMENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, broadcastFilter);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        unbindService(blockchainServiceConn);
        super.onDestroy();
    }

    private ServiceConnection blockchainServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            log.debug("Bound to blockchain service");
            blockchainServiceFuture.set(((BlockchainServiceImpl.LocalBinder) binder).getService());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            blockchainServiceFuture = null;
            log.debug("Disconnecting from blockchain service");
        }
    };

    WalletApplication getWalletApplication() {
        return (WalletApplication) getApplication();
    }

    private Wallet getWallet() {
        return getWalletApplication().getWallet();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log.debug("onBind()");
        if (blockchainServiceFuture == null) {
            return null;
        }
        return new PaymentChannelsBinder(
                this,
                getWallet(),
                blockchainServiceFuture);
    }

    IPaymentChannelClientInstance createClientChannel(
            Wallet wallet,
            IPaymentChannelCallbacks callbacks,
            Coin maxValue,
            Sha256Hash serverId,
            long requestedTimeWindow) {
        final int channelId = CLIENT_CHANNEL_ID.incrementAndGet();
        PaymentChannelClientInstanceBinder result = new PaymentChannelClientInstanceBinder(
                this, wallet, callbacks, channelId, maxValue, serverId, requestedTimeWindow);
        openClientChannels.put(channelId, result);
        return result;
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BROADCAST_CONFIRM_CHANNEL.equals(intent.getAction())) {
                // ID in the lookup map
                int id = intent.getIntExtra(BROADCAST_CONFIRM_CHANNEL_EXTRA_CHANNEL_ID, -1);
                // Boolean indicating confirm or cancel
                boolean confirm = intent.getBooleanExtra(BROADCAST_CONFIRM_CHANNEL_EXTRA_CONFIRMED, false);

                String password = intent.getStringExtra(BROADCAST_CONFIRM_CHANNEL_EXTRA_PASSWORD);

                // TODO fail gracefully if password was incorrect
                if (getWallet().isEncrypted() != (password != null)) {
                    // TODO notify no password when needed
                }

                KeyParameter key = password != null ?
                        getWallet().getKeyCrypter().deriveKey(password):
                        null;

                PaymentChannelClientInstanceBinder binder = openClientChannels.get(id);
                if (binder == null) {
                    Toast.makeText(context, "Payment failed", Toast.LENGTH_LONG).show();
                    return;
                }
                if (confirm) {
                    binder.onChannelConfirmed(key);
                } else {
                    binder.onChannelCancelled();
                }
            } else if (BROADCAST_CONFIRM_INCREMENT.equals(intent.getAction())) {
                // ID in the lookup map
                int id = intent.getIntExtra(BROADCAST_CONFIRM_INCREMENT_EXTRA_CHANNEL_ID, -1);
                // Boolean indicating confirm or cancel
                boolean confirm = intent.getBooleanExtra(BROADCAST_CONFIRM_INCREMENT_EXTRA_CONFIRMED, false);

                long incrementId = intent.getLongExtra(BROADCAST_CONFIRM_INCREMENT_EXTRA_INCREMENT_ID, -1);

                String password = intent.getStringExtra(BROADCAST_CONFIRM_INCREMENT_EXTRA_PASSWORD);

                KeyParameter key = password != null ?
                        getWallet().getKeyCrypter().deriveKey(password):
                        null;

                // TODO fail gracefully if password was incorrect
                if (getWallet().isEncrypted() != (key != null)) {
                    // TODO notify no password when needed
                }

                PaymentChannelClientInstanceBinder binder = openClientChannels.get(id);
                if (binder == null) {
                    Toast.makeText(context, "Payment failed", Toast.LENGTH_LONG).show();
                }
                if (confirm) {
                    binder.onChannelIncrementConfirmed(incrementId, key);
                } else {
                    binder.onChannelIncrementCancelled();
                }
            }
        }
    };

    void notifyChannelIncrement(final Coin amount, final String appName) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (notificationCount == 1)
                    nm.cancel(NOTIFICATION_ID_CHANNEL_INCREMENT);

                notificationCount++;
                notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
                if (appName != null && !notificationAppNames.contains(appName))
                    notificationAppNames.add(appName);

                final MonetaryFormat btcFormat = getWalletApplication().getConfiguration().getFormat();

                final String packageFlavor = getWalletApplication().applicationPackageFlavor();
                final String msgSuffix = packageFlavor != null ? " [" + packageFlavor + "]" : "";

                final String tickerMsg = getString(R.string.notification_micropayment_received_msg, btcFormat.format(amount)) + msgSuffix;
                final String msg = getString(R.string.notification_micropayment_received_msg, btcFormat.format(notificationAccumulatedAmount)) + msgSuffix;

                final StringBuilder text = new StringBuilder();
                for (final String notificationAppName : notificationAppNames)
                {
                    if (text.length() > 0)
                        text.append(", ");

                    text.append(notificationAppName);
                }

                final Notification.Builder notification = new Notification.Builder(PaymentChannelService.this);
                notification.setSmallIcon(R.drawable.stat_notify_received);
                notification.setTicker(tickerMsg);
                notification.setContentTitle(msg);
                if (text.length() > 0)
                    notification.setContentText(text);
                notification.setContentIntent(PendingIntent.getActivity(PaymentChannelService.this, 0, new Intent(PaymentChannelService.this, WalletActivity.class), 0));
                notification.setNumber(notificationCount == 1 ? 0 : notificationCount);
                notification.setWhen(System.currentTimeMillis());
                notification.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received));
                nm.notify(NOTIFICATION_ID_CHANNEL_INCREMENT, notification.getNotification());
            }
        });
    }

    /**
     * Adds the P2SH address of a channel to the address book
     */
    void addChannelToAddressBook(final Transaction contract, final boolean server, final String packageName) {
        Address address = null;
        for (TransactionOutput output : contract.getOutputs()) {
            if ((address = output.getAddressFromP2SH(getWallet().getNetworkParameters())) != null) {
                break;
            }
        }
        if (address == null) {
            return;
        }
        final Address finalAddress = address;
        handler.post(new Runnable() {
            @Override
            public void run() {
                final int labelId = server ? R.string.channel_address_server_label : R.string.channel_address_client_label;

                CharSequence appName;
                try {
                    ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
                    appName = getPackageManager().getApplicationLabel(info);
                } catch (PackageManager.NameNotFoundException e) {
                    log.warn("Couldn't find package name", e);
                    appName = "unknown app";
                }

                final Uri uri = AddressBookProvider.contentUri(getPackageName()).buildUpon().appendPath(finalAddress.toString()).build();
                final ContentValues values = new ContentValues();
                values.put(AddressBookProvider.KEY_LABEL, getString(labelId, appName));

                contentResolver.insert(uri, values);
            }
        });
    }
}
