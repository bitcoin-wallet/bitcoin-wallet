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

import android.os.Handler;
import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;

/**
 * @author Andreas Schildbach
 */
public abstract class ThrottelingLiveData<T> extends LiveData<T> {
    private final long throttleMs;
    private final Handler handler = new Handler();
    private long lastMessageMs;
    private static final long DEFAULT_THROTTLE_MS = 500;

    public ThrottelingLiveData() {
        this(DEFAULT_THROTTLE_MS);
    }

    public ThrottelingLiveData(final long throttleMs) {
        this.throttleMs = throttleMs;
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        handler.removeCallbacksAndMessages(null);
    }

    @MainThread
    protected void triggerLoad() {
        handler.removeCallbacksAndMessages(null);
        final Runnable runnable = () -> {
            lastMessageMs = System.currentTimeMillis();
            load();
        };
        final long lastMessageAgoMs = System.currentTimeMillis() - lastMessageMs;
        if (lastMessageAgoMs < throttleMs)
            handler.postDelayed(runnable, throttleMs - lastMessageAgoMs);
        else
            runnable.run(); // immediately
    }

    @MainThread
    protected void load() {
        // do nothing by default
    }
}
