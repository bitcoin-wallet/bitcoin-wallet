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

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;

import android.arch.lifecycle.LiveData;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRateLiveData extends LiveData<ExchangeRate> implements OnSharedPreferenceChangeListener {
    private final Configuration config;
    private final CursorLoader loader;

    public ExchangeRateLiveData(final WalletApplication application) {
        this.config = application.getConfiguration();
        this.loader = new CursorLoader(application, ExchangeRatesProvider.contentUri(application.getPackageName(), false), null,
                ExchangeRatesProvider.KEY_CURRENCY_CODE, new String[] { null }, null) {
            @Override
            public void deliverResult(final Cursor cursor) {
                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    setValue(ExchangeRatesProvider.getExchangeRate(cursor));
                }
            }
        };
    }

    @Override
    protected void onActive() {
        loader.startLoading();
        config.registerOnSharedPreferenceChangeListener(this);
        onCurrencyChange();
    }

    @Override
    protected void onInactive() {
        config.unregisterOnSharedPreferenceChangeListener(this);
        loader.stopLoading();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
            onCurrencyChange();
    }

    private void onCurrencyChange() {
        final String exchangeCurrency = config.getExchangeCurrencyCode();
        loader.setSelectionArgs(new String[] { exchangeCurrency });
        loader.forceLoad();
    }
}
