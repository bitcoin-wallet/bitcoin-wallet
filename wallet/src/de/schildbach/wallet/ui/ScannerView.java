/*
 * Copyright 2012-2015 the original author or authors.
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.zxing.ResultPoint;

import de.schildbach.wallet.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * @author Andreas Schildbach
 */
public class ScannerView extends View {
    private static final long LASER_ANIMATION_DELAY_MS = 100l;
    private static final int DOT_OPACITY = 0xa0;
    private static final int DOT_TTL_MS = 500;

    private final Paint maskPaint;
    private final Paint laserPaint;
    private final Paint dotPaint;
    private boolean isResult;
    private final int maskColor, maskResultColor;
    private final int laserColor;
    private final int dotColor, dotResultColor;
    private final Map<float[], Long> dots = new HashMap<float[], Long>(16);
    private Rect frame;
    private final Matrix matrix = new Matrix();

    public ScannerView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final Resources res = getResources();
        maskColor = res.getColor(R.color.scan_mask);
        maskResultColor = res.getColor(R.color.scan_result_view);
        laserColor = res.getColor(R.color.scan_laser);
        dotColor = res.getColor(R.color.scan_dot);
        dotResultColor = res.getColor(R.color.scan_result_dots);

        maskPaint = new Paint();
        maskPaint.setStyle(Style.FILL);

        laserPaint = new Paint();
        laserPaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.scan_laser_width));
        laserPaint.setStyle(Style.STROKE);

        dotPaint = new Paint();
        dotPaint.setAlpha(DOT_OPACITY);
        dotPaint.setStyle(Style.STROKE);
        dotPaint.setStrokeWidth(res.getDimension(R.dimen.scan_dot_size));
        dotPaint.setAntiAlias(true);
    }

    public void setFraming(final Rect frame, final RectF framePreview, final int displayRotation,
            final int cameraRotation, final boolean cameraFlip) {
        this.frame = frame;
        matrix.setRectToRect(framePreview, new RectF(frame), ScaleToFit.FILL);
        matrix.postRotate(-displayRotation, frame.exactCenterX(), frame.exactCenterY());
        matrix.postScale(cameraFlip ? -1 : 1, 1, frame.exactCenterX(), frame.exactCenterY());
        matrix.postRotate(cameraRotation, frame.exactCenterX(), frame.exactCenterY());

        invalidate();
    }

    public void setIsResult(final boolean isResult) {
        this.isResult = isResult;

        invalidate();
    }

    public void addDot(final ResultPoint dot) {
        dots.put(new float[] { dot.getX(), dot.getY() }, System.currentTimeMillis());

        invalidate();
    }

    @Override
    public void onDraw(final Canvas canvas) {
        if (frame == null)
            return;

        final long now = System.currentTimeMillis();

        final int width = canvas.getWidth();
        final int height = canvas.getHeight();

        final float[] point = new float[2];

        // draw mask darkened
        maskPaint.setColor(isResult ? maskResultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, maskPaint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, maskPaint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, maskPaint);
        canvas.drawRect(0, frame.bottom + 1, width, height, maskPaint);

        if (isResult) {
            laserPaint.setColor(dotResultColor);
            laserPaint.setAlpha(160);

            dotPaint.setColor(dotResultColor);
        } else {
            laserPaint.setColor(laserColor);
            final boolean laserPhase = (now / 600) % 2 == 0;
            laserPaint.setAlpha(laserPhase ? 160 : 255);

            dotPaint.setColor(dotColor);

            // schedule redraw
            postInvalidateDelayed(LASER_ANIMATION_DELAY_MS);
        }

        canvas.drawRect(frame, laserPaint);

        // draw points
        for (final Iterator<Map.Entry<float[], Long>> i = dots.entrySet().iterator(); i.hasNext();) {
            final Map.Entry<float[], Long> entry = i.next();
            final long age = now - entry.getValue();
            if (age < DOT_TTL_MS) {
                dotPaint.setAlpha((int) ((DOT_TTL_MS - age) * 256 / DOT_TTL_MS));

                matrix.mapPoints(point, entry.getKey());
                canvas.drawPoint(point[0], point[1], dotPaint);
            } else {
                i.remove();
            }
        }
    }
}
