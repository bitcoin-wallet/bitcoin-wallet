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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * @author Andreas Schildbach
 */
public class ViewPagerTabs extends View implements OnPageChangeListener
{
	private final List<String> labels = new ArrayList<String>();
	private final Paint paint = new Paint();
	private int maxWidth = 0;
	private int pagePosition = 0;
	private float pageOffset = 0;

	public ViewPagerTabs(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);

		final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
		final float density = displayMetrics.scaledDensity;

		paint.setTextSize(12f * density);
		paint.setColor(Color.BLACK);
		paint.setAntiAlias(true);
	}

	public void addTabLabels(final int... labelResId)
	{
		final Context context = getContext();

		paint.setTypeface(Typeface.DEFAULT_BOLD);

		for (final int resId : labelResId)
		{
			final String label = context.getString(resId);

			final int width = (int) paint.measureText(label);

			if (width > maxWidth)
				maxWidth = width;

			labels.add(label);
		}
	}

	@Override
	protected void onDraw(final Canvas canvas)
	{
		super.onDraw(canvas);

		final int halfWidth = getWidth() / 2;
		final int bottom = getHeight();

		final float density = getResources().getDisplayMetrics().density;
		final float spacing = 8 * density;

		final Path path = new Path();
		path.moveTo(halfWidth, bottom - 5 * density);
		path.lineTo(halfWidth + 5 * density, bottom);
		path.lineTo(halfWidth - 5 * density, bottom);
		path.close();

		paint.setColor(Color.WHITE);
		canvas.drawPath(path, paint);

		final float y = getPaddingTop() + -paint.getFontMetrics().top;

		for (int i = 0; i < labels.size(); i++)
		{
			final String label = labels.get(i);

			paint.setTypeface(i == pagePosition ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
			paint.setColor(i == pagePosition ? Color.BLACK : Color.DKGRAY);

			final float x = halfWidth + (maxWidth + spacing) * (i - pageOffset);
			canvas.drawText(label, x - paint.measureText(label) / 2, y, paint);
		}
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec)
	{
		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		final int widthSize = MeasureSpec.getSize(widthMeasureSpec);

		final int width;
		if (widthMode == MeasureSpec.EXACTLY)
			width = widthSize;
		else if (widthMode == MeasureSpec.AT_MOST)
			width = Math.min(getMeasuredWidth(), widthSize);
		else
			width = 0;

		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		final int height;
		if (heightMode == MeasureSpec.EXACTLY)
			height = heightSize;
		else if (heightMode == MeasureSpec.AT_MOST)
			height = Math.min(getSuggestedMinimumHeight(), heightSize);
		else
			height = getSuggestedMinimumHeight();

		setMeasuredDimension(width, height);
	}

	@Override
	protected int getSuggestedMinimumHeight()
	{
		return (int) (-paint.getFontMetrics().top + paint.getFontMetrics().bottom) + getPaddingTop() + getPaddingBottom();
	}

	public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels)
	{
		pageOffset = position + positionOffset;
		invalidate();
	}

	public void onPageSelected(final int position)
	{
		pagePosition = position;
		invalidate();
	}

	public void onPageScrollStateChanged(final int state)
	{
	}
}
