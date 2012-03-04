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

package de.schildbach.wallet.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Formatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.schildbach.wallet.Constants;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Environment;
import android.os.StatFs;

/**
 * @author Andreas Schildbach
 */
public class ErrorReporter implements Thread.UncaughtExceptionHandler
{
	private static final String STACKTRACE_FILENAME = ".stacktrace";
	private static final String REPORT_SUBJECT = "Bitcoin Wallet Crash Report";
	private static final String REPORT_EMAIL = "wallet@schildbach.de";
	private static final String DIALOG_TITLE = "Previous crash detected";
	private static final String DIALOG_MESSAGE = "Would you like to send a crash report, helping to fix this issue in the future?";

	private Thread.UncaughtExceptionHandler previousHandler;
	private File stackTraceFile;
	private final StringBuilder report = new StringBuilder();
	private File filesDir, cacheDir;

	private static ErrorReporter instance;

	public static ErrorReporter getInstance()
	{
		if (instance == null)
			instance = new ErrorReporter();
		return instance;
	}

	public synchronized void init(final Context context)
	{
		previousHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(this);

		filesDir = context.getFilesDir();
		cacheDir = context.getCacheDir();

		stackTraceFile = new File(cacheDir, STACKTRACE_FILENAME);

		report.append("=== collected at launch time ===\n\n");
		report.append("Test: " + Constants.TEST + "\n\n");
		appendReport(report, context);
	}

	private static void appendReport(final StringBuilder report, final Context context)
	{
		try
		{
			final PackageManager pm = context.getPackageManager();
			final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
			final Resources res = context.getResources();
			final Configuration config = res.getConfiguration();

			report.append("Date: " + new Date() + "\n");
			report.append("Version: " + pi.versionName + " (" + pi.versionCode + ")\n");
			report.append("Package: " + pi.packageName + "\n");
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
			report.append("ScreenLayout: size " + (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) + " long "
					+ (config.screenLayout & Configuration.SCREENLAYOUT_LONG_MASK) + "\n");
			report.append("DisplayMetrics: " + res.getDisplayMetrics() + "\n");
			report.append("Databases:");
			for (final String db : context.databaseList())
				report.append(" " + db);
			report.append("\n\n\n");
		}
		catch (NameNotFoundException e)
		{
			e.printStackTrace();
		}
	}

	private long getAvailableInternalMemorySize()
	{
		final File path = Environment.getDataDirectory();
		final StatFs stat = new StatFs(path.getPath());
		final long blockSize = stat.getBlockSize();
		final long availableBlocks = stat.getAvailableBlocks();
		return availableBlocks * blockSize;
	}

	private long getTotalInternalMemorySize()
	{
		final File path = Environment.getDataDirectory();
		final StatFs stat = new StatFs(path.getPath());
		final long blockSize = stat.getBlockSize();
		final long totalBlocks = stat.getBlockCount();
		return totalBlocks * blockSize;
	}

	public synchronized void uncaughtException(final Thread t, final Throwable e)
	{
		report.append("=== collected at exception time ===\n\n");

		report.append("Total Internal memory: " + getTotalInternalMemorySize() + "\n");
		report.append("Available Internal memory: " + getAvailableInternalMemorySize() + "\n");
		report.append("\n");

		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		final String stacktrace = result.toString();
		report.append(stacktrace + "\n");

		// If the exception was thrown in a background thread inside
		// AsyncTask, then the actual exception can be found with getCause
		Throwable cause = e.getCause();
		while (cause != null)
		{
			cause.printStackTrace(printWriter);
			report.append("Cause:\n");
			report.append(result.toString() + "\n");
			cause = cause.getCause();
		}
		printWriter.close();

		// append contents of directories
		report.append("\nContents of FilesDir " + filesDir + ":\n");
		appendReport(report, filesDir, 0);
		report.append("\nContents of CacheDir " + cacheDir + ":\n");
		appendReport(report, cacheDir, 0);

		saveAsFile(report.toString());

		previousHandler.uncaughtException(t, e);
	}

