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

import android.content.Context;
import androidx.annotation.StringRes;

/**
 * @author Andreas Schildbach
 */
public class DialogEvent extends Event<DialogEvent.Params> {
    public static DialogEvent dialog(@StringRes final int titleResId, @StringRes final int messageResId,
                                     final Object... messageArgs) {
        return new DialogEvent(false, titleResId, messageResId, messageArgs);
    }

    public static DialogEvent warn(@StringRes final int titleResId, @StringRes final int messageResId,
                                   final Object... messageArgs) {
        return new DialogEvent(true, titleResId, messageResId, messageArgs);
    }

    private DialogEvent(final boolean warn, @StringRes final int titleResId, @StringRes final int messageResId,
                        final Object... messageArgs) {
        super(new Params(warn, titleResId, messageResId, messageArgs));
    }

    public static class Observer extends Event.Observer<Params> {
        private Context context;

        public Observer(final Context context) {
            this.context = context;
        }

        @Override
        protected final void onEvent(final Params params) {
            onDialogEvent(params.warn, params.titleResId, params.messageResId, params.messageArgs);
        }

        protected void onDialogEvent(final boolean warn, @StringRes final int titleResId,
                                     @StringRes final int messageResId, final Object[] messageArgs) {
            final DialogBuilder dialog;
            if (warn)
                dialog = DialogBuilder.warn(context, titleResId, messageResId, messageArgs);
            else
                dialog = DialogBuilder.dialog(context, titleResId, messageResId, messageArgs);
            onBuildButtons(dialog);
            dialog.show();
        }

        protected void onBuildButtons(final DialogBuilder dialog) {
            dialog.singleDismissButton(null);
        }
    }

    protected static class Params {
        private final boolean warn;
        @StringRes
        private final int titleResId;
        @StringRes
        private final int messageResId;
        private final Object[] messageArgs;

        private Params(final boolean warn, @StringRes final int titleResId, @StringRes final int messageResId,
                       final Object... messageArgs) {
            this.warn = warn;
            this.titleResId = titleResId;
            this.messageResId = messageResId;
            this.messageArgs = messageArgs;
        }
    }
}
