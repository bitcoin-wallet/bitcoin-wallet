/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.integration.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoin.ChannelConstants;
import org.bitcoin.IChannelCallback;
import org.bitcoin.IChannelRemoteService;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>This class manages a connection with a wallet client and a payment channel on top.</p>
 *
 * <p>It is designed to hide some of the complexity when the wallet app goes away or the connection dies by reconnecting
 * on-demand. To avoid over-aggressive reconnection, some calls may fail and the client is expected to keep a queue of
 * pending requests which it can resend when the channelOpen() event triggers. Additionally, the client is expected to
 * disconnect itself if a request fails and channelOpen() does not trigger within some timeout.</p>
 *
 * <p>Note that all callbacks are asynchronous and will generally never be called until the generating method returns.
 * </p>
 */
public final class BitcoinPaymentChannelManager
{
	private static final String TAG = BitcoinPaymentChannelManager.class.getName();

	// A ThreadPool which runs payment channel calls to keep them off the main thread
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	// The application context used to call bindService
	private final Context context;
	// The remote service which is provided when we make a connection
	private IChannelRemoteService remoteService = null;

	// The API user's listener
	private final ChannelListener clientListener;

	private String channelCookie = null;

	private final boolean testNet;

	enum ChannelState {
		INIT,
		CHANNEL_OPENING,
		CHANNEL_OPEN,
		CHANNEL_INTERRUPTED,
		CHANNEL_CLOSED
	}
	private ChannelState state;
	// Keeps track of the contract hash to use for reconnection
	private byte[] previousContractHash = null;

	private final String hostId;
	private final IChannelCallback channelListener;

	// Visible for testing, would otherwise be inlined in connection.onServiceConnected
	synchronized void onServiceConnected(IChannelRemoteService remoteService) {
		this.remoteService = remoteService;
		Log.d(TAG, "Successfully bound to a wallet app, attempting to call openConnection");
		try {
			channelCookie = remoteService.openConnection(channelListener, hostId);
		} catch (RemoteException e) {
			// If we fail to call the remote openConnection, just close everything and let the client handle it
			Log.e(TAG, "Got RemoteException while calling openConnection on bound wallet app", e);
			disconnectFromWallet(true);
		}
		if (channelCookie == null) {
			Log.e(TAG, "Channel service interpreted our openConnection as an invalid request");
			disconnectFromWallet(true);
		}
	}

