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

import android.content.pm.PackageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author Andreas Schildbach
 */
public class CrashReporter {
    private static final String BACKGROUND_TRACES_FILENAME = "background.trace";
    private static final String CRASH_TRACE_FILENAME = "crash.trace";

    private static File backgroundTracesFile;
    private static File crashTraceFile;

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final Logger log = LoggerFactory.getLogger(CrashReporter.class);

    public static void init(final File cacheDir) {
        backgroundTracesFile = new File(cacheDir, BACKGROUND_TRACES_FILENAME);
        crashTraceFile = new File(cacheDir, CRASH_TRACE_FILENAME);

        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()));
    }

    public static boolean collectSavedBackgroundTraces(final File file) {
        return backgroundTracesFile.renameTo(file);
    }

    public static boolean hasSavedCrashTrace() {
        return crashTraceFile.exists();
    }

    public static void appendSavedCrashTrace(final Appendable report) throws IOException {
        if (crashTraceFile.exists()) {
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(crashTraceFile), StandardCharsets.UTF_8))) {
                copy(reader, report);
            } finally {
                deleteSaveCrashTrace();
            }
        }
    }

    public static boolean deleteSaveCrashTrace() {
        return crashTraceFile.delete();
    }

    private static void copy(final BufferedReader in, final Appendable out) throws IOException {
        while (true) {
            final String line = in.readLine();
            if (line == null)
                break;

            out.append(line).append('\n');
        }
    }

    public static void saveBackgroundTrace(final Throwable throwable, final PackageInfo packageInfo) {
        synchronized (backgroundTracesFile) {
            try (final PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(backgroundTracesFile, true), StandardCharsets.UTF_8))) {
                final Calendar now = new GregorianCalendar(UTC);
                writer.println(String.format(Locale.US, "\n--- collected at %tF %tT %tZ on version %s (%d) ---\n", now,
                        now, now, packageInfo.versionName, packageInfo.versionCode));
                appendTrace(writer, throwable);
            } catch (final IOException x) {
                log.error("problem writing background trace", x);
            }
        }
    }

    private static void appendTrace(final PrintWriter writer, final Throwable throwable) {
        throwable.printStackTrace(writer);
        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        Throwable cause = throwable.getCause();
        while (cause != null) {
            writer.println("\nCause:\n");
            cause.printStackTrace(writer);
            cause = cause.getCause();
        }
    }

    private static class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler previousHandler;

        public ExceptionHandler(final Thread.UncaughtExceptionHandler previousHandler) {
            this.previousHandler = previousHandler;
        }

        @Override
        public synchronized void uncaughtException(final Thread t, final Throwable exception) {
            log.warn("crashing because of uncaught exception", exception);
            saveCrashTrace(exception);
            previousHandler.uncaughtException(t, exception);
        }

        private void saveCrashTrace(final Throwable throwable) {
            // Don't bother the user and us with these Android system bugs; we cannot do anything about it.
            if (/* throwable instanceof android.app.RemoteServiceException && */
                    throwable.getMessage().contains("Context.startForegroundService() did not then call Service.startForeground()"))
                return;

            try {
                final PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(new FileOutputStream(crashTraceFile), StandardCharsets.UTF_8));
                appendTrace(writer, throwable);
                writer.close();
                log.info("saved crash trace to {}", crashTraceFile);
            } catch (final IOException x) {
                log.warn("problem saving crash trace", x);
            }
        }
    }
}
