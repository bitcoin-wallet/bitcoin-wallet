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

import android.os.AsyncTask;
import android.os.RemoteException;

import com.google.common.collect.Queues;

import org.bitcoin.paymentchannel.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * A class to handle all of the masquerading between threads of the service AIDL interface.
 */
public abstract class AsyncPaymentChannelTask extends AsyncTask<Void, Protos.TwoWayChannelMessage, Void> {

    private static final Logger log = LoggerFactory.getLogger(AsyncPaymentChannelTask.class);

    public static final int MESSAGE_TWO_WAY_CHANNEL_MESSAGE = 1;
    public static final int MESSAGE_CONNECTION_CLOSED = 2;

    private final IPaymentChannelCallbacks callbacks;

    private final BlockingQueue<Message> threadMessageQueue = Queues.newArrayBlockingQueue(2);

    public AsyncPaymentChannelTask(IPaymentChannelCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    protected Void doInBackground(Void... params) {
        while (!isCancelled()) {
            try {
                handleThreadMessage(threadMessageQueue.take());
            } catch (InterruptedException e) {
                log.info("Interrupted while waiting on message queue", e);
            }
        }
        return null;
    }

    protected abstract void handleThreadMessage(Message message);

    public void postMessage(Protos.TwoWayChannelMessage message) {
        log.debug("Receiving TwoWayChannelMessage {}", message.getType());
        sendMessageToThread(MESSAGE_TWO_WAY_CHANNEL_MESSAGE, message);
    }

    public void postConnectionClosed() {
        sendMessageToThread(MESSAGE_CONNECTION_CLOSED);
    }

    protected void sendMessageToThread(Message message) {
        threadMessageQueue.add(message);
    }

    protected void sendMessageToThread(int what) {
        sendMessageToThread(new Message(what));
    }

    protected void sendMessageToThread(int what, Object obj) {
        sendMessageToThread(new Message(what, obj));
    }

    protected void closeConnection() {
        try {
            callbacks.closeConnection();
        } catch (RemoteException e) {
            log.warn("Failed to close cancelled connection", e);
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        closeConnection();
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

    public static class Message {
        public int what;
        public Object obj;

        public Message(int what) {
            this.what = what;
        }

        public Message(int what, Object obj) {
            this(what);
            this.obj = obj;
        }
    }
}
