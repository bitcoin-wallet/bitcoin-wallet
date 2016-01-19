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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;

import java.io.IOException;

import javax.annotation.Nullable;

import de.schildbach.wallet.integration.android.channels.PaymentChannelConnector;
import de.schildbach.wallet.integration.android.channels.PaymentChannelServerAndroidConnection;
import de.schildbach.wallet.integration.sample.R;

public class PaymentChannelServerActivity extends Activity implements ServiceConnection, View.OnClickListener {

    private WalletAppKit walletAppKit;

    private PaymentChannelConnector paymentChannelConnector;
    private PaymentChannelServerAndroidConnection server;

    private Handler handler;

    private EditText channelSize, minChannelSize, channelExpiryHours;
    private TextView status, paymentTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.payment_channel_server_activity);

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
        minChannelSize = (EditText) findViewById(R.id.channel_min_size);
        status = (TextView) findViewById(R.id.channel_status);
        paymentTotal = (TextView) findViewById(R.id.channel_amount);

        findViewById(R.id.start).setOnClickListener(this);

        bindService(new Intent(this, PaymentChannelService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
                long minValue;
                long maxTime;
                try {
                    maxValue = Long.valueOf(channelSize.getText().toString());
                    minValue = Long.valueOf(minChannelSize.getText().toString());
                    maxTime = (long) (Float.valueOf(channelExpiryHours.getText().toString()) * 3600);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_LONG).show();
                    break;
                }

                findViewById(R.id.start).setEnabled(false);

                startChannel(Coin.valueOf(maxValue), Coin.valueOf(minValue), maxTime);
                break;
        }
    }

    private void startChannel(Coin maxValue, Coin minChannelSize, long maxTime) {
        try {
            server = new PaymentChannelServerAndroidConnection(paymentChannelConnector,
                    new PaymentChannelServerAndroidConnection.EventHandler() {
                        @Override
                        public void channelOpen(final Sha256Hash channelId) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    status.setText("Channel open: " + channelId);
                                }
                            });
                        }

                        @Nullable
                        @Override
                        public ListenableFuture<ByteString> paymentIncrease(Coin by, final Coin to, ByteString info) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    paymentTotal.setText(to.toFriendlyString());
                                }
                            });
                            return null;
                        }

                        @Override
                        public void channelClosed(final PaymentChannelCloseException.CloseReason reason) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    status.setText("Channel closed: " + reason);
                                }
                            });
                        }
                    },
                    walletAppKit.peerGroup(),
                    walletAppKit.wallet(),
                    minChannelSize,
                    maxValue,
                    null,
                    maxTime);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
