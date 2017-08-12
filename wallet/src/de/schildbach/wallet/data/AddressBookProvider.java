/*
 * Copyright 2011-2015 the original author or authors.
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

package de.schildbach.wallet.data;

import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

/**
 * @author Andreas Schildbach
 */
public class AddressBookProvider extends ContentProvider {
    private static final String DATABASE_TABLE = "address_book";

    public static final String KEY_ROWID = "_id";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_LABEL = "label";

    public static final String SELECTION_QUERY = "q";
    public static final String SELECTION_IN = "in";
    public static final String SELECTION_NOTIN = "notin";

    public static Uri contentUri(final String packageName) {
        return Uri.parse("content://" + packageName + '.' + DATABASE_TABLE);
    }

    public static String resolveLabel(final Context context, final String address) {
        String label = null;

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(address).build();
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst())
                label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));

            cursor.close();
        }

        return label;
    }

    private Helper helper;

    @Override
    public boolean onCreate() {
        helper = new Helper(getContext());
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        if (uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String address = uri.getLastPathSegment();
        values.put(KEY_ADDRESS, address);

        long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

        final Uri rowUri = contentUri(getContext().getPackageName()).buildUpon().appendPath(address)
                .appendPath(Long.toString(rowId)).build();

        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        if (uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String address = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values, KEY_ADDRESS + "=?",
                new String[] { address });

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String address = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, KEY_ADDRESS + "=?",
                new String[] { address });

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String originalSelection,
            final String[] originalSelectionArgs, final String sortOrder) {
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DATABASE_TABLE);

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() > 1)
            throw new IllegalArgumentException(uri.toString());

        String selection = null;
        String[] selectionArgs = null;

        if (pathSegments.size() == 1) {
            final String address = uri.getLastPathSegment();

            qb.appendWhere(KEY_ADDRESS + "=");
            qb.appendWhereEscapeString(address);
        } else if (SELECTION_IN.equals(originalSelection)) {
            final String[] addresses = originalSelectionArgs[0].trim().split(",");

            qb.appendWhere(KEY_ADDRESS + " IN (");
            appendAddresses(qb, addresses);
            qb.appendWhere(")");
        } else if (SELECTION_NOTIN.equals(originalSelection)) {
            final String[] addresses = originalSelectionArgs[0].trim().split(",");

            qb.appendWhere(KEY_ADDRESS + " NOT IN (");
            appendAddresses(qb, addresses);
            qb.appendWhere(")");
        } else if (SELECTION_QUERY.equals(originalSelection)) {
            final String query = '%' + originalSelectionArgs[0].trim() + '%';
            selection = KEY_ADDRESS + " LIKE ? OR " + KEY_LABEL + " LIKE ?";
            selectionArgs = new String[] { query, query };
        }

        final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, selection, selectionArgs, null, null,
                sortOrder);

        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    private static void appendAddresses(final SQLiteQueryBuilder qb, final String[] addresses) {
        for (final String address : addresses) {
            qb.appendWhereEscapeString(address.trim());
            if (!address.equals(addresses[addresses.length - 1]))
                qb.appendWhere(",");
        }
    }

    private static class Helper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "address_book";
        private static final int DATABASE_VERSION = 1;

        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
                + KEY_ADDRESS + " TEXT NOT NULL, " //
                + KEY_LABEL + " TEXT NULL);";

        public Helper(final Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.beginTransaction();
            try {
                for (int v = oldVersion; v < newVersion; v++)
                    upgrade(db, v);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private void upgrade(final SQLiteDatabase db, final int oldVersion) {
            if (oldVersion == 1) {
                // future
            } else {
                throw new UnsupportedOperationException("old=" + oldVersion);
            }
        }
    }
}
