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

package de.schildbach.wallet.ui;

import com.google.common.base.Strings;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.ExchangeRatesProvider;
import de.schildbach.wallet.data.WalletBalanceLiveData;

import android.app.Application;
import android.database.Cursor;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.loader.content.CursorLoader;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private ExchangeRatesLiveData exchangeRates;
    private WalletBalanceLiveData balance;

    public @Nullable String query = null;

    public ExchangeRatesViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
    }

    public ExchangeRatesLiveData getExchangeRates() {
        if (exchangeRates == null)
            exchangeRates = new ExchangeRatesLiveData(application);
        return exchangeRates;
    }

    public WalletBalanceLiveData getBalance() {
        if (balance == null)
            balance = new WalletBalanceLiveData(application);
        return balance;
    }

    public static class ExchangeRatesLiveData extends LiveData<Cursor> {
        private final CursorLoader loader;

        public ExchangeRatesLiveData(final WalletApplication application) {
            this.loader = new CursorLoader(application,
                    ExchangeRatesProvider.contentUri(application.getPackageName()), null,
                    ExchangeRatesProvider.QUERY_PARAM_Q, new String[] { "" }, null) {
                @Override
                public void deliverResult(final Cursor cursor) {
                    if (cursor != null)
                        setValue(cursor);
                }
            };
        }

        @Override
        protected void onActive() {
            loader.startLoading();
        }

        @Override
        protected void onInactive() {
            loader.stopLoading();
        }

        public void setQuery(final String query) {
            loader.setSelectionArgs(new String[] { Strings.nullToEmpty(query) });
            loader.forceLoad();
        }
    }
}
