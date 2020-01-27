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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public final class CurrencySymbolDrawable extends Drawable {
    private final Paint paint = new Paint();
    private final String symbol;
    private final float y;

    public CurrencySymbolDrawable(final String symbol, final float textSize, final int color, final float y) {
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setTextSize(textSize);

        this.symbol = symbol + Constants.CHAR_HAIR_SPACE;
        this.y = y;
    }

    @Override
    public void draw(final Canvas canvas) {
        canvas.drawText(symbol, 0, y, paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) paint.measureText(symbol);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(final int alpha) {
    }

    @Override
    public void setColorFilter(final ColorFilter cf) {
    }
}
