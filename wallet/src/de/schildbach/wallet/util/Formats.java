/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.schildbach.wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public final class Formats
{
	public static final Pattern PATTERN_MONETARY_SPANNABLE = Pattern.compile("(?:([\\p{Alpha}\\p{Sc}]++)\\s?+)?" // prefix
			+ "([\\+\\-" + Constants.CURRENCY_PLUS_SIGN + Constants.CURRENCY_MINUS_SIGN + "]?+(?:\\d*+\\.\\d{0,2}+|\\d++))" // significant
			+ "(\\d++)?"); // insignificant

	public static int PATTERN_GROUP_PREFIX = 1; // optional
	public static int PATTERN_GROUP_SIGNIFICANT = 2; // mandatory
	public static int PATTERN_GROUP_INSIGNIFICANT = 3; // optional

	private static final Pattern PATTERN_OUTER_HTML_PARAGRAPH = Pattern.compile("<p[^>]*>(.*)</p>\n?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	public static String maybeRemoveOuterHtmlParagraph(final CharSequence html)
	{
		final Matcher m = PATTERN_OUTER_HTML_PARAGRAPH.matcher(html);
		if (m.matches())
			return m.group(1);
		else
			return html.toString();
	}
}
