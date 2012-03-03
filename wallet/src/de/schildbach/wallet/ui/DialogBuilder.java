/*
 * Copyright 2014 the original author or authors.
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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class DialogBuilder extends AlertDialog.Builder
{
	private final View customTitle;
	private final ImageView iconView;
	private final TextView titleView;

	public DialogBuilder(final Context context)
	{
		super(context);

		setInverseBackgroundForced(true);

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
	public DialogBuilder setIcon(final int iconId)
	{
		if (iconId != 0)
		{
			setCustomTitle(customTitle);
			iconView.setImageResource(iconId);
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
	public DialogBuilder setTitle(final int titleId)
	{
		if (titleId != 0)
		{
			setCustomTitle(customTitle);
			titleView.setText(titleId);
		}

		return this;
	}

	@Override
	public DialogBuilder setMessage(CharSequence message)
	{
		super.setMessage(message);

		return this;
	}

	@Override
	public DialogBuilder setMessage(int messageId)
	{
		super.setMessage(messageId);

		return this;
	}
}
