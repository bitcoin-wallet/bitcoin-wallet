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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * @author Andreas Schildbach
 */
@Dao
public interface ExchangeRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ExchangeRateEntry exchangeRateEntry);

    @Query("SELECT * FROM exchange_rates ORDER BY currency_code COLLATE LOCALIZED ASC")
    LiveData<List<ExchangeRateEntry>> findAll();

    @Query("SELECT * FROM exchange_rates WHERE currency_code LIKE '%' || :constraint || '%' ORDER BY currency_code " +
            "COLLATE LOCALIZED ASC")
    LiveData<List<ExchangeRateEntry>> findByConstraint(String constraint);

    @Query("SELECT * FROM exchange_rates WHERE currency_code = :currencyCode")
    ExchangeRateEntry findByCurrencyCode(String currencyCode);
}