	private static void sendErrorMail(final Context context, final String errorContent)
	{
		final Intent sendIntent = new Intent(Intent.ACTION_SEND);
		final Matcher m = Pattern.compile("Version: (.+?) ").matcher(errorContent);
		final String versionName = m.find() ? m.group(1) : "";
		final String subject = REPORT_SUBJECT + " " + versionName;
		sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { REPORT_EMAIL });
		sendIntent.putExtra(Intent.EXTRA_TEXT, errorContent);
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		sendIntent.setType("message/rfc822");
		context.startActivity(Intent.createChooser(sendIntent, "Send Crash Report using..."));
	}

	public static void sendBugMail(final Context context)
	{
		final StringBuilder report = new StringBuilder();
		report.append("   Welchen Programmteil betrifft Dein Problem? (siehe erste Zeile des Fenstertitels)\n");
		report.append("   Which application component does your issue apply to? (first line of window title)\n");
		report.append("\n\n");
		report.append("   Welches Verkehrsnetz hast Du eingestellt? (siehe zweite Zeile des Fenstertitels)\n");
		report.append("   Which transport network have you got selected? (second line of window title)\n");
		report.append("\n\n");
		report.append("   Hast Du schon auf aktuellere Öffi-Versionen geprüft?\n");
		report.append("   Did you already check for new versions of Öffi?\n");
		report.append("\n\n");
		report.append("\n=== collected at reporting time ===\n\n");
		appendReport(report, context);

		// append contents of directories
		report.append("\nContents of FilesDir " + context.getFilesDir() + ":\n");
		appendReport(report, context.getFilesDir(), 0);
		report.append("\nContents of CacheDir " + context.getCacheDir() + ":\n");
		appendReport(report, context.getCacheDir(), 0);

		final Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { REPORT_EMAIL });
		sendIntent.putExtra(Intent.EXTRA_TEXT, report.toString());
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, REPORT_SUBJECT);
		sendIntent.setType("message/rfc822");
		context.startActivity(Intent.createChooser(sendIntent, "Send Bug Report using..."));
	}

	private void saveAsFile(final String errorContent)
	{
		try
		{
			final FileOutputStream trace = new FileOutputStream(stackTraceFile);
			trace.write(errorContent.getBytes());
			trace.close();
		}
		catch (final IOException x)
		{
			// swallow
		}
	}

	private void sendError(final Context context)
	{
		try
		{
			final StringBuilder errorText = new StringBuilder();

			final BufferedReader input = new BufferedReader(new FileReader(stackTraceFile));
			String line;
			while ((line = input.readLine()) != null)
				errorText.append(line + "\n");
			input.close();

			sendErrorMail(context, errorText.toString());
		}
		catch (Exception x)
		{
			x.printStackTrace();
		}
	}

	private static void appendReport(final StringBuilder report, final File file, final int indent)
	{
		final Formatter formatter = new Formatter(report);

		for (int i = 0; i < indent; i++)
			report.append("  - ");
		formatter.format("%tF %tT  %s  [%d]\n", file.lastModified(), file.lastModified(), file.getName(), file.length());

		if (file.isDirectory())
			for (final File f : file.listFiles())
				appendReport(report, f, indent + 1);
	}

	public synchronized void check(final Context context)
	{
		if (!stackTraceFile.exists())
			return;

		final Builder builder = new AlertDialog.Builder(context).setIcon(android.R.drawable.ic_dialog_alert).setTitle(DIALOG_TITLE)
				.setMessage(DIALOG_MESSAGE);
		builder.setPositiveButton("Report", new OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				sendError(context);
				stackTraceFile.delete();
			}
		});
		builder.setNegativeButton("Dismiss", new OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				stackTraceFile.delete();
				dialog.dismiss();
			}
		});

		try
		{
			builder.show();
		}
		catch (final Exception x)
		{
			// swallow
		}
	}
}
