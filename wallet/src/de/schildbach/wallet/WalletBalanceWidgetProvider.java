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

package de.schildbach.wallet;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.RemoteViews;
import androidx.annotation.Nullable;
import de.schildbach.wallet.exchangerate.ExchangeRateEntry;
import de.schildbach.wallet.exchangerate.ExchangeRatesRepository;
import de.schildbach.wallet.ui.RequestCoinsActivity;
import de.schildbach.wallet.ui.SendCoinsQrActivity;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.MonetarySpannable;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * @author Andreas Schildbach
 */
public class WalletBalanceWidgetProvider extends AppWidgetProvider {
    private static final StrikethroughSpan STRIKE_THRU_SPAN = new StrikethroughSpan();

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceWidgetProvider.class);

    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        final PendingResult result = goAsync();
        AsyncTask.execute(() -> {
            final WalletApplication application = (WalletApplication) context.getApplicationContext();
            final Coin balance = application.getWallet().getBalance(BalanceType.ESTIMATED);
            final Configuration config = application.getConfiguration();
            final ExchangeRatesRepository exchangeRatesRepository = ExchangeRatesRepository.get(application);
            final ExchangeRateEntry exchangeRate = config.isEnableExchangeRates() ?
                    exchangeRatesRepository.exchangeRateDao().findByCurrencyCode(config.getExchangeCurrencyCode()) : null;
            updateWidgets(context, appWidgetManager, appWidgetIds, balance, exchangeRate != null ?
                    exchangeRate.exchangeRate() : null);
            result.finish();
        });
    }

    @Override
    public void onAppWidgetOptionsChanged(final Context context, final AppWidgetManager appWidgetManager,
            final int appWidgetId, final Bundle newOptions) {
        if (newOptions != null)
            log.info("app widget {} options changed: minWidth={}", appWidgetId,
                    newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        final PendingResult result = goAsync();
        AsyncTask.execute(() -> {
            final WalletApplication application = (WalletApplication) context.getApplicationContext();
            final Coin balance = application.getWallet().getBalance(BalanceType.ESTIMATED);
            final Configuration config = application.getConfiguration();
            final ExchangeRatesRepository exchangeRatesRepository = ExchangeRatesRepository.get(application);
            final ExchangeRateEntry exchangeRate = config.isEnableExchangeRates() ?
                    exchangeRatesRepository.exchangeRateDao().findByCurrencyCode(config.getExchangeCurrencyCode()) : null;
            updateWidget(context, appWidgetManager, appWidgetId, newOptions, balance, exchangeRate != null ?
                    exchangeRate.exchangeRate() : null);
            result.finish();
        });
    }

    public static void updateWidgets(final Context context, final Coin balance,
            final @Nullable ExchangeRate exchangeRate) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final ComponentName providerName = new ComponentName(context, WalletBalanceWidgetProvider.class);

        try {
            final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(providerName);
            if (appWidgetIds.length > 0)
                WalletBalanceWidgetProvider.updateWidgets(context, appWidgetManager, appWidgetIds, balance,
                        exchangeRate);
        } catch (final RuntimeException x) // system server dead?
        {
            log.warn("cannot update app widgets", x);
        }
    }

    private static void updateWidgets(final Context context, final AppWidgetManager appWidgetManager,
            final int[] appWidgetIds, final Coin balance, final @Nullable ExchangeRate exchangeRate) {
        for (final int appWidgetId : appWidgetIds) {
            final Bundle options = getAppWidgetOptions(appWidgetManager, appWidgetId);
            updateWidget(context, appWidgetManager, appWidgetId, options, balance, exchangeRate);
        }
    }

    private static void updateWidget(final Context context, final AppWidgetManager appWidgetManager,
            final int appWidgetId, final Bundle appWidgetOptions, final Coin balance,
            final @Nullable ExchangeRate exchangeRate) {
        final WalletApplication application = (WalletApplication) context.getApplicationContext();
        final Configuration config = application.getConfiguration();
        final MonetaryFormat btcFormat = config.getFormat();

        final Spannable balanceStr = new MonetarySpannable(btcFormat.noCode(), balance).applyMarkup(null,
                MonetarySpannable.STANDARD_INSIGNIFICANT_SPANS);
        final Spannable localBalanceStr;
        if (exchangeRate != null) {
            final Fiat localBalance = exchangeRate.coinToFiat(balance);
            final MonetaryFormat localFormat = Constants.LOCAL_FORMAT.code(0,
                    Constants.PREFIX_ALMOST_EQUAL_TO + GenericUtils.currencySymbol(exchangeRate.fiat.currencyCode));
            final Object[] prefixSpans = new Object[] { MonetarySpannable.SMALLER_SPAN,
                    new ForegroundColorSpan(context.getColor(R.color.fg_insignificant_darkdefault)) };
            localBalanceStr = new MonetarySpannable(localFormat, localBalance).applyMarkup(prefixSpans,
                    MonetarySpannable.STANDARD_INSIGNIFICANT_SPANS);
            if (!Constants.NETWORK_PARAMETERS.getId().equals(NetworkParameters.ID_MAINNET))
                localBalanceStr.setSpan(STRIKE_THRU_SPAN, 0, localBalanceStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            return (Bundle) getAppWidgetOptions.invoke(appWidgetManager, appWidgetId);
        } catch (final Exception x) {
            return null;
        }
    }
}
