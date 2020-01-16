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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.ui.send.FeeCategory;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class BootstrapReceiver extends BroadcastReceiver {
    private static final Logger log = LoggerFactory.getLogger(BootstrapReceiver.class);

    private static final String ACTION_DISMISS = BootstrapReceiver.class.getPackage().getName() + ".dismiss";
    private static final String ACTION_DISMISS_FOREVER = BootstrapReceiver.class.getPackage().getName() +
            ".dismiss_forever";
    private static final String ACTION_DONATE = BootstrapReceiver.class.getPackage().getName() + ".donate";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        log.info("got broadcast: " + intent);
        final PendingResult result = goAsync();
        AsyncTask.execute(() -> {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
            onAsyncReceive(context, intent);
            result.finish();
        });
    }

    @WorkerThread
    private void onAsyncReceive(final Context context, final Intent intent) {
        final WalletApplication application = (WalletApplication) context.getApplicationContext();

        final String action = intent.getAction();
        final boolean bootCompleted = Intent.ACTION_BOOT_COMPLETED.equals(action);
        final boolean packageReplaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);

        if (packageReplaced || bootCompleted) {
            // make sure wallet is upgraded to HD
            if (packageReplaced)
                maybeUpgradeWallet(application.getWallet());

            // make sure there is always a blockchain sync scheduled
            StartBlockchainService.schedule(application, true);

            // if the app hasn't been used for a while and contains coins, maybe show reminder
            maybeShowInactivityNotification(application);
        } else if (ACTION_DISMISS.equals(action)) {
            dismissNotification(context);
        } else if (ACTION_DISMISS_FOREVER.equals(action)) {
            dismissNotificationForever(context, application.getConfiguration());
        } else if (ACTION_DONATE.equals(action)) {
            donate(context, application.getWallet());
        } else {
            throw new IllegalArgumentException(action);
        }
    }

    @WorkerThread
    private void maybeUpgradeWallet(final Wallet wallet) {
        log.info("maybe upgrading wallet");

        // Maybe upgrade wallet from basic to deterministic, and maybe upgrade to the latest script type
        if (wallet.isDeterministicUpgradeRequired(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE) && !wallet.isEncrypted())
            wallet.upgradeToDeterministic(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE, null);

        // Maybe upgrade wallet to secure chain
        try {
            wallet.doMaintenance(null, false);
        } catch (final Exception x) {
            log.error("failed doing wallet maintenance", x);
        }
    }

    @WorkerThread
    private void maybeShowInactivityNotification(final WalletApplication application) {
        final Configuration config = application.getConfiguration();
        if (!config.remindBalance() || !config.hasBeenUsed() || config.getLastUsedAgo() <= Constants.LAST_USAGE_THRESHOLD_INACTIVE_MS)
            return;

        final Wallet wallet = application.getWallet();
        final Coin estimatedBalance = wallet.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE);
        if (!estimatedBalance.isPositive())
            return;

        log.info("detected balance, showing inactivity notification");

        final Coin availableBalance = wallet.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE);
        final boolean canDonate =
                Constants.DONATION_ADDRESS != null && !availableBalance.isLessThan(Constants.SOME_BALANCE_THRESHOLD);

        final MonetaryFormat btcFormat = config.getFormat();
        final String title = application.getString(R.string.notification_inactivity_title);
        final StringBuilder text = new StringBuilder(application.getString(R.string.notification_inactivity_message,
                btcFormat.format(estimatedBalance)));
        if (canDonate)
            text.append("\n\n").append(application.getString(R.string.notification_inactivity_message_donate));

        final NotificationCompat.Builder notification = new NotificationCompat.Builder(application,
                Constants.NOTIFICATION_CHANNEL_ID_IMPORTANT);
        notification.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
        notification.setColor(application.getColor(R.color.fg_network_significant));
        notification.setSmallIcon(R.drawable.stat_notify_received_24dp);
        notification.setContentTitle(title);
        notification.setContentText(text);
        notification.setContentIntent(PendingIntent.getActivity(application, 0, new Intent(application, WalletActivity.class),
                0));
        notification.setAutoCancel(true);

        if (!canDonate) {
            final Intent dismissIntent = new Intent(application, BootstrapReceiver.class);
            dismissIntent.setAction(ACTION_DISMISS);
            notification.addAction(new NotificationCompat.Action.Builder(0,
                    application.getString(R.string.notification_inactivity_action_dismiss),
                    PendingIntent.getBroadcast(application, 0, dismissIntent, 0)).build());
        }

        final Intent dismissForeverIntent = new Intent(application, BootstrapReceiver.class);
        dismissForeverIntent.setAction(ACTION_DISMISS_FOREVER);
        notification.addAction(new NotificationCompat.Action.Builder(0,
                application.getString(R.string.notification_inactivity_action_dismiss_forever),
                PendingIntent.getBroadcast(application, 0, dismissForeverIntent, 0)).build());

        if (canDonate) {
            final Intent donateIntent = new Intent(application, BootstrapReceiver.class);
            donateIntent.setAction(ACTION_DONATE);
            notification.addAction(new NotificationCompat.Action.Builder(0,
                    application.getString(R.string.wallet_options_donate), PendingIntent.getBroadcast(application, 0,
                    donateIntent, 0)).build());
        }

        final NotificationManager nm = (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(Constants.NOTIFICATION_ID_INACTIVITY, notification.build());
    }

    @WorkerThread
    private void dismissNotification(final Context context) {
        log.info("dismissing inactivity notification");
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(Constants.NOTIFICATION_ID_INACTIVITY);
    }

    @WorkerThread
    private void dismissNotificationForever(final Context context, final Configuration config) {
        log.info("dismissing inactivity notification forever");
        config.setRemindBalance(false);
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(Constants.NOTIFICATION_ID_INACTIVITY);
    }

    @WorkerThread
    private void donate(final Context context, final Wallet wallet) {
        final Coin balance = wallet.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE);
        SendCoinsActivity.startDonate(context, balance, FeeCategory.ECONOMIC,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(Constants.NOTIFICATION_ID_INACTIVITY);
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
}
