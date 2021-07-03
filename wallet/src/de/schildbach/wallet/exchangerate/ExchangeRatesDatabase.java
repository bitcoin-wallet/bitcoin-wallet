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

package de.schildbach.wallet.exchangerate;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * @author Andreas Schildbach
 */
@Database(entities = { ExchangeRateEntry.class }, version = 1, exportSchema = false)
public abstract class ExchangeRatesDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "exchange_rates";
    private static ExchangeRatesDatabase INSTANCE;

    public static ExchangeRatesDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ExchangeRatesDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), ExchangeRatesDatabase.class,
                            DATABASE_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract ExchangeRateDao exchangeRateDao();
}
