/*
 * Copyright 2013 Google Inc.
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

package de.schildbach.wallet.util;

import java.util.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionBroadcaster;
import com.google.bitcoin.core.VerificationException;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used by payment channel clients to broadcast contract and refund transactions when the channel reaches its expire
 * time - stores transactions in the wallet and attempts to open up Peer connections to broadcast them on the network.
 */
public class ChainServiceTransactionBroadcaster implements TransactionBroadcaster {
	private static final Logger log = LoggerFactory.getLogger(ChainServiceTransactionBroadcaster.class);
	private final WalletApplication application;

	public ChainServiceTransactionBroadcaster(WalletApplication application) {
		this.application = application;
	}

	private final List<Transaction> txnToBroadcast = new LinkedList<Transaction>();
	private final Set<ListenableFuture<Transaction>> setBroadcastFutures = Collections.synchronizedSet(new HashSet<ListenableFuture<Transaction>>());
	private BlockchainService service = null;

	// Called with no locks held, on a bitcoinj Peer thread
	private synchronized void checkAllTransactionsBroadcasted() {
		if (setBroadcastFutures.isEmpty() && txnToBroadcast.isEmpty() && service != null) {
			application.unbindService(serviceConnection);
			service = null;
		}
	}

	// May be called from any thread
	private synchronized void doBroadcast(final Transaction tx) {
		log.info("Doing channel close broadcast for transaction with hash " + tx.getHashAsString());
		final ListenableFuture<Transaction> broadcastFuture;
		if (service != null)
			broadcastFuture = service.broadcastTransaction(tx);
		else
			broadcastFuture = null;
		if (broadcastFuture != null) {
			setBroadcastFutures.add(broadcastFuture);
			Futures.addCallback(broadcastFuture, new FutureCallback<Transaction>() {
				@Override
				public void onSuccess(Transaction result) {
					log.info("Channel close transaction broadcast successfully: " + tx.getHashAsString());
					setBroadcastFutures.remove(broadcastFuture);
					checkAllTransactionsBroadcasted();
				}

				@Override
				public void onFailure(Throwable t) {
					log.error("Channel close transaction failed to broadcast: " + tx.getHashAsString());
				}
			});
		} else
			log.info("Blockchain service is not connected, skipping channel close broadcast of transaction " + tx.getHashAsString());

		try {
			application.getWallet().receivePending(tx, new LinkedList<Transaction>());
		} catch (VerificationException e) {
			log.error("Channel close broadcast failed to commit to wallet.");
		}
	}

	// These methods are called from the application's main thread
	private final ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(final ComponentName name, final IBinder binder) {
			log.info("Bound to blockchain service...broadcasting pending channel close transactions");
			synchronized (ChainServiceTransactionBroadcaster.this) {
				service = ((BlockchainServiceImpl.LocalBinder) binder).getService();
				for (Transaction tx : txnToBroadcast) {
					doBroadcast(tx);
				}
				txnToBroadcast.clear();
			}
		}

		public void onServiceDisconnected(final ComponentName name) {
			synchronized (ChainServiceTransactionBroadcaster.this) {
				service = null;
			}
		}
	};

	// This may be called from any thread
	@Override
	public synchronized ListenableFuture<Transaction> broadcastTransaction(Transaction tx) {
		log.info("Got channel close broadcast with hash " + tx.getHashAsString());
		if (service == null) {
			txnToBroadcast.add(tx);
			if (!application.bindService(new Intent(application, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE))
				doBroadcast(tx);
		} else
			doBroadcast(tx);
		return null;
	}
}