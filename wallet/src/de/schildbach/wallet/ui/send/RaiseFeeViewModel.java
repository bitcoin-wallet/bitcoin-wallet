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

package de.schildbach.wallet.ui.send;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.DynamicFeeLiveData;

/**
 * @author Andreas Schildbach
 */
public class RaiseFeeViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private DynamicFeeLiveData dynamicFees;

    public RaiseFeeViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
    }

    public DynamicFeeLiveData getDynamicFees() {
        if (dynamicFees == null)
            dynamicFees = new DynamicFeeLiveData(application);
        return dynamicFees;
    }
}
