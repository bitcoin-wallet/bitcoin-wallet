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

package de.schildbach.wallet.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

/**
 * @author Andreas Schildbach
 */
public class ViewPager extends android.support.v4.view.ViewPager
{
	private OnPageChangeListener userPageChangeListener;
	private int currentItem;

	public ViewPager(final Context context)
	{
		super(context);
		init();
	}

	public ViewPager(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		init();
	}

	private void init()
	{
		super.setOnPageChangeListener(new OnPageChangeListener()
		{
			public void onPageSelected(final int position)
			{
				currentItem = position;

				if (userPageChangeListener != null)
					userPageChangeListener.onPageSelected(position);
			}

			public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels)
			{
				if (userPageChangeListener != null)
					userPageChangeListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
			}

			public void onPageScrollStateChanged(final int state)
			{
				if (userPageChangeListener != null)
					userPageChangeListener.onPageScrollStateChanged(state);
			}
		});
	}

	@Override
	public void setOnPageChangeListener(final OnPageChangeListener listener)
	{
		userPageChangeListener = listener;
	}

	@Override
	public boolean dispatchKeyEvent(final KeyEvent event)
	{
		return super.dispatchKeyEvent(event) || executeKeyEvent(event);
	}

	public boolean executeKeyEvent(final KeyEvent event)
	{
		if (event.getAction() == KeyEvent.ACTION_DOWN)
		{
			switch (event.getKeyCode())
			{
				case KeyEvent.KEYCODE_DPAD_LEFT:
					setCurrentItem(currentItem - 1);
					return false;

				case KeyEvent.KEYCODE_DPAD_RIGHT:
					setCurrentItem(currentItem + 1);
					return false;
			}
		}

		return false;
	}
}
