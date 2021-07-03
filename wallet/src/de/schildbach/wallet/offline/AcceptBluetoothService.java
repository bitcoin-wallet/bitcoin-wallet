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

package de.schildbach.wallet.offline;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.format.DateUtils;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.BlockchainServiceLiveData;
import de.schildbach.wallet.data.WalletLiveData;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Toast;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static androidx.core.util.Preconditions.checkNotNull;

/**
 * @author Andreas Schildbach
 */
public final class AcceptBluetoothService extends LifecycleService {
    private WalletApplication application;
    private WalletLiveData wallet;
    private WakeLock wakeLock;
    private AcceptBluetoothThread classicThread;
    private AcceptBluetoothThread paymentProtocolThread;

    private long serviceCreatedAt;

    private final Handler handler = new Handler();

    private static final long TIMEOUT_MS = 5 * DateUtils.MINUTE_IN_MILLIS;

    private static final Logger log = LoggerFactory.getLogger(AcceptBluetoothService.class);

    @Override
    public IBinder onBind(final Intent intent) {
        super.onBind(intent);
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);

        handler.removeCallbacks(timeoutRunnable);
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        serviceCreatedAt = System.currentTimeMillis();
        log.debug(".onCreate()");

        super.onCreate();
        this.application = (WalletApplication) getApplication();
        final BluetoothAdapter bluetoothAdapter = checkNotNull(BluetoothAdapter.getDefaultAdapter());
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.acquire();

        final NotificationCompat.Builder notification = new NotificationCompat.Builder(this,
                Constants.NOTIFICATION_CHANNEL_ID_ONGOING);
        notification.setColor(getColor(R.color.fg_network_significant));
        notification.setSmallIcon(R.drawable.stat_notify_bluetooth_24dp);
        notification.setContentTitle(getString(R.string.notification_bluetooth_service_listening));
        notification.setWhen(System.currentTimeMillis());
        notification.setOngoing(true);
        notification.setPriority(NotificationCompat.PRIORITY_LOW);
        startForeground(Constants.NOTIFICATION_ID_BLUETOOTH, notification.build());

        registerReceiver(bluetoothStateChangeReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        try {
            classicThread = new AcceptBluetoothThread.ClassicBluetoothThread(bluetoothAdapter) {
                @Override
                public boolean handleTx(final Transaction tx) {
                    return AcceptBluetoothService.this.handleTx(tx);
                }
            };
            paymentProtocolThread = new AcceptBluetoothThread.PaymentProtocolThread(bluetoothAdapter) {
                @Override
                public boolean handleTx(final Transaction tx) {
                    return AcceptBluetoothService.this.handleTx(tx);
                }
            };
        } catch (final IOException x) {
            new Toast(this).longToast(R.string.error_bluetooth, x.getMessage());
            log.warn("problem with listening, stopping service", x);
            CrashReporter.saveBackgroundTrace(x, application.packageInfo());
            stopSelf();
        }

        wallet = new WalletLiveData(application);
        wallet.observe(this, wallet -> {
            classicThread.start();
            paymentProtocolThread.start();
        });
    }

    private boolean handleTx(final Transaction tx) {
        log.info("tx {} arrived via blueooth", tx.getTxId());

        final Wallet wallet = this.wallet.getValue();
        try {
            if (wallet.isTransactionRelevant(tx)) {
                wallet.receivePending(tx, null);
                new BlockchainServiceLiveData(this).observe(this,
                        blockchainService -> blockchainService.broadcastTransaction(tx));
            } else {
                log.info("tx {} irrelevant", tx.getTxId());
            }

            return true;
        } catch (final VerificationException x) {
            log.info("cannot verify tx " + tx.getTxId() + " received via bluetooth", x);
        }

        return false;
    }

    @Override
    public void onDestroy() {
        if (paymentProtocolThread != null)
            paymentProtocolThread.stopAccepting();
        if (classicThread != null)
            classicThread.stopAccepting();

        unregisterReceiver(bluetoothStateChangeReceiver);

        wakeLock.release();

        handler.removeCallbacksAndMessages(null);

        super.onDestroy();

        log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
    }

    private final BroadcastReceiver bluetoothStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);

            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                log.info("bluetooth was turned off, stopping service");

                stopSelf();
            }
        }
    };

    private final Runnable timeoutRunnable = () -> {
        log.info("timeout expired, stopping service");

        stopSelf();
    };
}
