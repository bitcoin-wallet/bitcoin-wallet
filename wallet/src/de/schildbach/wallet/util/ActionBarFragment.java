/*
 * Copyright 2011-2012 the original author or authors.
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

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
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
public final class ActionBarFragment extends Fragment
{
	private ViewGroup view;
	private View backButtonView;
	private View backView;
	private ImageView iconView;
	private TextView primaryTitleView;
	private TextView secondaryTitleView;

	private LinearLayout.LayoutParams separatorParams;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		view = (ViewGroup) inflater.inflate(R.layout.action_bar_fragment, container);
		backButtonView = view.findViewById(R.id.action_bar_back_button);
		backView = view.findViewById(R.id.action_bar_back);
		iconView = (ImageView) view.findViewById(R.id.action_bar_icon);
		primaryTitleView = (TextView) view.findViewById(R.id.action_bar_primary_title);
		secondaryTitleView = (TextView) view.findViewById(R.id.action_bar_secondary_title);

		return view;
	}

	@Override
	public void onAttach(final Activity activity)
	{
		final Resources res = getResources();
		final int separatorWidth = res.getDimensionPixelSize(R.dimen.action_bar_button_separator_width);
		final int separatorMargin = res.getDimensionPixelSize(R.dimen.action_bar_button_separator_margin);

		separatorParams = new LinearLayout.LayoutParams(separatorWidth, LayoutParams.FILL_PARENT, 0f);
		separatorParams.setMargins(0, separatorMargin, 0, separatorMargin);

		super.onAttach(activity);
	}

	public void setIcon(final int iconRes)
	{
		iconView.setImageResource(iconRes);
	}

	public void setBack(final OnClickListener onClickListener)
	{
		backButtonView.setOnClickListener(onClickListener);
		backView.setVisibility(View.VISIBLE);
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
		final LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(getResources().getDimensionPixelSize(
				R.dimen.action_bar_button_width), LayoutParams.FILL_PARENT, 0f);
		buttonParams.gravity = Gravity.CENTER_VERTICAL;

		final ImageButton button = new ImageButton(getActivity());
		button.setImageResource(drawableRes);
		button.setScaleType(ScaleType.CENTER);
		button.setBackgroundResource(R.drawable.action_bar_background);
		button.setPadding(0, 0, 0, 0);
		view.addView(button, 2, buttonParams);

		final ImageView separator1 = new ImageView(getActivity());
		separator1.setImageDrawable(new ColorDrawable(Color.parseColor("#44ffffff")));
		view.addView(separator1, 2, separatorParams);

		final ImageView separator2 = new ImageView(getActivity());
		separator2.setImageDrawable(new ColorDrawable(Color.parseColor("#44000000")));
		view.addView(separator2, 2, separatorParams);

		return button;
	}

	public boolean removeButton(final ImageButton button)
	{
		final int index = view.indexOfChild(button);
		if (index == -1)
			return false;

		view.removeViews(index - 2, 3);
		return true;
	}
}
