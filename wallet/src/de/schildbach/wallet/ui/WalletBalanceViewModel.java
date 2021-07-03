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
import androidx.lifecycle.AndroidViewModel;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.SelectedExchangeRateLiveData;
import de.schildbach.wallet.data.WalletBalanceLiveData;

/**
 * @author Andreas Schildbach
 */
public class WalletBalanceViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private WalletBalanceLiveData balance;
    private SelectedExchangeRateLiveData exchangeRate;

    public WalletBalanceViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
    }

    public WalletBalanceLiveData getBalance() {
        if (balance == null)
            balance = new WalletBalanceLiveData(application);
        return balance;
    }

    public SelectedExchangeRateLiveData getExchangeRate() {
        if (exchangeRate == null)
            exchangeRate = new SelectedExchangeRateLiveData(application);
        return exchangeRate;
    }
}
