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

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ActionMenuView;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public final class Toolbars {
    public static void colorize(final android.widget.Toolbar toolbarView, final int toolbarIconsColor) {
        final PorterDuffColorFilter colorFilter
                = new PorterDuffColorFilter(toolbarIconsColor, PorterDuff.Mode.MULTIPLY);
        final int toolbarChildCount = toolbarView.getChildCount();
        for (int iToolbarChild = 0; iToolbarChild < toolbarChildCount; iToolbarChild++) {
            final View toolbarChild = toolbarView.getChildAt(iToolbarChild);
            if (toolbarChild instanceof ActionMenuView) {
                final ActionMenuView actionMenuView = (ActionMenuView) toolbarChild;
                final int menuChildCount = actionMenuView.getChildCount();
                for (int iMenuChild = 0; iMenuChild < menuChildCount; iMenuChild++) {
                    final View menuChild = actionMenuView.getChildAt(iMenuChild);
                    if (menuChild instanceof TextView) {
                        final TextView actionButton = (TextView) menuChild;
                        final int drawableCount = actionButton.getCompoundDrawables().length;
                        for (int iDrawable = 0; iDrawable < drawableCount; iDrawable++) {
                            final Drawable drawable = actionButton.getCompoundDrawables()[iDrawable];
                            if (drawable != null)
                                drawable.setColorFilter(colorFilter);
                        }
                    }
                }
            }
        }
    }
}
