/*
 * Copyright 2010 the original author or authors.
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

package de.schildbach.wallet;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class ActionBarFragment extends Fragment
{
	private ViewGroup view;
	private ImageView iconView;
	private TextView primaryTitleView;
	private TextView secondaryTitleView;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		view = (ViewGroup) inflater.inflate(R.layout.action_bar_fragment, container);
		iconView = (ImageView) view.findViewById(R.id.action_bar_icon);
		primaryTitleView = (TextView) view.findViewById(R.id.action_bar_primary_title);
		secondaryTitleView = (TextView) view.findViewById(R.id.action_bar_secondary_title);

		return view;
	}

	public void setIcon(final int iconRes)
	{
		iconView.setImageResource(iconRes);
	}

	public void setPrimaryTitle(final CharSequence title)
	{
		primaryTitleView.setText(title);
	}

	public void setPrimaryTitle(final int titleRes)
	{
		primaryTitleView.setText(titleRes);
	}

	public void setSecondaryTitle(final CharSequence title)
	{
		secondaryTitleView.setText(title);
		secondaryTitleView.setVisibility(title != null ? View.VISIBLE : View.GONE);
		primaryTitleView.setSingleLine(title != null);
	}

	public ImageButton addButton(final int drawableRes)
	{
		final float density = getResources().getDisplayMetrics().density;

		final LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(Math.round(44 * density), LayoutParams.FILL_PARENT, 0f);
		buttonParams.gravity = Gravity.CENTER_VERTICAL;

		final ImageButton button = new ImageButton(getActivity());
		button.setImageResource(drawableRes);
		button.setScaleType(ScaleType.CENTER);
		button.setBackgroundResource(R.drawable.action_bar_background);
		button.setPadding(0, 0, 0, 0);
		view.addView(button, 2, buttonParams);

		final LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(Math.round(1 * density), LayoutParams.FILL_PARENT, 0f);

		final ImageView sep1 = new ImageView(getActivity());
		sep1.setImageDrawable(new ColorDrawable(Color.parseColor("#44ffffff")));
		view.addView(sep1, 2, sepParams);

		final ImageView sep2 = new ImageView(getActivity());
		sep2.setImageDrawable(new ColorDrawable(Color.parseColor("#44000000")));
		view.addView(sep2, 2, sepParams);

		return button;
	}
}
