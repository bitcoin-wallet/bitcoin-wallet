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
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;

/**
 * This LinearLayout will avoid gaps by sizing its child's to the height (for horizontal orientation) or width (for
 * vertical orientation) of the largest child. This will never increase the size of the LinearLayout itself, only
 * fill the available gaps.
 *
 * @author Andreas Schildbach
 */
public class FillGapsLinearLayout extends LinearLayout {
    public FillGapsLinearLayout(final Context context) {
        super(context);
    }

    public FillGapsLinearLayout(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public FillGapsLinearLayout(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int count = getChildCount();
        final int orientation = getOrientation();
        if (orientation == HORIZONTAL) {
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE)
                    continue;
                child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(this.getMeasuredHeight(), MeasureSpec.EXACTLY));
            }
        } else if (orientation == VERTICAL) {
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE)
                    continue;
                child.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY));
            }
        }
    }
}
