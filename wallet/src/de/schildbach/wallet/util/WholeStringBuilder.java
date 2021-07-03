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

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;

/**
 * @author Andreas Schildbach
 */
public class WholeStringBuilder extends SpannableStringBuilder {
    public static CharSequence bold(final CharSequence text) {
        return new WholeStringBuilder(text, new StyleSpan(Typeface.BOLD));
    }

    public WholeStringBuilder(final CharSequence text, final Object span) {
        super(text);

        setSpan(span, 0, text.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