	// The connection to the remote service (visible for testing)
	public ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			BitcoinPaymentChannelManager.this.onServiceConnected(IChannelRemoteService.Stub.asInterface(service));
		}

		public void onServiceDisconnected(ComponentName name) {
			// We handle service disconnection as a temporary error and will retry connection sometime later, ensuring
			// at that time that we connect to the same server and hoping we get the same channel back.
			Log.i(TAG, "Wallet payment channel service disconnected, channel interrupted");
			synchronized (BitcoinPaymentChannelManager.this) {
				remoteService = null;
				state = ChannelState.CHANNEL_INTERRUPTED;
			}
			// Use the executor service to ensure callbacks are processed in order
			executorService.submit(new Runnable() {
				public void run() {
					clientListener.channelInterrupted();
				}
			});
		}
	};

	// Attempts to connect and returns true if we are already connected
	private synchronized boolean getConnected(boolean openingOk) {
		if ((state == ChannelState.CHANNEL_OPENING && !openingOk) || state == ChannelState.CHANNEL_CLOSED)
			return false;
		if (remoteService == null) {
			if (!context.bindService(new Intent("org.bitcoin.PAYMENT_CHANNEL" + (testNet ? "_TEST" : "")), connection, Context.BIND_AUTO_CREATE)) {
				Log.w(TAG, "No wallet app installed while attempting to bind to one: did you call prepare()?");
				boolean runChannelClosed = state != ChannelState.CHANNEL_CLOSED;
				state = ChannelState.CHANNEL_CLOSED;
				if (runChannelClosed)
					executorService.submit(new Runnable() {
						public void run() {
                            clientListener.channelClosedOrNotOpened(ChannelListener.CloseReason.NO_WALLET_APP);
						}
					});
			} else {
				state = ChannelState.CHANNEL_OPENING;
				Log.d(TAG, "Attempting to connect to wallet service.");
			}
			return false; // Next time wait until you get the connected callback
		}
		return state == ChannelState.CHANNEL_OPEN || state == ChannelState.CHANNEL_OPENING;
	}

	/**
	 * Attempts to ensure there is an open channel, trying to bind to the wallet and open a payment channel with the
	 * server if there is not already one open.
	 */
	public void connect() {
		getConnected(false);
	}

	// Unbinds the service itself
	private synchronized void doUnbind() {
		if (connection != null)
			try {
				context.unbindService(connection);
			} catch (IllegalArgumentException e) {
				// This is expected as we are often not connected and will get
				// "java.lang.IllegalArgumentException: Service not registered: ..."
			}
		remoteService = null;
	}

	/**
	 * Closes the connection to the wallet app and optionally also asks for the payment channel to be closed too. If the
	 * connection to the server dies, call this to enable the state to be resumed later. May generate a
	 * {@link ChannelListener#channelClosedOrNotOpened} or {@link ChannelListener#channelInterrupted()} callback if
	 * relevant.
	 */
	public synchronized void disconnectFromWallet(boolean closeChannel) {
		if (closeChannel) {
            boolean runChannelClosed = state != ChannelState.CHANNEL_CLOSED;
            state = ChannelState.CHANNEL_CLOSED;
			Log.d(TAG, "Attempting to close channel and associated service binding");
			try {
				if (remoteService != null)
					remoteService.closeConnection(channelCookie);
			} catch (RemoteException e) {
				// In this case it is not worth trying to reconnect just to close the channel, we leave it to the wallet
				// to figure it out.
			}
			if (runChannelClosed)
				executorService.submit(new Runnable() {
					public void run() {
                        clientListener.channelClosedOrNotOpened(ChannelListener.CloseReason.CLIENT_REQUESTED_CLOSE);
					}
				});
		} else {
			Log.d(TAG, "Closing service binding for payment channel");
			try {
				if (remoteService != null)
					remoteService.disconnectFromWallet(channelCookie);
			} catch (RemoteException e) {
				// In this case it is not worth trying to reconnect just to close the channel, we leave it to the wallet
				// to figure it out.
			}
		}
		doUnbind();
		if (state != ChannelState.CHANNEL_CLOSED)
			executorService.submit(new Runnable() {
				public void run() {
					clientListener.channelInterrupted();
				}
			});
	}

	private void resetConnection() {
		Log.i(TAG, "Resetting connection to wallet service - closing service binding and attempting to reopen it");
		disconnectFromWallet(false);
		getConnected(false);
	}

	/**
	 * Called by {@link BitcoinPaymentChannelManager#prepare(android.content.Context, long, boolean, boolean, int, de.schildbach.wallet.integration.android.BitcoinPaymentChannelManager.PrepareCallback)}
	 * to signal completion of the preparation steps, or if invokeUI was set to false then notifyUser is called with
	 * a brief description of what we need them to do. If you're calling prepare from a foreground activity, then
	 * you should set invokeUI to true.
	 */
	public static interface PrepareCallback {
		public void success();

		enum UIRequestReason {
			NEED_WALLET_APP,
			NEED_AUTH
		}
		public void notifyUser(UIRequestReason reason);
	}

	/**
	 * <p>This should be called before you try to use payment channels from your app for the given server for the
	 * first time. It may redirect the user to the market to install a wallet, or it may show a permission granting
	 * activity if needed - for instance because you never used channels before from your app, or because you ran out
	 * of money and the user needs to grant authorization to spend more.</p>
	 *
	 * <p>You may have to invoke this method multiple times. Do it when you want to make a payment and also from
	 * your activities onActivityResult method (but only if the resultCode == Activity.RESULT_OK so the user doesn't
	 * get stuck in an infinite loop if they cancel at any point).</p>
	 *
	 * <p>Eventually your callback is invoked either to indicate success, or to indicate if user intervention
	 * is necessary: this is only done if invokeUI was false. If invokeUI is true, the context must be an instance
	 * of Activity. If invokeUI is false, it can be any Context.</p>
	 *
	 * <p>Because money gets spent over time, and because the user might forcibly close your payment channel or a
	 * double spend could occur, etc, you could call prepare before using openConnection and making the first payment.
	 * In particular use it after your app is resumed by the OS. If you're not in the foreground then set invokeUI
	 * to false so you don't interrupt what the user is doing, and if you get notifyUser in the callback
	 * put a notification in the notification area instead so the user can re-authorize your app when it's convenient
	 * for them.</p>
	 *
	 * @param activity Your current activity. Because it may require user interaction, you can only call this from a UI.
	 * @param minValue Amount of money you want to be able to spend up to and minimum the user must allow to be locked
	 *                 into the payment channel. This must be the same as (or higher than) the server's minimum.
	 * @param invokeUI Whether to open up activities required to get authorization or not.
	 * @param testNet If true, attempts to find a wallet running testnet instead of the regular Bitcoin network
	 * @param activityRequestCode You will get this back in onActivityResult so you know it's because of this class.
	 * @param prepareCallback  callback that is invoked with the outcome.
	 */
	public static void prepare(final Context activity, final long minValue, final boolean invokeUI, boolean testNet,
							   final int activityRequestCode, final PrepareCallback prepareCallback) {
		if (invokeUI && !(activity instanceof Activity))
			throw new IllegalArgumentException("Must provide an Activity as context if invokeUI is true.");
		final ServiceConnection conn = new ServiceConnection() {
			public void onServiceConnected(ComponentName componentName, IBinder binder) {
				IChannelRemoteService service = IChannelRemoteService.Stub.asInterface(binder);
				try {
					Intent intent = service.prepare(minValue);
					activity.unbindService(this);
					if (intent == null) {
						prepareCallback.success();
					} else if (invokeUI) {
						// Need to show permissions UI.
						((Activity)activity).startActivityForResult(intent, activityRequestCode);
					} else {
						// Let the caller ask the user whenever is convenient.
						prepareCallback.notifyUser(PrepareCallback.UIRequestReason.NEED_AUTH);
					}
				} catch (RemoteException e) {
					Log.d(TAG, "Unable to talk to wallet app to check permissions", e);
				}
			}

			public void onServiceDisconnected(ComponentName componentName) {
				// Don't care.
				Log.e(TAG, "prepare.onServiceDisconnected()");
			}
		};
		if (!activity.bindService(new Intent("org.bitcoin.PAYMENT_CHANNEL" + (testNet ? "_TEST" : "")),
				conn, Context.BIND_AUTO_CREATE)) {
			if (invokeUI) {
				redirectToDownload((Activity)activity, activityRequestCode);
			} else {
				prepareCallback.notifyUser(PrepareCallback.UIRequestReason.NEED_WALLET_APP);
			}
		}
	}

	/**
	 * <p>Opens a payment channel to the given host and port through an installed wallet app. It will bind to the
	 * remote wallet app and start sending RPCs, so best not do this on the UI thread.</p>
	 *
	 * <p>You probably want to call {@link BitcoinPaymentChannelManager#connect()} after this to initiate the channel.
	 * </p>
	 *
	 * @param context The application context which is used to bind to the remote service
	 * @param hostId A unique id which identifies the host. It is generally safe to just use host+port as long as you do
	 *               not expect to see multiple servers which have the same host/port.
	 * @param testNet If true, attempts to find a wallet running testnet instead of the regular Bitcoin network. Note
	 *                that this must be the same as the call to {@link de.schildbach.wallet.integration.android.BitcoinPaymentChannelManager#prepare(android.content.Context, long, boolean, boolean, int, de.schildbach.wallet.integration.android.BitcoinPaymentChannelManager.PrepareCallback)}
	 * @param clientListener The callback handler which will receive callbacks as long as the channel is alive
	 */
	public BitcoinPaymentChannelManager(final Context context, String hostId, boolean testNet, final ChannelListener clientListener) {
		this.hostId = hostId;
		this.clientListener = clientListener;
		this.testNet = testNet;

		this.channelListener = new IChannelCallback.Stub() {
			public void channelOpen(final byte[] contractHash) throws RemoteException {
				synchronized (BitcoinPaymentChannelManager.this) {
					if (previousContractHash == null) {
						Log.i(TAG, "New channel successfully opened");
						previousContractHash = contractHash;
					} else if (!Arrays.equals(previousContractHash, contractHash)) {
						Log.i(TAG, "New channel opened with different contract hash - closing");
						disconnectFromWallet(true);
						return;
					} else
						Log.d(TAG, "Channel successfully reopened");
					state = ChannelState.CHANNEL_OPEN;
				}
				// Use the executor service to ensure callbacks are processed in order
				executorService.submit(new Runnable() {
					public void run() {
						clientListener.channelOpen(contractHash);
					}
				});
			}

			public void channelOpenFailed() throws RemoteException {
				Log.e(TAG, "Wallet service called channelOpenFailed - closing binding and calling channelClosedOrNotOpened()");
				disconnectFromWallet(true); // Calls clientListener.channelClosedOrNotOpened() for us
			}

			public void sendProtobuf(final byte[] protobuf) throws RemoteException {
				// Use the executor service to ensure callbacks are processed in order
				executorService.submit(new Runnable() {
					public void run() {
						clientListener.sendProtobuf(protobuf);
					}
				});
			}

			public void closeConnection(final int reason) throws RemoteException {
				doUnbind();
				boolean runChannelClosed;
				synchronized (BitcoinPaymentChannelManager.this) {
					runChannelClosed = state != ChannelState.CHANNEL_CLOSED;
					state = ChannelState.CHANNEL_CLOSED;
				}
				// Use the executor service to ensure callbacks are processed in order
				if (runChannelClosed) {
                    final ChannelListener.CloseReason r = ChannelListener.CloseReason.from(reason);
					executorService.submit(new Runnable() {
						public void run() {
							clientListener.channelClosedOrNotOpened(r);
						}
					});
                }
			}
		};

		this.context = context;
	}

	private synchronized long doSendMoney(long amount) {
		if (!getConnected(false)) {
			Log.i(TAG, "sendMoney called while not connected, connecting");
			return 0;
		}
		// Channel is currently open and the service is connected (though channel may have disconnected from server,
		// which will trigger a ChannelException)
		try {
			long returnValue = remoteService.payServer(channelCookie, amount);

			if (returnValue > ChannelConstants.RESULT_OK) {
				// If amount is too small to pay, just give up and dont pay anything
				if (amount <= returnValue) {
					Log.e(TAG, "Payment channel service failed to pay because amount was to small (ie cost too much in fees)");
					return 0;
				}

				try {
					Log.e(TAG, "Not enough value left in channel for sendMoney call - spending the rest and closing channel");
					// Try to send the remaining value left in the channel...
					long returnValue2 = remoteService.payServer(channelCookie, returnValue);
					// ...and open a new channel to pay the rest
					disconnectFromWallet(true);
					if (returnValue2 == ChannelConstants.RESULT_OK) {
						Log.i(TAG, "Successfully spent the remaining value in channel - " + returnValue);
						return returnValue;
					} else {
						Log.e(TAG, "Error spending the remaining value in the channel - service returned " + returnValue);
						return 0;
					}
				} catch (RemoteException e) {
					Log.e(TAG, "RemoteException while spending remaining channel value, closing channel and connection", e);
					// Server seems busted
					disconnectFromWallet(true);
					return 0;
				}
			}

			if (returnValue == ChannelConstants.RESULT_OK) {
				Log.d(TAG, "Successfully sent money on channel");
				return amount;
			} else
				Log.e(TAG, "Error sending money on channel, service returned " + returnValue);
		} catch (RemoteException e) {
			// Service connection died
			Log.e(TAG, "RemoteException while sending money on channel", e);
		}
		// Channel has closed, service connection died, etc
		resetConnection();
		return 0;
	}

	/**
	 * <p>Attempts to send the given amount to the server. If there is not enough value left in the channel, the
	 * remaining value will be sent and the application is expected to open a new channel if it wishes to send the rest
	 * (a {@link ChannelListener#channelClosedOrNotOpened(int)} callback will be generated before the future returns).</p>
	 *
	 * <p>If the channel is not currently open, no value is sent and reconnection will be attempted. In this case, the
	 * caller is expected to time the channel out after some reasonable period, just as for initial channel creation</p>
	 *
	 * @return The amount actually sent
	 */
	public ListenableFuture<Long> sendMoney(final long amount) {
		final SettableFuture<Long> future = SettableFuture.create();
		executorService.submit(new Runnable() {
			public void run() {
				future.set(doSendMoney(amount));
			}
		});
		return future;
	}

	/**
	 * Closes this channel, attempts to notify the server that the channel can be fully claimed. After this call, calls
	 * to {@link BitcoinPaymentChannelManager#sendMoney(long)} will fail and no reconnection attempts will be made. A
	 * {@link ChannelListener#channelClosedOrNotOpened} callback will be generated.
	 */
	public void closeChannel() {
		executorService.submit(new Runnable() {
			public void run() {
				disconnectFromWallet(true);
			}
		});
	}

	private static void redirectToDownload(final Activity context, int activityRequestCode) {
		Log.e(TAG, "No wallet app installed, opening link to download bitcoin-wallet");
		Toast.makeText(context, "No Bitcoin application found.\nPlease install Bitcoin Wallet.", Toast.LENGTH_LONG).show();

		final Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=de.schildbach.wallet"));
		final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://code.google.com/p/bitcoin-wallet/downloads/list"));

		final PackageManager pm = context.getPackageManager();
		if (pm.resolveActivity(marketIntent, 0) != null)
			context.startActivityForResult(marketIntent, activityRequestCode);
		else if (pm.resolveActivity(binaryIntent, 0) != null)
			context.startActivityForResult(binaryIntent, activityRequestCode);
		// else out of luck
	}

	/**
	 * Call this method each time a new protobuf message has been received from the server.
	 * @return A future which completes with true if the message was passed to the wallet, or false if the call should
	 *         be retried on channelOpen.
	 */
	public ListenableFuture<Boolean> messageReceived(final byte[] protobuf) {
		final SettableFuture<Boolean> future = SettableFuture.create();
		executorService.submit(new Runnable() {
			public void run() {
				if (!getConnected(true)) {
					Log.i(TAG, "messageReceived called while not connected, connecting");
					future.set(false);
					return;
				}
				try {
					remoteService.messageReceived(channelCookie, protobuf);
					future.set(true);
				} catch (RemoteException e) {
					resetConnection();
					future.set(false);
				}
			}
		});
		return future;
	}
}
