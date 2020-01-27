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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class DialogBuilder extends AlertDialog.Builder {
    private final View customTitle;
    private final ImageView iconView;
    private final TextView titleView;

    public static DialogBuilder dialog(final Context context, @StringRes final int titleResId,
                                       @StringRes final int messageResId,
                                       final Object... messageArgs) {
        return dialog(context, titleResId, context.getString(messageResId, messageArgs));
    }

    public static DialogBuilder dialog(final Context context, @StringRes final int titleResId, final CharSequence message) {
        final DialogBuilder builder = new DialogBuilder(context);
        if (titleResId != 0)
            builder.setTitle(titleResId);
        builder.setMessage(message);
        return builder;
    }

    public static DialogBuilder warn(final Context context, @StringRes final int titleResId,
                                     @StringRes final int messageResId,
                                     final Object... messageArgs) {
        return warn(context, titleResId, context.getString(messageResId, messageArgs));
    }

    public static DialogBuilder warn(final Context context, @StringRes final int titleResId,
                                     final CharSequence message) {
        final DialogBuilder builder = dialog(context, titleResId, message);
        builder.setIcon(R.drawable.ic_warning_grey600_24dp);
        return builder;
    }

    public static DialogBuilder custom(final Context context, @StringRes final int titleResId, final View view) {
        final DialogBuilder builder = new DialogBuilder(context);
        if (titleResId != 0)
            builder.setTitle(titleResId);
        builder.setView(view);
        return builder;
    }

    protected DialogBuilder(final Context context) {
        super(context, R.style.My_Theme_Dialog);
        this.customTitle = LayoutInflater.from(context).inflate(R.layout.dialog_title, null);
        this.iconView = customTitle.findViewById(android.R.id.icon);
        this.titleView = customTitle.findViewById(android.R.id.title);
    }

    @Override
    public DialogBuilder setIcon(final Drawable icon) {
        if (icon != null) {
            setCustomTitle(customTitle);
            iconView.setImageDrawable(icon);
            iconView.setVisibility(View.VISIBLE);
        }

        return this;
    }

    @Override
    public DialogBuilder setIcon(@DrawableRes final int iconResId) {
        if (iconResId != 0) {
            setCustomTitle(customTitle);
            iconView.setImageResource(iconResId);
            iconView.setVisibility(View.VISIBLE);
        }

        return this;
    }

    @Override
    public DialogBuilder setTitle(final CharSequence title) {
        if (title != null) {
            setCustomTitle(customTitle);
            titleView.setText(title);
        }

        return this;
    }

    @Override
    public DialogBuilder setTitle(@StringRes final int titleResId) {
        if (titleResId != 0) {
            setCustomTitle(customTitle);
            titleView.setText(titleResId);
        }

        return this;
    }

    @Override
    public DialogBuilder setMessage(final CharSequence message) {
        super.setMessage(message);

        return this;
    }

    @Override
    public DialogBuilder setMessage(@StringRes final int messageResId) {
        super.setMessage(messageResId);

        return this;
    }

    public DialogBuilder singleDismissButton(@Nullable final OnClickListener dismissListener) {
        setNeutralButton(R.string.button_dismiss, dismissListener);

        return this;
    }
}
