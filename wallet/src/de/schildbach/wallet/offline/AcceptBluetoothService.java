/*
 * Copyright 2013-2015 the original author or authors.
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

package de.schildbach.wallet.offline;

import java.io.IOException;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.R;

import android.app.Service;
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

/**
 * @author Andreas Schildbach
 */
public final class AcceptBluetoothService extends Service {
    private WalletApplication application;
    private Wallet wallet;
    private WakeLock wakeLock;
    private AcceptBluetoothThread classicThread;
    private AcceptBluetoothThread paymentProtocolThread;

    private long serviceCreatedAt;

    private final Handler handler = new Handler();

    private static final long TIMEOUT_MS = 5 * DateUtils.MINUTE_IN_MILLIS;

    private static final Logger log = LoggerFactory.getLogger(AcceptBluetoothService.class);

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
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
        this.wallet = application.getWallet();

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + " bluetooth transaction submission");
        wakeLock.acquire();

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

            classicThread.start();
            paymentProtocolThread.start();
        } catch (final IOException x) {
            new Toast(this).longToast(R.string.error_bluetooth, x.getMessage());
            CrashReporter.saveBackgroundTrace(x, application.packageInfo());
        }
    }

    private boolean handleTx(final Transaction tx) {
        log.info("tx " + tx.getHashAsString() + " arrived via blueooth");

        try {
            if (wallet.isTransactionRelevant(tx)) {
                wallet.receivePending(tx, null);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        application.broadcastTransaction(tx);
                    }
                });
            } else {
                log.info("tx " + tx.getHashAsString() + " irrelevant");
            }

            return true;
        } catch (final VerificationException x) {
            log.info("cannot verify tx " + tx.getHashAsString() + " received via bluetooth", x);
        }

        return false;
    }

    @Override
    public void onDestroy() {
        paymentProtocolThread.stopAccepting();
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

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            log.info("timeout expired, stopping service");

            stopSelf();
        }
    };
}
