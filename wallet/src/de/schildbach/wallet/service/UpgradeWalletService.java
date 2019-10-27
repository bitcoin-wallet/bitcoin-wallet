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

package de.schildbach.wallet.service;

import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * This service upgrades the wallet to a deterministic wallet. Use {@link #startUpgrade(Context)} to start the
 * process.
 * 
 * It will upgrade and then hand over to {@link BlockchainService} to pre-generate the look-ahead keys. If the
 * wallet is already upgraded, it will do nothing.
 * 
 * @author Andreas Schildbach
 */
public final class UpgradeWalletService extends IntentService {
    public static void startUpgrade(final Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, UpgradeWalletService.class));
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationCompat.Builder notification = new NotificationCompat.Builder(this,
                    Constants.NOTIFICATION_CHANNEL_ID_ONGOING);
            notification.setSmallIcon(R.drawable.stat_notify_received_24dp);
            notification.setWhen(System.currentTimeMillis());
            notification.setOngoing(true);
            startForeground(Constants.NOTIFICATION_ID_MAINTENANCE, notification.build());
        }
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
        final Wallet wallet = application.getWallet();

        // Maybe upgrade wallet from basic to deterministic
        if (wallet.isDeterministicUpgradeRequired(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE) && !wallet.isEncrypted())
            wallet.upgradeToDeterministic(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE, null);

        // Maybe upgrade wallet to secure chain
        try {
            wallet.doMaintenance(null, false);
        } catch (final Exception x) {
            log.error("failed doing wallet maintenance", x);
        }

        // Let other service pre-generate look-ahead keys
        BlockchainService.start(this, false);
    }
}
