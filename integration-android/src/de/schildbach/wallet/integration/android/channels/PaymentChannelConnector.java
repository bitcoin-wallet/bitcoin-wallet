package de.schildbach.wallet.integration.android.channels;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import java.io.Closeable;
import java.io.IOException;

import de.schildbach.wallet.channels.IPaymentChannelCallbacks;
import de.schildbach.wallet.channels.IPaymentChannelClientInstance;
import de.schildbach.wallet.channels.IPaymentChannelServerInstance;
import de.schildbach.wallet.channels.IPaymentChannels;

/**
 * Manages a connection to a Bitcoin wallet via its exposed remote service for processing
 * micropayments.
 */
public class PaymentChannelConnector implements Closeable {
    private static final Intent SERVICE_INTENT =
            new Intent("de.schildbach.wallet.channels.PaymentChannelService");

    private final Context context;

    private IPaymentChannels boundService;
    private final Object serviceMonitor = new Object();

    /**
     * Connects to a Bitcoin Wallet to set up payment channels.
     * @param context
     * @throws PaymentChannelConnectionException If we couldn't connect to the service
     */
    public PaymentChannelConnector(Context context) throws PaymentChannelConnectionException {
        this.context = context;
        // We don't care if the service we are connecting to is a legitimate version of Bitcoin
        // Wallet as the protocol provides trust-free payments.
        if (!context.bindService(SERVICE_INTENT, serviceConnection, Context.BIND_AUTO_CREATE)) {
            // We couldn't bind to the service
            try {
                close();
            } catch (IOException e) { }
            throw new PaymentChannelConnectionException();
        }
    }

    public static class PaymentChannelConnectionException extends IOException {

    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            synchronized (serviceMonitor) {
                boundService = IPaymentChannels.Stub.asInterface(binder);
                serviceMonitor.notifyAll();
            }
            onConnectedToWallet();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            synchronized (serviceMonitor) {
                boundService = null;
            }
        }
    };

    @Override
    public void close() throws IOException {
        context.unbindService(serviceConnection);
    }

    /**
     * Called when a connection to Bitcoin Wallet has been established.
     */
    protected void onConnectedToWallet() { }

    public static abstract class PaymentChannelCallbacks extends IPaymentChannelCallbacks.Stub {

    }

    /**
     * Starts the creation of a payment channel to send money to the Bitcoin wallet app.
     * @param callbacks The callbacks to be called when a message is received.
     */
    public IPaymentChannelServerInstance createChannelToWallet(PaymentChannelCallbacks callbacks)
            throws RemoteException, InterruptedException {
        synchronized (this) {
            while (boundService == null) {
                synchronized (serviceMonitor) {
                    serviceMonitor.wait();
                }
            }
        }
        return boundService.createChannelToWallet(callbacks);
    }

    /**
     * Starts the creation of a payment channel requesting money from the Bitcoin Wallet app.
     * @param callbacks The callbacks to be called when a message is received.
     * @param requestedMaxValue The maximum value that we wish to have for this channel.
     * @param serverId If re-opening a channel, the ID of the channel to re-open.
     * @param requestedTimeWindow The time window that we would like the channel to be open for, in
     *                            seconds.
     */
    public IPaymentChannelClientInstance createChannelFromWallet(
            PaymentChannelCallbacks callbacks,
            long requestedMaxValue,
            byte[] serverId,
            long requestedTimeWindow
            )
            throws RemoteException, InterruptedException {
        synchronized (serviceMonitor) {
            while (boundService == null) {
                synchronized (serviceMonitor) {
                    serviceMonitor.wait();
                }
            }
        }
        return boundService.createChannelFromWallet(
                callbacks,
                requestedMaxValue,
                serverId,
                requestedTimeWindow);
    }
}
