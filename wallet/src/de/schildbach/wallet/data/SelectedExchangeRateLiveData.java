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

package de.schildbach.wallet.data;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.InvalidationTracker;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.exchangerate.ExchangeRateDao;
import de.schildbach.wallet.exchangerate.ExchangeRateEntry;
import de.schildbach.wallet.exchangerate.ExchangeRatesRepository;

import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public class SelectedExchangeRateLiveData extends LiveData<ExchangeRateEntry> implements OnSharedPreferenceChangeListener {
    private final Configuration config;
    private final ExchangeRateDao dao;
    private final InvalidationTracker invalidationTracker;

    private final InvalidationTracker.Observer invalidationObserver =
            new InvalidationTracker.Observer(ExchangeRateEntry.TABLE_NAME) {
        @Override
        public void onInvalidated(@NonNull final Set<String> tables) {
            onChange();
        }
    };

    public SelectedExchangeRateLiveData(final WalletApplication application) {
        this.config = application.getConfiguration();
        final ExchangeRatesRepository exchangeRatesRepository = ExchangeRatesRepository.get(application);
        this.dao = exchangeRatesRepository.exchangeRateDao();
        this.invalidationTracker = exchangeRatesRepository.exchangeRateInvalidationTracker();
    }

    @Override
    protected void onActive() {
        invalidationTracker.addObserver(invalidationObserver);
        config.registerOnSharedPreferenceChangeListener(this);
        onChange();
    }

    @Override
    protected void onInactive() {
        config.unregisterOnSharedPreferenceChangeListener(this);
        invalidationTracker.removeObserver(invalidationObserver);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key) ||
                Configuration.PREFS_KEY_ENABLE_EXCHANGE_RATES.equals(key))
            onChange();
    }

    private void onChange() {
        AsyncTask.execute(() -> {
            if (config.isEnableExchangeRates()) {
                final String currencyCode = config.getExchangeCurrencyCode();
                final ExchangeRateEntry exchangeRate = dao.findByCurrencyCode(currencyCode);
                postValue(exchangeRate);
            } else {
                postValue(null);
            }
        });
    }
}
