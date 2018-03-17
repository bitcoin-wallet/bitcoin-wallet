/*
 * Copyright 2011-2015 the original author or authors.
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

package de.schildbach.wallet;

import java.lang.reflect.Method;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.data.ExchangeRate;
import de.schildbach.wallet.data.ExchangeRatesProvider;
import de.schildbach.wallet.ui.RequestCoinsActivity;
import de.schildbach.wallet.ui.SendCoinsQrActivity;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.MonetarySpannable;
import de.schildbach.wallet_test.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.RemoteViews;

/**
 * @author Andreas Schildbach
 */
public class WalletBalanceWidgetProvider extends AppWidgetProvider {
    private static final Logger log = LoggerFactory.getLogger(WalletBalanceWidgetProvider.class);

    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        final WalletApplication application = (WalletApplication) context.getApplicationContext();
        final Coin balance = application.getWallet().getBalance(BalanceType.ESTIMATED);

        updateWidgets(context, appWidgetManager, appWidgetIds, balance);
    }

    @Override
    public void onAppWidgetOptionsChanged(final Context context, final AppWidgetManager appWidgetManager,
            final int appWidgetId, final Bundle newOptions) {
        if (newOptions != null)
            log.info("app widget {} options changed: minWidth={}", appWidgetId,
                    newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));

        final WalletApplication application = (WalletApplication) context.getApplicationContext();
        final Coin balance = application.getWallet().getBalance(BalanceType.ESTIMATED);

        updateWidget(context, appWidgetManager, appWidgetId, newOptions, balance);
    }

    public static void updateWidgets(final Context context, final Coin walletBalance) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final ComponentName providerName = new ComponentName(context, WalletBalanceWidgetProvider.class);

        try {
            final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(providerName);

            if (appWidgetIds.length > 0) {
                WalletBalanceWidgetProvider.updateWidgets(context, appWidgetManager, appWidgetIds, walletBalance);
            }
        } catch (final RuntimeException x) // system server dead?
        {
            log.warn("cannot update app widgets", x);
        }
    }

    private static void updateWidgets(final Context context, final AppWidgetManager appWidgetManager,
            final int[] appWidgetIds, final Coin balance) {
        for (final int appWidgetId : appWidgetIds) {
            final Bundle options = getAppWidgetOptions(appWidgetManager, appWidgetId);
            updateWidget(context, appWidgetManager, appWidgetId, options, balance);
        }
    }

    private static void updateWidget(final Context context, final AppWidgetManager appWidgetManager,
            final int appWidgetId, final Bundle appWidgetOptions, final Coin balance) {
        final WalletApplication application = (WalletApplication) context.getApplicationContext();
        final Configuration config = application.getConfiguration();
        final MonetaryFormat btcFormat = config.getFormat();

        final Spannable balanceStr = new MonetarySpannable(btcFormat.noCode(), balance).applyMarkup(null,
                MonetarySpannable.STANDARD_INSIGNIFICANT_SPANS);

        final Cursor data = context.getContentResolver().query(
                ExchangeRatesProvider.contentUri(context.getPackageName(), true), null,
                ExchangeRatesProvider.KEY_CURRENCY_CODE, new String[] { config.getExchangeCurrencyCode() }, null);
        final Spannable localBalanceStr;
        if (data != null) {
            if (data.moveToFirst()) {
                final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
                final Fiat localBalance = exchangeRate.rate.coinToFiat(balance);
                final MonetaryFormat localFormat = Constants.LOCAL_FORMAT.code(0,
                        Constants.PREFIX_ALMOST_EQUAL_TO + GenericUtils.currencySymbol(exchangeRate.getCurrencyCode()));
                final Object[] prefixSpans = new Object[] { MonetarySpannable.SMALLER_SPAN,
                        new ForegroundColorSpan(context.getResources().getColor(R.color.fg_less_significant)) };
                localBalanceStr = new MonetarySpannable(localFormat, localBalance).applyMarkup(prefixSpans,
                        MonetarySpannable.STANDARD_INSIGNIFICANT_SPANS);
            } else {
                localBalanceStr = null;
            }

            data.close();
        } else {
            localBalanceStr = null;
        }

        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.wallet_balance_widget_content);

        final String currencyCode = btcFormat.code();
        if (MonetaryFormat.CODE_BTC.equals(currencyCode))
            views.setImageViewResource(R.id.widget_wallet_prefix, R.drawable.currency_symbol_btc);
        else if (MonetaryFormat.CODE_MBTC.equals(currencyCode))
            views.setImageViewResource(R.id.widget_wallet_prefix, R.drawable.currency_symbol_mbtc);
        else if (MonetaryFormat.CODE_UBTC.equals(currencyCode))
            views.setImageViewResource(R.id.widget_wallet_prefix, R.drawable.currency_symbol_ubtc);

        views.setTextViewText(R.id.widget_wallet_balance_btc, balanceStr);
        views.setViewVisibility(R.id.widget_wallet_balance_local, localBalanceStr != null ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.widget_wallet_balance_local, localBalanceStr);

        if (appWidgetOptions != null) {
            final int minWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            views.setViewVisibility(R.id.widget_app_icon, minWidth > 400 ? View.VISIBLE : View.GONE);
            views.setViewVisibility(R.id.widget_button_request, minWidth > 300 ? View.VISIBLE : View.GONE);
            views.setViewVisibility(R.id.widget_button_send, minWidth > 300 ? View.VISIBLE : View.GONE);
            views.setViewVisibility(R.id.widget_button_send_qr, minWidth > 200 ? View.VISIBLE : View.GONE);
        }

        views.setOnClickPendingIntent(R.id.widget_button_balance,
                PendingIntent.getActivity(context, 0, new Intent(context, WalletActivity.class), 0));
        views.setOnClickPendingIntent(R.id.widget_button_request,
                PendingIntent.getActivity(context, 0, new Intent(context, RequestCoinsActivity.class), 0));
        views.setOnClickPendingIntent(R.id.widget_button_send,
                PendingIntent.getActivity(context, 0, new Intent(context, SendCoinsActivity.class), 0));
        views.setOnClickPendingIntent(R.id.widget_button_send_qr,
                PendingIntent.getActivity(context, 0, new Intent(context, SendCoinsQrActivity.class), 0));

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static Bundle getAppWidgetOptions(final AppWidgetManager appWidgetManager, final int appWidgetId) {
        try {
            final Method getAppWidgetOptions = AppWidgetManager.class.getMethod("getAppWidgetOptions", Integer.TYPE);
            final Bundle options = (Bundle) getAppWidgetOptions.invoke(appWidgetManager, appWidgetId);
            return options;
        } catch (final Exception x) {
            return null;
        }
    }
}
