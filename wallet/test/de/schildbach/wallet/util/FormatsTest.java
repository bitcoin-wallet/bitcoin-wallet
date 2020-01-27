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

import org.junit.Test;

import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Andreas Schildbach
 */
public class FormatsTest {
    @Test
    public void monetarySpannable() throws Exception {
        final Matcher single = Formats.PATTERN_MONETARY_SPANNABLE.matcher("0");
        assertTrue(single.find());
        assertNull(single.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(single.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("0", single.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNull(single.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher many = Formats.PATTERN_MONETARY_SPANNABLE.matcher("00000000");
        assertTrue(many.find());
        assertNull(many.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(many.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("00000000", many.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNull(many.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher standard = Formats.PATTERN_MONETARY_SPANNABLE.matcher("0.0000");
        assertTrue(standard.find());
        assertNull(standard.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(standard.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("0.00", standard.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNotNull(standard.group(Formats.PATTERN_GROUP_INSIGNIFICANT));
        assertEquals("00", standard.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher startWithDot = Formats.PATTERN_MONETARY_SPANNABLE.matcher(".0000");
        assertTrue(startWithDot.find());
        assertNull(startWithDot.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(startWithDot.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals(".00", startWithDot.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNotNull(startWithDot.group(Formats.PATTERN_GROUP_INSIGNIFICANT));
        assertEquals("00", startWithDot.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher endWithDot = Formats.PATTERN_MONETARY_SPANNABLE.matcher("00.");
        assertTrue(endWithDot.find());
        assertNull(endWithDot.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(endWithDot.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("00.", endWithDot.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNull(endWithDot.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher signed = Formats.PATTERN_MONETARY_SPANNABLE.matcher("-0.00");
        assertTrue(signed.find());
        assertNull(signed.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(signed.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("-0.00", signed.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNull(signed.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher symbol = Formats.PATTERN_MONETARY_SPANNABLE.matcher("€0.00");
        assertTrue(symbol.find());
        assertNotNull(symbol.group(Formats.PATTERN_GROUP_PREFIX));
        assertEquals("€", symbol.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(symbol.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("0.00", symbol.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNull(symbol.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher code = Formats.PATTERN_MONETARY_SPANNABLE.matcher("BTC 0.00");
        assertTrue(code.find());
        assertNotNull(code.group(Formats.PATTERN_GROUP_PREFIX));
        assertEquals("BTC", code.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(code.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("0.00", code.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNull(code.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher subString = Formats.PATTERN_MONETARY_SPANNABLE.matcher("###$0###");
        assertTrue(subString.find());
        assertNotNull(subString.group(Formats.PATTERN_GROUP_PREFIX));
        assertEquals("$", subString.group(Formats.PATTERN_GROUP_PREFIX));
        assertNotNull(subString.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertEquals("0", subString.group(Formats.PATTERN_GROUP_SIGNIFICANT));
        assertNull(subString.group(Formats.PATTERN_GROUP_INSIGNIFICANT));

        final Matcher empty = Formats.PATTERN_MONETARY_SPANNABLE.matcher("");
        assertFalse(empty.find());

        final Matcher signOnly = Formats.PATTERN_MONETARY_SPANNABLE.matcher("+");
        assertFalse(signOnly.find());
    }
}
