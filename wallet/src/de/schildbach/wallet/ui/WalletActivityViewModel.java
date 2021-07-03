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
import android.os.AsyncTask;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AbstractWalletLiveData;
import de.schildbach.wallet.util.OnFirstPreDraw;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;

/**
 * @author Andreas Schildbach
 */
public class WalletActivityViewModel extends AndroidViewModel implements OnFirstPreDraw.Callback {
    public enum EnterAnimationState {
        WAITING, ANIMATING, FINISHED
    }

    private final WalletApplication application;
    public final WalletEncryptedLiveData walletEncrypted;
    public final WalletLegacyFallbackLiveData walletLegacyFallback;
    public final MutableLiveData<Event<Integer>> showHelpDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showBackupWalletDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showRestoreWalletDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showEncryptKeysDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showReportIssueDialog = new MutableLiveData<>();
    public final MutableLiveData<Event<Void>> showReportCrashDialog = new MutableLiveData<>();
    public final MutableLiveData<EnterAnimationState> enterAnimation = new MutableLiveData<>();
    private boolean doAnimation, globalLayoutFinished, balanceLoadingFinished, addressLoadingFinished,
            transactionsLoadingFinished;

    public WalletActivityViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.walletEncrypted = new WalletEncryptedLiveData(this.application);
        this.walletLegacyFallback = new WalletLegacyFallbackLiveData(this.application);
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

    public static class WalletEncryptedLiveData extends AbstractWalletLiveData<Boolean> {
        public WalletEncryptedLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            load();
        }

        @Override
        protected void load() {
            final Wallet wallet = getWallet();
            AsyncTask.execute(() -> {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                postValue(wallet.isEncrypted());
            });
        }
    }

    public static class WalletLegacyFallbackLiveData extends AbstractWalletLiveData<Boolean> {
        public WalletLegacyFallbackLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            load();
        }

        @Override
        protected void load() {
            final Wallet wallet = getWallet();
            AsyncTask.execute(() -> {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                postValue(wallet.getActiveKeyChain().getOutputScriptType() == Script.ScriptType.P2WPKH
                        && wallet.getActiveKeyChains().get(0).getOutputScriptType() != Script.ScriptType.P2WPKH);
            });
        }
    }
}
