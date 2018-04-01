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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.data;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Strings;

import de.schildbach.wallet.WalletApplication;

import android.arch.lifecycle.LiveData;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;

/**
 * @author Andreas Schildbach
 */
public class AddressBookLiveData extends LiveData<Map<String, String>>
        implements Loader.OnLoadCompleteListener<Cursor> {
    private final CursorLoader loader;

    public AddressBookLiveData(final WalletApplication application) {
        this.loader = new CursorLoader(application, AddressBookProvider.contentUri(application.getPackageName()), null,
                AddressBookProvider.SELECTION_QUERY, new String[] { "" },
                AddressBookProvider.KEY_LABEL + " COLLATE LOCALIZED ASC");
    }

    @Override
    protected void onActive() {
        loader.registerListener(0, this);
        loader.startLoading();
    }

    @Override
    protected void onInactive() {
        loader.stopLoading();
        loader.unregisterListener(this);
    }

    @Override
    public void onLoadComplete(final Loader<Cursor> loader, final Cursor cursor) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                final Map<String, String> addressBook = new LinkedHashMap<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    final String address = cursor
                            .getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
                    final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
                    addressBook.put(address, label);
                }
                postValue(addressBook);
            }
        });
    }

    public void setConstraint(final String constraint) {
        loader.setSelectionArgs(new String[] { Strings.nullToEmpty(constraint) });
        loader.forceLoad();
    }
}
