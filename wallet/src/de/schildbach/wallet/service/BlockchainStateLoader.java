/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.service;

import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

/**
 * @author Andreas Schildbach
 */
public class BlockchainStateLoader extends AsyncTaskLoader<BlockchainState> {
    private final LocalBroadcastManager broadcastManager;
    private BlockchainService service;

    private static final Logger log = LoggerFactory.getLogger(BlockchainStateLoader.class);

    public BlockchainStateLoader(final Context context) {
        super(context);

        this.broadcastManager = LocalBroadcastManager.getInstance(context.getApplicationContext());
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        broadcastManager.registerReceiver(broadcastReceiver,
                new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));

        final Context context = getContext();
        context.bindService(new Intent(context, BlockchainServiceImpl.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStopLoading() {
        broadcastManager.unregisterReceiver(broadcastReceiver);

        super.onStopLoading();
    }

    @Override
    public BlockchainState loadInBackground() {
        final BlockchainState blockchainState = service.getBlockchainState();

        getContext().unbindService(serviceConnection);

        return blockchainState;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = ((BlockchainServiceImpl.LocalBinder) binder).getService();

            forceLoad();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent broadcast) {
            try {
                deliverResult(BlockchainState.fromIntent(broadcast));
            } catch (final RejectedExecutionException x) {
                log.info("rejected execution: " + BlockchainStateLoader.this.toString());
            }
        }
    };
}
