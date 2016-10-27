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

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

/**
 * @author Andreas Schildbach
 */
public final class FileAttachmentProvider extends ContentProvider {
    public static Uri contentUri(final String packageName, final File file) {
        return Uri.parse("content://" + packageName + ".file_attachment" + file.getAbsolutePath());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        final File file = new File(uri.getPath());

        if (!file.getAbsolutePath().startsWith(getContext().getCacheDir().getAbsolutePath()))
            return null;

        final String[] split = file.getName().split("\\.");
        if (split.length >= 2) {
            final String suffix = split[split.length - 1];
            if ("txt".equalsIgnoreCase(suffix) || "log".equalsIgnoreCase(suffix))
                return "text/plain";
            else if ("gz".equalsIgnoreCase(suffix))
                return "application/x-gzip";
        }

        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(final Uri uri, final String mode) throws FileNotFoundException {
        return openFileHelper(uri, mode);
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        final File file = new File(uri.getPath());

        if (!file.getAbsolutePath().startsWith(getContext().getCacheDir().getAbsolutePath()))
            throw new IllegalArgumentException("not in cache dir: " + uri);

        final MatrixCursor cursor = new MatrixCursor(projection);
        final RowBuilder row = cursor.newRow();
        for (int i = 0; i < projection.length; i++) {
            final String columnName = projection[i];
            if (columnName.equals(MediaStore.MediaColumns.DATA))
                row.add(file.getAbsolutePath());
            else if (columnName.equals(MediaStore.MediaColumns.SIZE))
                row.add(file.length());
            else if (columnName.equals(MediaStore.MediaColumns.DISPLAY_NAME))
                row.add(uri.getLastPathSegment());
            else
                throw new IllegalArgumentException("cannot handle: " + columnName);
        }

        return cursor;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
