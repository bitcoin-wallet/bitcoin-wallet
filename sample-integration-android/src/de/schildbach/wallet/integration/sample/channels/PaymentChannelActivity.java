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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.bitcoinj.kits.WalletAppKit;

import de.schildbach.wallet.integration.android.BitcoinIntegration;
import de.schildbach.wallet.integration.sample.R;

public class PaymentChannelActivity extends Activity implements ServiceConnection {
    public static final String INTENT_TESTNET = PaymentChannelActivity.class.getCanonicalName() + ".testnet";

    private WalletAppKit walletAppKit;

    private TextView status, balance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.payment_channel_activity);

        status = (TextView) findViewById(R.id.wallet_status);
        balance = (TextView) findViewById(R.id.wallet_balance);

        findViewById(R.id.start_client_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(PaymentChannelActivity.this, PaymentChannelClientActivity.class));
            }
        });
        findViewById(R.id.start_server_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(PaymentChannelActivity.this, PaymentChannelServerActivity.class));
            }
        });

        PaymentChannelService.TEST_NET = getIntent().getBooleanExtra(INTENT_TESTNET, true);
        bindService(new Intent(this, PaymentChannelService.class), this, BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter(PaymentChannelService.BROADCAST_STARTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder binder) {
        if (binder instanceof PaymentChannelService.LocalBinder) {
            walletAppKit = ((PaymentChannelService.LocalBinder)binder).getService().getWalletAppKit();

            Button requestBitcoin = (Button) findViewById(R.id.request_bitcoin);
            requestBitcoin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    BitcoinIntegration.request(PaymentChannelActivity.this,
                            walletAppKit.wallet().currentReceiveAddress().toString());
                }
            });
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        walletAppKit = null;
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == PaymentChannelService.BROADCAST_STARTED) {
                status.setText("Started");
                balance.setText(walletAppKit.wallet().getBalance().toFriendlyString());
            }
        }
    };
}
