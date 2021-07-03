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

import android.os.Handler;
import androidx.lifecycle.Observer;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletApplication.OnWalletLoadedListener;
import de.schildbach.wallet.ui.Event;
import org.bitcoinj.wallet.Wallet;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletLiveData<T> extends ThrottelingLiveData<T> implements Observer<Event<Void>> {
    private final WalletApplication application;
    private final Handler handler = new Handler();
    private Wallet wallet;

    public AbstractWalletLiveData(final WalletApplication application) {
        super();
        this.application = application;
    }

    public AbstractWalletLiveData(final WalletApplication application, final long throttleMs) {
        super(throttleMs);
        this.application = application;
    }

    @Override
    protected final void onActive() {
        application.walletChanged.observeForever(this);
        loadWallet();
    }

    @Override
    protected final void onInactive() {
        // TODO cancel async loading
        if (wallet != null)
            onWalletInactive(wallet);
        application.walletChanged.removeObserver(this);
    }

    private void loadWallet() {
        application.getWalletAsync(onWalletLoadedListener);
    }

    protected Wallet getWallet() {
        return wallet;
    }

    private final OnWalletLoadedListener onWalletLoadedListener = wallet -> handler.post(() -> {
        AbstractWalletLiveData.this.wallet = wallet;
        onWalletActive(wallet);
    });

    @Override
    public void onChanged(final Event<Void> v) {
        if (wallet != null)
            onWalletInactive(wallet);
        loadWallet();
    }

    protected abstract void onWalletActive(Wallet wallet);

    protected void onWalletInactive(final Wallet wallet) {
        // do nothing by default
    }
}
