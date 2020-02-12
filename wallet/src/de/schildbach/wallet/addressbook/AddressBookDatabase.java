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

package de.schildbach.wallet.addressbook;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * @author Andreas Schildbach
 */
@Database(entities = { AddressBookEntry.class }, version = 2, exportSchema = false)
public abstract class AddressBookDatabase extends RoomDatabase {
    public abstract AddressBookDao addressBookDao();

    private static final String DATABASE_NAME = "address_book";
    private static AddressBookDatabase INSTANCE;

    public static AddressBookDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AddressBookDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AddressBookDatabase.class, DATABASE_NAME)
                            .addMigrations(MIGRATION_1_2).allowMainThreadQueries().build();
                }
            }
        }
        return INSTANCE;
    }

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE address_book_new (address TEXT NOT NULL, label TEXT, PRIMARY KEY(address))");
            database.execSQL(
                    "INSERT OR IGNORE INTO address_book_new (address, label) SELECT address, label FROM address_book");
            database.execSQL("DROP TABLE address_book");
            database.execSQL("ALTER TABLE address_book_new RENAME TO address_book");
        }
    };
}
