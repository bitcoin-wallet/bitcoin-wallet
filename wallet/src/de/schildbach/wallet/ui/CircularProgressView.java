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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * @author Andreas Schildbach
 */
public class CircularProgressView extends View {
    private int width;
    private int height;
    private int progress = 1;
    private int maxProgress = 1;
    private int size = 1;
    private int maxSize = 1;
    private final Path path = new Path();
    private final Paint fillPaint = new Paint();
    private final Paint strokePaint = new Paint();

    public CircularProgressView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final float density = getResources().getDisplayMetrics().density;

        fillPaint.setStyle(Style.FILL);
        fillPaint.setColor(Color.parseColor("#44ff44"));
        fillPaint.setAntiAlias(true);

        strokePaint.setStyle(Style.STROKE);
        strokePaint.setColor(Color.DKGRAY);
        strokePaint.setStrokeWidth(1 * density);
        strokePaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawPath(path, fillPaint);
        canvas.drawPath(path, strokePaint);
    }

    public void setColors(final int fillColor, final int strokeColor) {
        fillPaint.setColor(fillColor);
        strokePaint.setColor(strokeColor);
        invalidate();
    }

    public void setProgress(final int progress) {
        this.progress = progress;

        updatePath(getWidth(), getHeight());
        invalidate();
    }

    public void setMaxProgress(final int maxProgress) {
        this.maxProgress = maxProgress;

        updatePath(getWidth(), getHeight());
        invalidate();
    }

    public void setSize(final int size) {
        this.size = size;

        updatePath(getWidth(), getHeight());
        invalidate();
    }

    public void setMaxSize(final int maxSize) {
        this.maxSize = maxSize;

        updatePath(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        updatePath(w, h);

        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void updatePath(final int w, final int h) {
        final float maxAbsSize = Math.min(w, h) / 2f;
        final float absSize = size < maxSize ? maxAbsSize * size / maxSize : maxAbsSize - 1;

        path.reset();

        if (progress == 0) {
            path.close();
        } else if (progress < maxProgress) {
            final float angle = (float) (progress * 360) / maxProgress;
            final float x = w / 2f;
            final float y = h / 2f;

            path.moveTo(x, y);
            path.arcTo(new RectF(x - absSize, y - absSize, x + absSize, y + absSize), 270, angle);
            path.close();
        } else {
            path.addCircle(w / 2f, h / 2f, absSize, Direction.CW);
        }
    }

    @Override
    protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec) {
        final int wMode = MeasureSpec.getMode(wMeasureSpec);
        final int wSize = MeasureSpec.getSize(wMeasureSpec);

        if (wMode == MeasureSpec.EXACTLY)
            width = wSize;
        else if (wMode == MeasureSpec.AT_MOST)
            width = Math.min(width, wSize);

        final int hMode = MeasureSpec.getMode(hMeasureSpec);
        final int hSize = MeasureSpec.getSize(hMeasureSpec);

        if (hMode == MeasureSpec.EXACTLY)
            height = hSize;
        else if (hMode == MeasureSpec.AT_MOST)
            height = Math.min(height, hSize);

        setMeasuredDimension(this.width, this.height);
    }

    @Override
    public int getBaseline() {
        return getMeasuredHeight() - 1;
    }
}
