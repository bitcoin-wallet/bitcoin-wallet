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

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.squareup.moshi.Moshi;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.Logging;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.exchangerate.CoinGecko;
import de.schildbach.wallet.exchangerate.ExchangeRateDao;
import de.schildbach.wallet.exchangerate.ExchangeRateEntry;
import de.schildbach.wallet.exchangerate.ExchangeRatesDatabase;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionSpec;
import okhttp3.Headers;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider {
    public static final String KEY_CURRENCY_CODE = "currency_code";
    private static final String KEY_RATE_COIN = "rate_coin";
    private static final String KEY_RATE_FIAT = "rate_fiat";
    private static final String KEY_SOURCE = "source";

    public static final String QUERY_PARAM_Q = "q";

    private WalletApplication application;
    private Configuration config;
    private String userAgent;
    private ExchangeRatesDatabase db;
    private ExchangeRateDao dao;
    private final AtomicLong lastUpdated = new AtomicLong(0);

    private static final long UPDATE_FREQ_MS = 10 * DateUtils.MINUTE_IN_MILLIS;

    private static final Logger log = LoggerFactory.getLogger(ExchangeRatesProvider.class);

    @Override
    public boolean onCreate() {
        if (!Constants.ENABLE_EXCHANGE_RATES)
            return false;

        WalletApplication.initStrictMode();

        final Stopwatch watch = Stopwatch.createStarted();

        final Context context = getContext();
        Logging.init(context.getFilesDir());
        this.application = (WalletApplication) context.getApplicationContext();
        this.config = application.getConfiguration();
        this.userAgent = WalletApplication.httpUserAgent(application.packageInfo().versionName);

        this.db = ExchangeRatesDatabase.getDatabase(application);
        this.dao = db.exchangeRateDao();

        watch.stop();
        log.info("{}.onCreate() took {}", getClass().getSimpleName(), watch);
        return true;
    }

    public static Uri contentUri(final String packageName) {
        final Uri.Builder uri = Uri.parse("content://" + packageName + '.' + "exchange_rates").buildUpon();
        return uri.build();
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        maybeRequestExchangeRates();

        final MatrixCursor cursor = new MatrixCursor(
                new String[] { BaseColumns._ID, KEY_CURRENCY_CODE, KEY_RATE_COIN, KEY_RATE_FIAT, KEY_SOURCE });

        if (selection == null) {
            for (final ExchangeRateEntry entry : dao.findAll())
                cursor.newRow().add((int) entry.getId()).add(entry.getCurrencyCode()).add(entry.getRateCoin())
                        .add(entry.getRateFiat()).add(entry.getSource());
        } else if (selection.equals(QUERY_PARAM_Q)) {
            final String selectionArg = selectionArgs[0].toLowerCase(Locale.US);
            for (final ExchangeRateEntry entry : dao.findByConstraint(selectionArg))
                cursor.newRow().add((int) entry.getId()).add(entry.getCurrencyCode()).add(entry.getRateCoin())
                        .add(entry.getRateFiat()).add(entry.getSource());
        } else if (selection.equals(KEY_CURRENCY_CODE)) {
            final String selectionArg = selectionArgs[0];
            final ExchangeRateEntry entry = dao.findByCurrencyCode(selectionArg);
            if (entry != null)
                cursor.newRow().add((int) entry.getId()).add(entry.getCurrencyCode()).add(entry.getRateCoin())
                        .add(entry.getRateFiat()).add(entry.getSource());
        }

        return cursor;
    }

    public static ExchangeRate getExchangeRate(final Cursor cursor) {
        final String currencyCode = cursor
                .getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE));
        final Coin rateCoin = Coin
                .valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_COIN)));
        final Fiat rateFiat = Fiat.valueOf(currencyCode,
                cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_FIAT)));
        final String source = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_SOURCE));

        return new ExchangeRate(new org.bitcoinj.utils.ExchangeRate(rateCoin, rateFiat), source);
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    private void maybeRequestExchangeRates() {
        final Stopwatch watch = Stopwatch.createStarted();
        final long now = System.currentTimeMillis();

        final long lastUpdated = this.lastUpdated.get();
        if (lastUpdated != 0 && now - lastUpdated <= UPDATE_FREQ_MS)
            return;

        final CoinGecko coinGecko = new CoinGecko(new Moshi.Builder().build());
        final Request.Builder request = new Request.Builder();
        request.url(coinGecko.url());
        final Headers.Builder headers = new Headers.Builder();
        headers.add("User-Agent", userAgent);
        headers.add("Accept", coinGecko.mediaType().toString());
        request.headers(headers.build());

        final Builder httpClientBuilder = Constants.HTTP_CLIENT.newBuilder();
        httpClientBuilder.connectionSpecs(Collections.singletonList(ConnectionSpec.RESTRICTED_TLS));
        final Call call = httpClientBuilder.build().newCall(request.build());
        call.enqueue(new Callback() {
            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        for (final ExchangeRateEntry exchangeRate : coinGecko.parse(response.body().source()))
                            dao.insertOrUpdate(exchangeRate);
                        ExchangeRatesProvider.this.lastUpdated.set(now);
                        getContext().getContentResolver().notifyChange(contentUri(application.getPackageName()), null);
                        watch.stop();
                        log.info("fetched exchange rates from {}, took {}", coinGecko.url(), watch);
                    } else {
                        log.warn("http status {} {} when fetching exchange rates from {}", response.code(),
                                response.message(), coinGecko.url());
                    }
                } catch (final Exception x) {
                    log.warn("problem fetching exchange rates from " + coinGecko.url(), x);
                }
            }

            @Override
            public void onFailure(final Call call, final IOException x) {
                log.warn("problem fetching exchange rates from " + coinGecko.url(), x);
            }
        });
    }
}
