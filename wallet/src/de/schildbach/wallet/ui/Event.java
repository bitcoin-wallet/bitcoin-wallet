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

package de.schildbach.wallet.ui;

import static androidx.core.util.Preconditions.checkState;

/**
 * @author Andreas Schildbach
 */
public class Event<T> {
    private final T content;
    private boolean hasBeenHandled = false;

    public static Event<Void> simple() {
        return new Event<>(null);
    }

    public Event(final T content) {
        this.content = content;
    }

    public boolean hasBeenHandled() {
        return hasBeenHandled;
    }

    public T getContentOrThrow() {
        checkState(!hasBeenHandled);
        hasBeenHandled = true;
        return content;
    }

    public T getContentIfNotHandled() {
        if (hasBeenHandled)
            return null;
        hasBeenHandled = true;
        return content;
    }

    public static abstract class Observer<T> implements androidx.lifecycle.Observer<Event<T>> {
        @Override
        public final void onChanged(final Event<T> event) {
            if (!event.hasBeenHandled())
                onEvent(event.getContentOrThrow());
        }

        protected abstract void onEvent(final T content);
    }
}
