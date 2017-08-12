/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.service;

import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

/**
 * This service upgrades the wallet to an HD wallet. Use {@link #startUpgrade(Context)} to start the process.
 * 
 * It will upgrade and then hand over to {@Link BlockchainService} to pre-generate the look-ahead keys. If the
 * wallet is already upgraded, it will do nothing.
 * 
 * @author Andreas Schildbach
 */
public final class UpgradeWalletService extends IntentService {
    public static void startUpgrade(final Context context) {
        context.startService(new Intent(context, UpgradeWalletService.class));
    }

    private WalletApplication application;

    private static final Logger log = LoggerFactory.getLogger(UpgradeWalletService.class);

    public UpgradeWalletService() {
        super(UpgradeWalletService.class.getName());

        setIntentRedelivery(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        application = (WalletApplication) getApplication();

    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

        final Wallet wallet = application.getWallet();

        if (wallet.isDeterministicUpgradeRequired()) {
            log.info("detected non-HD wallet, upgrading");

            // upgrade wallet to HD
            wallet.upgradeToDeterministic(null);

            // let other service pre-generate look-ahead keys
            application.startBlockchainService(false);
        }

        maybeUpgradeToSecureChain(wallet);
    }

    private void maybeUpgradeToSecureChain(final Wallet wallet) {
        try {
            wallet.doMaintenance(null, false);

            // let other service pre-generate look-ahead keys
            application.startBlockchainService(false);
        } catch (final Exception x) {
            log.error("failed doing wallet maintenance", x);
        }
    }
}
