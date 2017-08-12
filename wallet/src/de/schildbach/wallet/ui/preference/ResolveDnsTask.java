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

package de.schildbach.wallet.ui.preference;

import java.net.InetAddress;
import java.net.UnknownHostException;

import android.os.Handler;
import android.os.Looper;

/**
 * @author Andreas Schildbach
 */
public abstract class ResolveDnsTask {
    private final Handler backgroundHandler;
    private final Handler callbackHandler;

    public ResolveDnsTask(final Handler backgroundHandler) {
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
    }

    public final void resolve(final String hostname) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final InetAddress address = InetAddress.getByName(hostname); // blocks on network

                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onSuccess(address);
                        }
                    });
                } catch (final UnknownHostException x) {
                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onUnknownHost();
                        }
                    });
                }
            }
        });
    }

    protected abstract void onSuccess(InetAddress address);

    protected abstract void onUnknownHost();
}
