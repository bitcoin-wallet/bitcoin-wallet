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

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public class ActionBar extends LinearLayout
{
	private final ImageView iconView;
	private final TextView primaryTitleView;
	private final TextView secondaryTitleView;
	private final ImageView progressView;

	private int progressCount = 0;
	private Animation progressAnimation;

	public ActionBar(final Context context, final AttributeSet attributes)
	{
		super(context, attributes);

		final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.action_bar, this, true);

		iconView = (ImageView) findViewById(R.id.action_bar_icon);
		primaryTitleView = (TextView) findViewById(R.id.action_bar_primary_title);
		secondaryTitleView = (TextView) findViewById(R.id.action_bar_secondary_title);
		progressView = (ImageView) findViewById(R.id.action_bar_progress_image);

		progressAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate);
	}

	public void setIcon(final int iconRes)
	{
		iconView.setImageResource(iconRes);
	}

	public void setPrimaryTitle(final int titleRes)
	{
		primaryTitleView.setText(titleRes);
	}

	public void setSecondaryTitle(final CharSequence title)
	{
		secondaryTitleView.setText(title);
		secondaryTitleView.setVisibility(View.VISIBLE);
	}

	public ImageButton getButton()
	{
		return (ImageButton) findViewById(R.id.action_bar_button);
	}

	public View getProgressButton()
	{
		return (ImageButton) findViewById(R.id.action_bar_progress_button);
	}

	public void startProgress()
	{
		if (progressCount == 0)
			progressView.startAnimation(progressAnimation);

		progressCount++;
	}

	public void stopProgress()
	{
		progressCount--;

		if (progressCount <= 0)
			progressView.clearAnimation();
	}

	public int getProgressCount()
	{
		return progressCount;
	}
}
