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
package de.schildbach.wallet.integration.sample.channels;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.WalletExtension;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates;
import org.bitcoinj.protocols.channels.StoredPaymentChannelServerStates;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import ch.qos.logback.classic.android.BasicLogcatConfigurator;

/**
 * A simple service to present a payment channel wallet to activities
 */
public class PaymentChannelService extends Service {

    private static final String TAG = PaymentChannelService.class.getName();

    public static final String BROADCAST_STARTED = PaymentChannelService.class.getCanonicalName() + ".started";

    private static String BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";

    static {
        BasicLogcatConfigurator.configureDefaultContext();
    }

    /**
     * Set this variable to decide whether to use testNet or not. Don't do this in real life - this
     * is a sample.
     */
    public static boolean TEST_NET = true;

    private WalletAppKit walletAppKit;

    @Override
    public void onCreate() {
        super.onCreate();

        NetworkParameters params = TEST_NET ? TestNet3Params.get() : MainNetParams.get();

        final Handler handler = new Handler(Looper.getMainLooper());

        try {
            MnemonicCode.INSTANCE = new MnemonicCode(getAssets().open("bip39-wordlist.txt"), BIP39_ENGLISH_SHA256);
        } catch (IOException e) {
            Log.e(TAG, "Failed to find BIP39 wordlist", e);
            stopSelf();
            return;
        }

        String walletFileName = TEST_NET ? "test_wallet" : "main_wallet";
        walletAppKit = new WalletAppKit(params, getFilesDir(), walletFileName) {
            @Override
            protected void onSetupCompleted() {
                super.onSetupCompleted();
                wallet().allowSpendingUnconfirmedTransactions();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        LocalBroadcastManager.getInstance(PaymentChannelService.this)
                                .sendBroadcast(new Intent(BROADCAST_STARTED));
                    }
                });
            }

            @Override
            protected List<WalletExtension> provideWalletExtensions() throws Exception {
                return ImmutableList.of(
                        new StoredPaymentChannelClientStates(null),
                        new StoredPaymentChannelServerStates(null));
            }
        };

        // Set up checkpoints
        try {
            final InputStream checkpointsInputStream;
            checkpointsInputStream = getAssets().open(TEST_NET ? "checkpoints-testnet.txt" : "checkpoints.txt");
            walletAppKit.setCheckpoints(checkpointsInputStream);
        } catch (IOException e) {
            Log.e(TAG, "Failed to set up checkpoints", e);
        }

        walletAppKit.setBlockingStartup(true);
        walletAppKit.setAutoSave(true);
        walletAppKit.startAsync();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        walletAppKit.stopAsync();
    }

    public WalletAppKit getWalletAppKit() {
        return walletAppKit;
    }

    public class LocalBinder extends Binder {
        PaymentChannelService getService() {
            return PaymentChannelService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }
}
