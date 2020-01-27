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

import androidx.annotation.Nullable;
import de.schildbach.wallet.Constants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Andreas Schildbach
 */
public final class Formats {
    public static final Pattern PATTERN_WHITESPACE = Pattern.compile("\\s+");
    public static final Pattern PATTERN_MONETARY_SPANNABLE = Pattern.compile("(?:([\\p{Alpha}\\p{Sc}]++)\\s?+)?" // prefix
            + "([\\+\\-" + Constants.CURRENCY_PLUS_SIGN + Constants.CURRENCY_MINUS_SIGN
            + "]?+(?:\\d*+\\.\\d{0,2}+|\\d++))" // significant
            + "(\\d++)?"); // insignificant
    public static int PATTERN_GROUP_PREFIX = 1; // optional
    public static int PATTERN_GROUP_SIGNIFICANT = 2; // mandatory
    public static int PATTERN_GROUP_INSIGNIFICANT = 3; // optional

    private static final Pattern PATTERN_MEMO = Pattern.compile(
            "(?:Payment request for Coinbase order code: (.+)|Payment request for BitPay invoice (.+) for merchant (.+))",
            Pattern.CASE_INSENSITIVE);

    @Nullable
    public static String[] sanitizeMemo(final @Nullable String memo) {
        if (memo == null)
            return null;

        final Matcher m = PATTERN_MEMO.matcher(memo);
        if (m.matches() && m.group(1) != null)
            return new String[] { m.group(1) + " (via Coinbase)" };
        else if (m.matches() && m.group(2) != null)
            return new String[] { m.group(2) + " (via BitPay)", m.group(3) };
        else
            return new String[] { memo };
    }
}
