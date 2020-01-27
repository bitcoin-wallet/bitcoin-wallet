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

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;

/**
 * @author Andreas Schildbach
 */
public class WalletDisclaimerViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private DisclaimerEnabledLiveData disclaimerEnabled;

    public WalletDisclaimerViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
    }

    public DisclaimerEnabledLiveData getDisclaimerEnabled() {
        if (disclaimerEnabled == null)
            disclaimerEnabled = new DisclaimerEnabledLiveData(application);
        return disclaimerEnabled;
    }

    public static class DisclaimerEnabledLiveData extends LiveData<Boolean>
            implements OnSharedPreferenceChangeListener {
        private final Configuration config;

        public DisclaimerEnabledLiveData(final WalletApplication application) {
            this.config = application.getConfiguration();
        }

        @Override
        protected void onActive() {
            config.registerOnSharedPreferenceChangeListener(this);
            setValue(config.getDisclaimerEnabled());
        }

        @Override
        protected void onInactive() {
            config.unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
            if (Configuration.PREFS_KEY_DISCLAIMER.equals(key))
                setValue(config.getDisclaimerEnabled());
        }
    }
}
