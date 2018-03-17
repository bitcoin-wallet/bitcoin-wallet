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

import de.schildbach.wallet.WalletApplication;

import android.arch.lifecycle.LiveData;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

/**
 * @author Andreas Schildbach
 */
public class AddressBookChangeLiveData extends LiveData<Void> {
    private final ContentResolver contentResolver;
    private final Uri contentUri;
    private final Handler handler = new Handler();

    public AddressBookChangeLiveData(final WalletApplication application) {
        this.contentResolver = application.getContentResolver();
        this.contentUri = AddressBookProvider.contentUri(application.getPackageName());
    }

    @Override
    protected void onActive() {
        contentResolver.registerContentObserver(contentUri, true, contentObserver);
        setValue(null);
    }

    @Override
    protected void onInactive() {
        contentResolver.unregisterContentObserver(contentObserver);
    }

    private final ContentObserver contentObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            setValue(null);
        }
    };
}