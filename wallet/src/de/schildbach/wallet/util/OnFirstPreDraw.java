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

package de.schildbach.wallet.util;

import android.view.View;
import android.view.ViewTreeObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static androidx.core.util.Preconditions.checkNotNull;

/**
 * @author Andreas Schildbach
 */
public class OnFirstPreDraw implements ViewTreeObserver.OnPreDrawListener {
    public interface Callback {
        boolean onFirstPreDraw();
    }

    public static void listen(final View view, final Callback callback) {
        new OnFirstPreDraw(view.getViewTreeObserver(), callback);
    }

    private final ViewTreeObserver viewTreeObserver;
    private final Callback callback;
    private final AtomicBoolean fired = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(OnFirstPreDraw.class);

    private OnFirstPreDraw(final ViewTreeObserver viewTreeObserver, final Callback callback) {
        this.viewTreeObserver = viewTreeObserver;
        this.callback = checkNotNull(callback);
        viewTreeObserver.addOnPreDrawListener(this);
    }

    @Override
    public boolean onPreDraw() {
        if (viewTreeObserver.isAlive())
            viewTreeObserver.removeOnPreDrawListener(this);
        else
            log.debug("ViewTreeObserver has died, cannot remove listener");
        if (!fired.getAndSet(true))
            return callback.onFirstPreDraw();
        return true;
    }
}
