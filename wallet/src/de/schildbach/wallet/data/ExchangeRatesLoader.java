/*
 * Copyright 2012-2015 the original author or authors.
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

import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

/**
 * @author Andreas Schildbach
 */
public final class ExchangeRatesLoader extends CursorLoader implements OnSharedPreferenceChangeListener {
    private final Configuration config;

    public ExchangeRatesLoader(final Context context, final Configuration config) {
        super(context, ExchangeRatesProvider.contentUri(context.getPackageName(), false), null,
                ExchangeRatesProvider.KEY_CURRENCY_CODE, new String[] { null }, null);

        this.config = config;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        config.registerOnSharedPreferenceChangeListener(this);

        onCurrencyChange();
    }

    @Override
    protected void onStopLoading() {
        config.unregisterOnSharedPreferenceChangeListener(this);

        super.onStopLoading();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
            onCurrencyChange();
    }

    private void onCurrencyChange() {
        final String exchangeCurrency = config.getExchangeCurrencyCode();

        setSelectionArgs(new String[] { exchangeCurrency });

        forceLoad();
    }
}
