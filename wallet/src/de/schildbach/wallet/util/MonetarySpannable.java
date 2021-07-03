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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import androidx.annotation.Nullable;
import de.schildbach.wallet.Constants;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.MonetaryFormat;

import java.util.regex.Matcher;

import static androidx.core.util.Preconditions.checkArgument;

/**
 * @author Andreas Schildbach
 */
public final class MonetarySpannable extends SpannableString {
    public MonetarySpannable(@Nullable final MonetaryFormat format, final boolean signed,
            @Nullable final Monetary monetary) {
        super(format(format, signed, monetary));
    }

    public MonetarySpannable(@Nullable final MonetaryFormat format, @Nullable final Monetary monetary) {
        super(format(format, false, monetary));
    }

    private static CharSequence format(@Nullable final MonetaryFormat format, final boolean signed,
            final Monetary monetary) {
        if (monetary == null)
            return "";
        if (format == null)
            return monetary.toString();

        checkArgument(monetary.signum() >= 0 || signed);

        if (signed)
            return format.negativeSign(Constants.CURRENCY_MINUS_SIGN).positiveSign(Constants.CURRENCY_PLUS_SIGN)
                    .format(monetary);
        else
            return format.format(monetary);
    }

    public MonetarySpannable applyMarkup(@Nullable final Object[] prefixSpans,
            @Nullable final Object[] insignificantSpans) {
        applyMarkup(this, prefixSpans, STANDARD_SIGNIFICANT_SPANS, insignificantSpans);
        return this;
    }

    public static final Object BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    public static final RelativeSizeSpan SMALLER_SPAN = new RelativeSizeSpan(0.85f);

    public static final Object[] STANDARD_SIGNIFICANT_SPANS = new Object[] { BOLD_SPAN };
    public static final Object[] STANDARD_INSIGNIFICANT_SPANS = new Object[] { MonetarySpannable.SMALLER_SPAN };

    public static void applyMarkup(final Spannable spannable, @Nullable final Object[] prefixSpans,
            @Nullable final Object[] significantSpans, @Nullable final Object[] insignificantSpans) {
        if (prefixSpans != null)
            for (final Object span : prefixSpans)
                spannable.removeSpan(span);
        if (significantSpans != null)
            for (final Object span : significantSpans)
                spannable.removeSpan(span);
        if (insignificantSpans != null)
            for (final Object span : insignificantSpans)
                spannable.removeSpan(span);

        final Matcher m = Formats.PATTERN_MONETARY_SPANNABLE.matcher(spannable);
        if (m.find()) {
            int i = 0;

            if (m.group(Formats.PATTERN_GROUP_PREFIX) != null) {
                final int end = m.end(Formats.PATTERN_GROUP_PREFIX);
                if (prefixSpans != null)
                    for (final Object span : prefixSpans)
                        spannable.setSpan(span, i, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end;
            }

            if (m.group(Formats.PATTERN_GROUP_SIGNIFICANT) != null) {
                final int end = m.end(Formats.PATTERN_GROUP_SIGNIFICANT);
                if (significantSpans != null)
                    for (final Object span : significantSpans)
                        spannable.setSpan(span, i, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end;
            }

            if (m.group(Formats.PATTERN_GROUP_INSIGNIFICANT) != null) {
                final int end = m.end(Formats.PATTERN_GROUP_INSIGNIFICANT);
                if (insignificantSpans != null)
                    for (final Object span : insignificantSpans)
                        spannable.setSpan(span, i, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end;
            }
        }
    }
}
