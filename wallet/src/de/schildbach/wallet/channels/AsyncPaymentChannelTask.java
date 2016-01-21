package de.schildbach.wallet.channels;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;

import com.google.common.util.concurrent.SettableFuture;

import org.bitcoin.paymentchannel.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/**
 * A class to handle all of the masquerading between threads of the service AIDL interface.
 */
public abstract class AsyncPaymentChannelTask extends AsyncTask<Void, Protos.TwoWayChannelMessage, Void> {

    private static final Logger log = LoggerFactory.getLogger(AsyncPaymentChannelTask.class);

    public static final int MESSAGE_TWO_WAY_CHANNEL_MESSAGE = 1;
    public static final int MESSAGE_CONNECTION_CLOSED = 2;

    private final SettableFuture<Handler> handlerFuture = SettableFuture.create();
    private final IPaymentChannelCallbacks callbacks;

    public AsyncPaymentChannelTask(IPaymentChannelCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    protected Void doInBackground(Void... params) {
        Looper.prepare();
        handlerFuture.set(createHandler());
        return null;
    }

    protected Handler getHandler() {
        try {
            return handlerFuture.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            // Won't happen
            return null;
        }
    }

    protected abstract Handler createHandler();

    public void postMessage(Protos.TwoWayChannelMessage message) {
        getHandler().sendMessage(Message.obtain(getHandler(), MESSAGE_TWO_WAY_CHANNEL_MESSAGE, message));
    }

    public void postConnectionClosed() {
        getHandler().sendMessage(Message.obtain(getHandler(), MESSAGE_CONNECTION_CLOSED));
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        try {
            callbacks.closeConnection();
        } catch (RemoteException e) {
            log.warn("Failed to close cancelled connection", e);
        }
    }

    @Override
    protected void onProgressUpdate(Protos.TwoWayChannelMessage... messages) {
        super.onProgressUpdate(messages);
        for (Protos.TwoWayChannelMessage message : messages) {
            try {
                callbacks.sendMessage(message.toByteArray());
            } catch (RemoteException e) {
                try {
                    callbacks.closeConnection();
                } catch (RemoteException e1) {
                    log.warn("Failed to close failed connection", e);
                }
            }
        }
    }
}
