/*
 * Copyright 2016 the original author or authors.
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

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.ui.send.FeeCategory;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet_test.R;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

/**
 * This service is responsible for showing a notification if the user hasn't used the app for a longer time.
 *
 * @author Andreas Schildbach
 */
public final class InactivityNotificationService extends IntentService {
    public static void startMaybeShowNotification(final Context context) {
        context.startService(new Intent(context, InactivityNotificationService.class));
    }

    private NotificationManager nm;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;

    private static final String ACTION_DISMISS = InactivityNotificationService.class.getPackage().getName()
            + ".dismiss";
    private static final String ACTION_DISMISS_FOREVER = InactivityNotificationService.class.getPackage().getName()
            + ".dismiss_forever";
    private static final String ACTION_DONATE = InactivityNotificationService.class.getPackage().getName() + ".donate";

    private static final Logger log = LoggerFactory.getLogger(InactivityNotificationService.class);

    public InactivityNotificationService() {
        super(InactivityNotificationService.class.getName());

        setIntentRedelivery(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        application = (WalletApplication) getApplication();
        config = application.getConfiguration();
        wallet = application.getWallet();
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

        if (ACTION_DISMISS.equals(intent.getAction()))
            handleDismiss();
        else if (ACTION_DISMISS_FOREVER.equals(intent.getAction()))
            handleDismissForever();
        else if (ACTION_DONATE.equals(intent.getAction()))
            handleDonate();
        else
            handleMaybeShowNotification();
    }

    private void handleMaybeShowNotification() {
        final Coin estimatedBalance = wallet.getBalance(BalanceType.ESTIMATED_SPENDABLE);

        if (estimatedBalance.isPositive()) {
            log.info("detected balance, showing inactivity notification");

            final Coin availableBalance = wallet.getBalance(BalanceType.AVAILABLE_SPENDABLE);
            final boolean canDonate = Constants.DONATION_ADDRESS != null && availableBalance.isPositive();

            final MonetaryFormat btcFormat = config.getFormat();
            final String title = getString(R.string.notification_inactivity_title);
            final StringBuilder text = new StringBuilder(
                    getString(R.string.notification_inactivity_message, btcFormat.format(estimatedBalance)));
            if (canDonate)
                text.append("\n\n").append(getString(R.string.notification_inactivity_message_donate));

            final Intent dismissIntent = new Intent(this, InactivityNotificationService.class);
            dismissIntent.setAction(ACTION_DISMISS);
            final Intent dismissForeverIntent = new Intent(this, InactivityNotificationService.class);
            dismissForeverIntent.setAction(ACTION_DISMISS_FOREVER);
            final Intent donateIntent = new Intent(this, InactivityNotificationService.class);
            donateIntent.setAction(ACTION_DONATE);

            final NotificationCompat.Builder notification = new NotificationCompat.Builder(this);
            notification.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
            notification.setSmallIcon(R.drawable.stat_notify_received_24dp);
            notification.setContentTitle(title);
            notification.setContentText(text);
            notification
                    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
            notification.setAutoCancel(true);
            if (!canDonate)
                notification.addAction(new NotificationCompat.Action.Builder(0,
                        getString(R.string.notification_inactivity_action_dismiss),
                        PendingIntent.getService(this, 0, dismissIntent, 0)).build());
            notification.addAction(new NotificationCompat.Action.Builder(0,
                    getString(R.string.notification_inactivity_action_dismiss_forever),
                    PendingIntent.getService(this, 0, dismissForeverIntent, 0)).build());
            if (canDonate)
                notification
                        .addAction(new NotificationCompat.Action.Builder(0, getString(R.string.wallet_options_donate),
                                PendingIntent.getService(this, 0, donateIntent, 0)).build());
            nm.notify(Constants.NOTIFICATION_ID_INACTIVITY, notification.build());
        }
    }

    private void handleDismiss() {
        log.info("dismissing inactivity notification");
        nm.cancel(Constants.NOTIFICATION_ID_INACTIVITY);
    }

    private void handleDismissForever() {
        log.info("dismissing inactivity notification forever");
        config.setRemindBalance(false);
        nm.cancel(Constants.NOTIFICATION_ID_INACTIVITY);
    }

    private void handleDonate() {
        final Coin balance = wallet.getBalance(BalanceType.AVAILABLE_SPENDABLE);
        SendCoinsActivity.startDonate(this, balance, FeeCategory.ECONOMIC,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        nm.cancel(Constants.NOTIFICATION_ID_INACTIVITY);
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
}
