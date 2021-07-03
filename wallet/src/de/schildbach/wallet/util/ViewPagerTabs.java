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

package de.schildbach.wallet.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.viewpager2.widget.ViewPager2;
import de.schildbach.wallet.R;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Andreas Schildbach
 */
public class ViewPagerTabs extends View {
    public enum Mode { DYNAMIC, STATIC }

    private Mode mode = Mode.DYNAMIC;
    private final List<String> labels = new ArrayList<>();
    private final Paint paint = new Paint();
    private int maxWidth = 0;
    @ColorInt
    private int textColor, selectedTextColor;
    @ColorInt
    private int indicatorColor;

    // instance state
    private int pagePosition = 0;
    private float pageOffset = 0;

    public ViewPagerTabs(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        setSaveEnabled(true);

        paint.setTextSize(getResources().getDimension(R.dimen.font_size_tiny));
        paint.setAntiAlias(true);

        textColor = context.getColor(R.color.fg_less_significant);
        selectedTextColor = context.getColor(R.color.fg_significant);
        indicatorColor = context.getColor(R.color.bg_level2);
    }

    public void setMode(final Mode mode) {
        this.mode = mode;
        invalidate();
    }

    public void addTabLabels(final int... labelResId) {
        final Context context = getContext();

        paint.setTypeface(Typeface.DEFAULT_BOLD);

        for (final int resId : labelResId) {
            final String label = context.getString(resId);

            final int width = (int) paint.measureText(label);

            if (width > maxWidth)
                maxWidth = width;

            labels.add(label);
        }
    }

    private final Path path = new Path();

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        if (mode == Mode.DYNAMIC)
            drawDynamic(canvas);
        else
            drawStatic(canvas);
    }

    private void drawDynamic(final Canvas canvas) {
        final int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        final int viewHalfWidth = getPaddingLeft() + viewWidth / 2;
        final int viewBottom = getHeight();

        final float density = getResources().getDisplayMetrics().density;
        final float spacing = 32 * density;

        path.reset();
        path.moveTo(viewHalfWidth, viewBottom - 5 * density);
        path.lineTo(viewHalfWidth + 5 * density, viewBottom);
        path.lineTo(viewHalfWidth - 5 * density, viewBottom);
        path.close();

        paint.setColor(indicatorColor);
        canvas.drawPath(path, paint);

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        final float y = getPaddingTop() + -paint.getFontMetrics().top;

        for (int i = 0; i < labels.size(); i++) {
            final String label = labels.get(i);

            paint.setTypeface(i == pagePosition ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            paint.setColor(i == pagePosition ? selectedTextColor : textColor);

            final float x = viewHalfWidth + (maxWidth + spacing) * (i - pageOffset);
            final float labelWidth = paint.measureText(label);
            final float labelHalfWidth = labelWidth / 2;

            final float labelLeft = x - labelHalfWidth;
            final float labelVisibleLeft = labelLeft >= 0 ? 1f : 1f - (-labelLeft / labelWidth);

            final float labelRight = x + labelHalfWidth;
            final float labelVisibleRight = labelRight < viewWidth ? 1f : 1f - ((labelRight - viewWidth) / labelWidth);

            final float labelVisible = Math.min(labelVisibleLeft, labelVisibleRight);

            paint.setAlpha((int) (labelVisible * 255));

            canvas.drawText(label, labelLeft, y, paint);
        }
    }

    private void drawStatic(final Canvas canvas) {
        final int numLabels = labels.size();
        final float labelWidth = (float) (getWidth() - getPaddingLeft() - getPaddingRight()) / numLabels;
        final float leftPadding = getResources().getDimension(R.dimen.list_entry_padding_horizontal);
        final float y = getPaddingTop() + -paint.getFontMetrics().top;
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(textColor);
        for (int i = 0; i < numLabels; i++)
            canvas.drawText(labels.get(i), getPaddingLeft() + labelWidth * i + leftPadding, y, paint);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
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
    protected int getSuggestedMinimumHeight() {
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        return (int) (-paint.getFontMetrics().top + paint.getFontMetrics().bottom) + getPaddingTop()
                + getPaddingBottom();
    }

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
            pageOffset = position + positionOffset;
            invalidate();
        }

        @Override
        public void onPageSelected(final int position) {
            pagePosition = position;
            invalidate();
        }
    };

    public ViewPager2.OnPageChangeCallback getPageChangeCallback() {
        return pageChangeCallback;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable("super_state", super.onSaveInstanceState());
        state.putInt("page_position", pagePosition);
        state.putFloat("page_offset", pageOffset);
        return state;
    }

    @Override
    public void onRestoreInstanceState(final Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            pagePosition = bundle.getInt("page_position");
            pageOffset = bundle.getFloat("page_offset");
            super.onRestoreInstanceState(bundle.getParcelable("super_state"));
            return;
        }

        super.onRestoreInstanceState(state);
    }
}
