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

import android.annotation.SuppressLint;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author Andreas Schildbach
 */
@SuppressLint("SimpleDateFormat")
public class Iso8601Format extends SimpleDateFormat {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    public Iso8601Format(final String formatString) {
        super(formatString, Locale.US);
        setTimeZone(UTC);
    }

    public static DateFormat newTimeFormat() {
        return new Iso8601Format("HH:mm:ss");
    }

    public static DateFormat newDateFormat() {
        return new Iso8601Format("yyyy-MM-dd");
    }

    public static DateFormat newDateTimeFormat() {
        return new Iso8601Format("yyyy-MM-dd HH:mm:ss");
    }

    public static String formatDateTime(final Date date) {
        return newDateTimeFormat().format(date);
    }

    public static Date parseDateTime(final String source) throws ParseException {
        return newDateTimeFormat().parse(source);
    }

    public static DateFormat newDateTimeFormatT() {
        return new Iso8601Format("yyyy-MM-dd'T'HH:mm:ss'Z'");
    }

    public static String formatDateTimeT(final Date date) {
        return newDateTimeFormatT().format(date);
    }

    public static Date parseDateTimeT(final String source) throws ParseException {
        return newDateTimeFormatT().parse(source);
    }
}
