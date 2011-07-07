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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/**
 * @author Andreas Schildbach
 */
public class BtcDrawable extends Drawable
{
	private static final String TEXT = "BTC";

	private final Paint paint;
	private final float y;

	public BtcDrawable(final float textSize, final float y)
	{
		paint = new Paint();
		paint.setColor(Color.parseColor("#666666"));
		paint.setAntiAlias(true);
		paint.setTextSize(textSize);

		this.y = y;
	}

	@Override
	public void draw(final Canvas canvas)
	{
		canvas.drawText(TEXT, 0, y, paint);
	}

	@Override
	public int getIntrinsicWidth()
	{
		return (int) paint.measureText(TEXT);
	}

	@Override
	public int getOpacity()
	{
		return 0;
	}

	@Override
	public void setAlpha(int alpha)
	{
	}

	@Override
	public void setColorFilter(ColorFilter cf)
	{
	}
}
