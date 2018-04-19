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

package de.schildbach.wallet.ui;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.WalletLiveData;
import de.schildbach.wallet.util.OnFirstPreDraw;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.MutableLiveData;

/**
 * @author Andreas Schildbach
 */
public class WalletViewModel extends AndroidViewModel implements OnFirstPreDraw.Callback {
    public static enum EnterAnimationState {
        WAITING, ANIMATING, FINISHED
    }

    private final WalletApplication application;
    public final WalletLiveData wallet;
    public final MutableLiveData<EnterAnimationState> enterAnimation = new MutableLiveData<>();
    private boolean doAnimation, globalLayoutFinished, balanceLoadingFinished, addressLoadingFinished,
            transactionsLoadingFinished;

    public WalletViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.wallet = new WalletLiveData(this.application);
    }

    public void animateWhenLoadingFinished() {
        doAnimation = true;
        maybeToggleState();
    }

    @Override
    public boolean onFirstPreDraw() {
        globalLayoutFinished = true;
        maybeToggleState();
        return true;
    }

    public void balanceLoadingFinished() {
        balanceLoadingFinished = true;
        maybeToggleState();
    }

    public void addressLoadingFinished() {
        addressLoadingFinished = true;
        maybeToggleState();
    }

    public void transactionsLoadingFinished() {
        transactionsLoadingFinished = true;
        maybeToggleState();
    }

    public void animationFinished() {
        enterAnimation.setValue(EnterAnimationState.FINISHED);
    }

    private void maybeToggleState() {
        if (enterAnimation.getValue() == null) {
            if (doAnimation && globalLayoutFinished)
                enterAnimation.setValue(EnterAnimationState.WAITING);
        } else if (enterAnimation.getValue() == EnterAnimationState.WAITING) {
            if (balanceLoadingFinished && addressLoadingFinished && transactionsLoadingFinished)
                enterAnimation.setValue(EnterAnimationState.ANIMATING);
        }
    }
}
