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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;
import java.util.Set;

/**
 * @author Andreas Schildbach
 */
@Dao
public interface AddressBookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(AddressBookEntry addressBookEntry);

    @Query("DELETE FROM address_book WHERE address = :address")
    void delete(String address);

    @Query("SELECT label FROM address_book WHERE address = :address")
    String resolveLabel(String address);

    @Query("SELECT * FROM address_book WHERE address LIKE '%' || :constraint || '%' OR label LIKE '%' || :constraint || '%' ORDER BY label COLLATE LOCALIZED ASC")
    List<AddressBookEntry> get(String constraint);

    @Query("SELECT * FROM address_book ORDER BY label COLLATE LOCALIZED ASC")
    LiveData<List<AddressBookEntry>> getAll();

    @Query("SELECT * FROM address_book WHERE address NOT IN (:except) ORDER BY label COLLATE LOCALIZED ASC")
    LiveData<List<AddressBookEntry>> getAllExcept(Set<String> except);
}
