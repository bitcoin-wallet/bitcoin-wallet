/*
 * Copyright 2011-2013 the original author or authors.
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

import android.os.Handler;
import android.os.Looper;
import de.schildbach.wallet.util.IOUtils;
import de.schildbach.wallet.util.Iso8601Format;

/**
 * @author Andreas Schildbach
 */
public abstract class DetermineFirstSeenThread extends Thread
{
	private static final Pattern P_FIRST_SEEN = Pattern.compile(
			"<li>First seen.*(?:\\((\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\)|(Never used on the network)).*</li>", Pattern.CASE_INSENSITIVE);

	private final String address;
	private final Handler callbackHandler;

	public DetermineFirstSeenThread(final String address)
	{
		this.address = address;

		callbackHandler = new Handler(Looper.myLooper());

		start();
	}

	@Override
	public void run()
	{
		try
		{
			final URL url = new URL(Constants.BLOCKEXPLORER_BASE_URL + "address/" + address);
			final URLConnection connection = url.openConnection();
			connection.connect();
			final Reader is = new InputStreamReader(new BufferedInputStream(connection.getInputStream()));
			final StringBuilder content = new StringBuilder();
			IOUtils.copy(is, content);
			is.close();

			final Matcher m = P_FIRST_SEEN.matcher(content);
			if (m.find())
			{
				if (m.group(1) != null)
					onSucceed(Iso8601Format.parseDateTime(m.group(1)));
				else if (m.group(2) != null)
					onSucceed(null);
			}
			else
			{
				onFail(null);
			}
		}
		catch (final IOException x)
		{
			onFail(x);
		}
		catch (final ParseException x)
		{
			onFail(x);
		}
	}

	private void onSucceed(final Date creationTime)
	{
		callbackHandler.post(new Runnable()
		{
			public void run()
			{
				succeed(creationTime);
			}
		});
	}

	private void onFail(final Exception exception)
	{
		callbackHandler.post(new Runnable()
		{
			public void run()
			{
				fail(exception);
			}
		});
	}

	protected abstract void succeed(final Date creationTime);

	protected void fail(final Exception x)
	{
	}
}
