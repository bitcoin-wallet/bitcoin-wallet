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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.protocols.channels.PaymentIncrementAck;
import org.bitcoinj.protocols.channels.ValueOutOfRangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import de.schildbach.wallet.integration.android.channels.PaymentChannelClientAndroidConnection;
import de.schildbach.wallet.integration.android.channels.PaymentChannelConnector;
import de.schildbach.wallet.integration.android.channels.PaymentChannelServerAndroidConnection;
import de.schildbach.wallet.integration.sample.R;

public class PaymentChannelClientActivity extends Activity implements ServiceConnection, View.OnClickListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentChannelServerAndroidConnection.class);

    private WalletAppKit walletAppKit;

    private PaymentChannelConnector paymentChannelConnector;
    private PaymentChannelClientAndroidConnection client;

    private Handler handler;

    private EditText channelSize, channelExpiryHours;
    private TextView status, paymentTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.payment_channel_client_activity);

        handler = new Handler(Looper.getMainLooper());

        try {
            paymentChannelConnector = new PaymentChannelConnector(this);
        } catch (PaymentChannelConnector.PaymentChannelConnectionException e) {
            Toast.makeText(this, "Couldn't connect to wallet - is wallet installed?", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        channelSize = (EditText) findViewById(R.id.channel_size);
        channelExpiryHours = (EditText) findViewById(R.id.channel_expiry_hours);
        status = (TextView) findViewById(R.id.channel_status);
        paymentTotal = (TextView) findViewById(R.id.channel_amount);

        findViewById(R.id.start).setOnClickListener(this);
        findViewById(R.id.satoshi_100).setOnClickListener(this);
        findViewById(R.id.satoshi_1000).setOnClickListener(this);
        findViewById(R.id.satoshi_10000).setOnClickListener(this);
        findViewById(R.id.satoshi_100000).setOnClickListener(this);
        findViewById(R.id.satoshi_1000000).setOnClickListener(this);
        findViewById(R.id.settle_channel).setOnClickListener(this);

        bindService(new Intent(this, PaymentChannelService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (paymentChannelConnector != null) {
            try {
                paymentChannelConnector.close();
            } catch (IOException e) {
                log.warn("Failed to close wallet channel connector", e);
            }
        }
        unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        if (binder instanceof PaymentChannelService.LocalBinder) {
            walletAppKit = ((PaymentChannelService.LocalBinder)binder).getService().getWalletAppKit();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        walletAppKit = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start:
                long maxValue;
                long maxTime;
                try {
                    maxValue = Long.valueOf(channelSize.getText().toString());
                    maxTime = (long) (Float.valueOf(channelExpiryHours.getText().toString()) * 3600);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_LONG).show();
                    break;
                }

                findViewById(R.id.start).setEnabled(false);

                startChannel(Coin.valueOf(maxValue), maxTime);
                break;
            case R.id.satoshi_100:
                increaseAmount(Coin.valueOf(100));
                break;
            case R.id.satoshi_1000:
                increaseAmount(Coin.valueOf(1000));
                break;
            case R.id.satoshi_10000:
                increaseAmount(Coin.valueOf(10000));
                break;
            case R.id.satoshi_100000:
                increaseAmount(Coin.valueOf(100000));
                break;
            case R.id.satoshi_1000000:
                increaseAmount(Coin.valueOf(1000000));
                break;
            case R.id.settle_channel:
                settleChannel();
                break;
        }
    }

    private void startChannel(Coin maxValue, long maxTime) {
        try {
            ECKey myKey = new ECKey();
            walletAppKit.wallet().importKey(myKey);
            String serverId = "SERVER ID NOT REAL BLAH";
            client = new PaymentChannelClientAndroidConnection(paymentChannelConnector,
                    walletAppKit.wallet(),
                    myKey,
                    maxValue,
                    serverId,
                    maxTime,
                    null);
            Futures.addCallback(client.getChannelOpenFuture(), new FutureCallback<PaymentChannelClientAndroidConnection>() {
                @Override
                public void onSuccess(PaymentChannelClientAndroidConnection result) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("Channel open");
                            findViewById(R.id.satoshi_100).setEnabled(true);
                            findViewById(R.id.satoshi_1000).setEnabled(true);
                            findViewById(R.id.satoshi_10000).setEnabled(true);
                            findViewById(R.id.satoshi_100000).setEnabled(true);
                            findViewById(R.id.satoshi_1000000).setEnabled(true);
                            findViewById(R.id.settle_channel).setEnabled(true);
                        }
                    });
                }

                @Override
                public void onFailure(final Throwable t) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("Channel failed to open: " + t.getMessage());
                        }
                    });
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ValueOutOfRangeException e) {
            e.printStackTrace();
        }
    }

    private void increaseAmount(Coin amount) {
        try {
            Futures.addCallback(client.incrementPayment(amount),
                    new FutureCallback<PaymentIncrementAck>() {
                        @Override
                        public void onSuccess(final PaymentIncrementAck result) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(PaymentChannelClientActivity.this, "Payment incremented", Toast.LENGTH_SHORT).show();
                                    paymentTotal.setText(result.getValue().toFriendlyString());
                                }
                            });
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            log.warn("Payment increment failed", t);
                            Toast.makeText(PaymentChannelClientActivity.this, "Payment increment failed", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (ValueOutOfRangeException e) {
            log.warn("Requesting payment channel increment failed", e);
            Toast.makeText(this, "Failed to increment payment", Toast.LENGTH_LONG).show();
        }
    }

    private void settleChannel() {
        client.settle();
    }
}
