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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.util;

import static android.support.v4.util.Preconditions.checkNotNull;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.view.View;
import android.view.ViewTreeObserver;

/**
 * @author Andreas Schildbach
 */
public class OnFirstGlobalLayout implements ViewTreeObserver.OnGlobalLayoutListener {
    public static interface Callback {
        void onFirstGlobalLayout();
    }

    public static void listen(final View view, final Callback callback) {
        new OnFirstGlobalLayout(view.getViewTreeObserver(), callback);
    }

    private final ViewTreeObserver viewTreeObserver;
    private final Callback callback;
    private final AtomicBoolean fired = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(OnFirstGlobalLayout.class);

    private OnFirstGlobalLayout(final ViewTreeObserver viewTreeObserver, final Callback callback) {
        this.viewTreeObserver = viewTreeObserver;
        this.callback = checkNotNull(callback);
        viewTreeObserver.addOnGlobalLayoutListener(this);
    }

    @Override
    public void onGlobalLayout() {
        if (viewTreeObserver.isAlive())
            viewTreeObserver.removeOnGlobalLayoutListener(this);
        else
            log.debug("ViewTreeObserver has died, cannot remove listener");
        if (fired.getAndSet(true))
            return;
        callback.onFirstGlobalLayout();
    }
}
