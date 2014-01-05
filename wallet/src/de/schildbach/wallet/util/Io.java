/*
 * Copyright 2011-2014 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public class Io
{
	private static final Logger log = LoggerFactory.getLogger(Io.class);

	public static final long copy(@Nonnull final Reader reader, @Nonnull final StringBuilder builder) throws IOException
	{
		final char[] buffer = new char[256];
		long count = 0;
		int n = 0;
		while (-1 != (n = reader.read(buffer)))
		{
			builder.append(buffer, 0, n);
			count += n;
		}
		return count;
	}

	public static final long copy(@Nonnull final InputStream is, @Nonnull final OutputStream os) throws IOException
	{
		final byte[] buffer = new byte[1024];
		long count = 0;
		int n = 0;
		while (-1 != (n = is.read(buffer)))
		{
			os.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void chmod(@Nonnull final File path, final int mode)
	{
		try
		{
			final Class fileUtils = Class.forName("android.os.FileUtils");
			final Method setPermissions = fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
			setPermissions.invoke(null, path.getAbsolutePath(), mode, -1, -1);
		}
		catch (final Exception x)
		{
			log.info("problem using undocumented chmod api", x);
		}
	}
}
