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

package de.schildbach.wallet.ui;

import javax.annotation.Nullable;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class DialogBuilder extends AlertDialog.Builder
{
	private final View customTitle;
	private final ImageView iconView;
	private final TextView titleView;

	public static DialogBuilder warn(final Context context, final int titleResId)
	{
		final DialogBuilder builder = new DialogBuilder(context);
		builder.setIcon(R.drawable.ic_warning_grey600_24dp);
		builder.setTitle(titleResId);
		return builder;
	}

	public DialogBuilder(final Context context)
	{
		super(context, Build.VERSION.SDK_INT < Constants.SDK_LOLLIPOP ? AlertDialog.THEME_HOLO_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);

		this.customTitle = LayoutInflater.from(context).inflate(R.layout.dialog_title, null);
		this.iconView = (ImageView) customTitle.findViewById(android.R.id.icon);
		this.titleView = (TextView) customTitle.findViewById(android.R.id.title);
	}

	@Override
	public DialogBuilder setIcon(final Drawable icon)
	{
		if (icon != null)
		{
			setCustomTitle(customTitle);
			iconView.setImageDrawable(icon);
			iconView.setVisibility(View.VISIBLE);
		}

		return this;
	}

	@Override
	public DialogBuilder setIcon(final int iconResId)
	{
		if (iconResId != 0)
		{
			setCustomTitle(customTitle);
			iconView.setImageResource(iconResId);
			iconView.setVisibility(View.VISIBLE);
		}

		return this;
	}

	@Override
	public DialogBuilder setTitle(final CharSequence title)
	{
		if (title != null)
		{
			setCustomTitle(customTitle);
			titleView.setText(title);
		}

		return this;
	}

	@Override
	public DialogBuilder setTitle(final int titleResId)
	{
		if (titleResId != 0)
		{
			setCustomTitle(customTitle);
			titleView.setText(titleResId);
		}

		return this;
	}

	@Override
	public DialogBuilder setMessage(final CharSequence message)
	{
		super.setMessage(message);

		return this;
	}

	@Override
	public DialogBuilder setMessage(final int messageResId)
	{
		super.setMessage(messageResId);

		return this;
	}

	public DialogBuilder singleDismissButton(@Nullable final OnClickListener dismissListener)
	{
		setNeutralButton(R.string.button_dismiss, dismissListener);

		return this;
	}
}
