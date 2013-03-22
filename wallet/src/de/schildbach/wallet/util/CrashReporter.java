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

package de.schildbach.wallet.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Formatter;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;

/**
 * @author Andreas Schildbach
 */
public class CrashReporter
{
	private static final String STACK_TRACE_FILENAME = "crash.trace";
	private static final String APPLICATION_LOG_FILENAME = "crash.log";

	private static File stackTraceFile;
	private static File applicationLogFile;

	public static void init(final File cacheDir)
	{
		stackTraceFile = new File(cacheDir, STACK_TRACE_FILENAME);
		applicationLogFile = new File(cacheDir, APPLICATION_LOG_FILENAME);

		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()));
	}

	public static boolean hasSavedReport()
	{
		return stackTraceFile.exists();
	}

	public static void appendSavedStackTrace(final Appendable report) throws IOException
	{
		BufferedReader stackTraceReader = null;

		try
		{
			stackTraceReader = new BufferedReader(new FileReader(stackTraceFile));
			copy(stackTraceReader, report);
		}
		finally
		{
			if (stackTraceReader != null)
				stackTraceReader.close();

			stackTraceFile.delete();
		}
	}

	public static void appendSavedApplicationLog(final Appendable report) throws IOException
	{
		BufferedReader applicationLogReader = null;

		try
		{
			applicationLogReader = new BufferedReader(new FileReader(applicationLogFile));
			copy(applicationLogReader, report);
		}
		finally
		{
			if (applicationLogReader != null)
				applicationLogReader.close();

			applicationLogFile.delete();
		}
	}

	private static void copy(final BufferedReader in, final Appendable out) throws IOException
	{
		while (true)
		{
			final String line = in.readLine();
			if (line == null)
				break;

			out.append(line).append('\n');
		}
	}

	public static void appendDeviceInfo(final Appendable report, final Context context) throws IOException
	{
		final Resources res = context.getResources();
		final Configuration config = res.getConfiguration();
		final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

		report.append("Date: " + new Date() + "\n");
		report.append("Phone Model: " + android.os.Build.MODEL + "\n");
		report.append("Android Version: " + android.os.Build.VERSION.RELEASE + "\n");
		report.append("Board: " + android.os.Build.BOARD + "\n");
		report.append("Brand: " + android.os.Build.BRAND + "\n");
		report.append("Device: " + android.os.Build.DEVICE + "\n");
		report.append("Display: " + android.os.Build.DISPLAY + "\n");
		report.append("Finger Print: " + android.os.Build.FINGERPRINT + "\n");
		report.append("Host: " + android.os.Build.HOST + "\n");
		report.append("ID: " + android.os.Build.ID + "\n");
		// report.append("Manufacturer: " + manufacturer + "\n");
		report.append("Model: " + android.os.Build.MODEL + "\n");
		report.append("Product: " + android.os.Build.PRODUCT + "\n");
		report.append("Tags: " + android.os.Build.TAGS + "\n");
		report.append("Time: " + android.os.Build.TIME + "\n");
		report.append("Type: " + android.os.Build.TYPE + "\n");
		report.append("User: " + android.os.Build.USER + "\n");
		report.append("Configuration: " + config + "\n");
		report.append("Screen Layout: size " + (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) + " long "
				+ (config.screenLayout & Configuration.SCREENLAYOUT_LONG_MASK) + "\n");
		report.append("Display Metrics: " + res.getDisplayMetrics() + "\n");
		report.append("Memory Class: " + activityManager.getMemoryClass() + "\n");
	}

	public static void appendApplicationInfo(final Appendable report, final Context context) throws IOException
	{
		try
		{
			final PackageManager pm = context.getPackageManager();
			final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
			report.append("Version: " + pi.versionName + " (" + pi.versionCode + ")\n");
			report.append("Package: " + pi.packageName + "\n");
			report.append("Databases:");
			for (final String db : context.databaseList())
				report.append(" " + db);
			report.append("\n");

			final File filesDir = context.getFilesDir();
			report.append("\nContents of FilesDir " + filesDir + ":\n");
			appendDir(report, filesDir, 0);
			final File cacheDir = context.getCacheDir();
			report.append("\nContents of CacheDir " + cacheDir + ":\n");
			appendDir(report, cacheDir, 0);
		}
		catch (final NameNotFoundException x)
		{
			throw new IOException(x.toString());
		}
	}

	public static void appendApplicationLog(final Appendable report) throws IOException
	{
		Process process = null;
		BufferedReader logReader = null;

		try
		{
			// likely to throw exception on older android devices
			process = Runtime.getRuntime().exec("logcat -d -v time");
			logReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;
			while ((line = logReader.readLine()) != null)
				report.append(line).append('\n');
		}
		finally
		{
			if (logReader != null)
				logReader.close();

			if (process != null)
				process.destroy();
		}
	}

	private static void appendDir(final Appendable report, final File file, final int indent) throws IOException
	{
		for (int i = 0; i < indent; i++)
			report.append("  - ");

		final Formatter formatter = new Formatter(report);
		formatter.format("%tF %tT %8d  %s\n", file.lastModified(), file.lastModified(), file.length(), file.getName());
		formatter.close();

		if (file.isDirectory())
			for (final File f : file.listFiles())
				appendDir(report, f, indent + 1);
	}

	private static class ExceptionHandler implements Thread.UncaughtExceptionHandler
	{
		private final Thread.UncaughtExceptionHandler previousHandler;

		public ExceptionHandler(final Thread.UncaughtExceptionHandler previousHandler)
		{
			this.previousHandler = previousHandler;
		}

		public synchronized void uncaughtException(final Thread t, final Throwable exception)
		{
			try
			{
				saveStacktrace(exception);
				saveApplicationLog();
			}
			catch (final IOException x)
			{
				x.printStackTrace();
			}

			previousHandler.uncaughtException(t, exception);
		}

		private void saveStacktrace(final Throwable exception) throws IOException
		{
			final PrintWriter writer = new PrintWriter(new FileWriter(stackTraceFile));
			exception.printStackTrace(writer);
			// If the exception was thrown in a background thread inside
			// AsyncTask, then the actual exception can be found with getCause
			Throwable cause = exception.getCause();
			while (cause != null)
			{
				writer.println("\nCause:\n");
				cause.printStackTrace(writer);
				cause = cause.getCause();
			}
			writer.close();
		}

		private void saveApplicationLog() throws IOException
		{
			final PrintWriter writer = new PrintWriter(new FileWriter(applicationLogFile));
			appendApplicationLog(writer);
			writer.close();
		}
	}
}
