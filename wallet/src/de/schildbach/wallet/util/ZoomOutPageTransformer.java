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

import android.view.View;
import androidx.viewpager2.widget.ViewPager2;

/**
 * @author Andreas Schildbach
 */
public final class ZoomOutPageTransformer implements ViewPager2.PageTransformer {
    private static final float MIN_SCALE = 0.85f;
    private static final float MIN_ALPHA = 0.5f;

    public void transformPage(final View view, final float position) {
        if (position < -1 || position > 1) {
            view.setAlpha(0f);
        } else {
            final float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
            final float verticalMargin = view.getHeight() * (1 - scaleFactor) / 2;
            final float horizontalMargin = view.getWidth() * (1 - scaleFactor) / 2;
            if (position < 0)
                view.setTranslationX(horizontalMargin - verticalMargin / 2);
            else
                view.setTranslationX(-horizontalMargin + verticalMargin / 2);
            view.setScaleX(scaleFactor);
            view.setScaleY(scaleFactor);
            view.setAlpha(MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA));
        }
    }
}
