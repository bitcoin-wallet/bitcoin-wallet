/*
 * Copyright 2011-2012 the original author or authors.
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

package de.schildbach.wallet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.schildbach.wallet.util.IOUtils;
import de.schildbach.wallet.util.Iso8601Format;

/**
 * @author Andreas Schildbach
 */
public class DetermineFirstSeenThread extends Thread
{
	private static final Pattern P_FIRST_SEEN = Pattern.compile("<li>First seen.*\\((\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\)</li>");

	private final String address;

	public DetermineFirstSeenThread(final String address)
	{
		this.address = address;
		start();
	}

	@Override
	public void run()
	{
		try
		{
			final URLConnection connection = new URL(Constants.BLOCKEXPLORER_BASE_URL + "address/" + address).openConnection();
			connection.connect();
			final Reader is = new InputStreamReader(new BufferedInputStream(connection.getInputStream()));
			final StringBuilder content = new StringBuilder();
			IOUtils.copy(is, content);
			is.close();

			final Matcher m = P_FIRST_SEEN.matcher(content);
			if (m.find())
			{
				succeed(Iso8601Format.parseDateTime(m.group(1)));
			}
			else
			{
				succeed(null);
			}
		}
		catch (final IOException x)
		{
			failed(x);
		}
		catch (final ParseException x)
		{
			failed(x);
		}
	}

	protected void succeed(final Date creationTime)
	{
	}

	protected void failed(final Exception x)
	{
	}
}
