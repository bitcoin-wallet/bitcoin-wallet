/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.lifecycle.LiveData;
import de.schildbach.wallet.service.BlockchainService;

/**
 * @author Andreas Schildbach
 */
public class BlockchainServiceLiveData extends LiveData<BlockchainService> implements ServiceConnection {
    private final Context context;

    public BlockchainServiceLiveData(final Context context) {
        this.context = context;
    }

    @Override
    protected void onActive() {
        context.bindService(new Intent(context, BlockchainService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onInactive() {
        context.unbindService(this);
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        setValue(((BlockchainService.LocalBinder) service).getService());
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        setValue(null);
    }
}
