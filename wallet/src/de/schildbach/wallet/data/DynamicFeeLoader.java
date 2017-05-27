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

package de.schildbach.wallet.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.http.HttpDate;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.send.FeeCategory;
import de.schildbach.wallet.util.Io;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;

/**
 * @author Andreas Schildbach
 */
public class DynamicFeeLoader extends AsyncTaskLoader<Map<FeeCategory, Coin>> {
    private final HttpUrl dynamicFeesUrl;
    private final String userAgent;
    private final AssetManager assets;

    private static final Logger log = LoggerFactory.getLogger(DynamicFeeLoader.class);

    public DynamicFeeLoader(final Context context) {
        super(context);
        final PackageInfo packageInfo = WalletApplication.packageInfoFromContext(context);
        final int versionNameSplit = packageInfo.versionName.indexOf('-');
        this.dynamicFeesUrl = HttpUrl.parse(Constants.DYNAMIC_FEES_URL
                + (versionNameSplit >= 0 ? packageInfo.versionName.substring(versionNameSplit) : ""));
        this.userAgent = WalletApplication.httpUserAgent(packageInfo.versionName);
        this.assets = context.getAssets();
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

    @Override
    public Map<FeeCategory, Coin> loadInBackground() {
        try {
            final Map<FeeCategory, Coin> staticFees = parseFees(assets.open(Constants.Files.FEES_FILENAME));
            final File dynamicFeesFile = new File(getContext().getFilesDir(), Constants.Files.FEES_FILENAME);
            final File tempFile = new File(getContext().getCacheDir(), Constants.Files.FEES_FILENAME + ".temp");
            fetchDynamicFees(dynamicFeesUrl, tempFile, dynamicFeesFile, userAgent);
            if (!dynamicFeesFile.exists())
                return staticFees;

            // Check dynamic fees for sanity.
            final Map<FeeCategory, Coin> dynamicFees = parseFees(new FileInputStream(dynamicFeesFile));
            for (final FeeCategory category : FeeCategory.values()) {
                final Coin staticFee = staticFees.get(category);
                final Coin dynamicFee = dynamicFees.get(category);
                if (dynamicFee == null) {
                    dynamicFees.put(category, staticFee);
                    log.warn("Dynamic fee category missing, using static: category {}, {}/kB", category,
                            staticFee.toFriendlyString());
                    continue;
                }
                final Coin upperBound = staticFee.shiftLeft(2);
                if (dynamicFee.isGreaterThan(upperBound)) {
                    dynamicFees.put(category, upperBound);
                    log.warn("Down-adjusting dynamic fee: category {} from {}/kB to {}/kB", category,
                            dynamicFee.toFriendlyString(), upperBound.toFriendlyString());
                    continue;
                }
                final Coin lowerBound = staticFee.shiftRight(2);
                if (dynamicFee.isLessThan(lowerBound)) {
                    dynamicFees.put(category, lowerBound);
                    log.warn("Up-adjusting dynamic fee: category {} from {}/kB to {}/kB", category,
                            dynamicFee.toFriendlyString(), lowerBound.toFriendlyString());
                }
            }
            return dynamicFees;
        } catch (final IOException x) {
            // Should not happen
            throw new RuntimeException(x);
        }
    }

    private static Map<FeeCategory, Coin> parseFees(final InputStream is) throws IOException {
        final Map<FeeCategory, Coin> dynamicFees = new HashMap<FeeCategory, Coin>();
        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new InputStreamReader(is, Charsets.US_ASCII));
            while (true) {
                line = reader.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;

                final String[] fields = line.split("=");
                try {
                    final FeeCategory category = FeeCategory.valueOf(fields[0]);
                    final Coin rate = Coin.valueOf(Long.parseLong(fields[1]));
                    dynamicFees.put(category, rate);
                } catch (IllegalArgumentException x) {
                    log.warn("Cannot parse line, ignoring: '" + line + "'", x);
                }
            }
        } catch (final Exception x) {
            throw new RuntimeException("Error while parsing: '" + line + "'", x);
        } finally {
            if (reader != null)
                reader.close();
            is.close();
        }
        return dynamicFees;
    }

    private static void fetchDynamicFees(final HttpUrl url, final File tempFile, final File targetFile,
            final String userAgent) {
        final Stopwatch watch = Stopwatch.createStarted();

        final Request.Builder request = new Request.Builder();
        request.url(url);
        request.header("User-Agent", userAgent);
        if (targetFile.exists())
            request.header("If-Modified-Since", HttpDate.format(new Date(targetFile.lastModified())));

        final OkHttpClient httpClient = Constants.HTTP_CLIENT.clone();
        httpClient.setConnectTimeout(5, TimeUnit.SECONDS);
        httpClient.setWriteTimeout(5, TimeUnit.SECONDS);
        httpClient.setReadTimeout(5, TimeUnit.SECONDS);
        final Call call = httpClient.newCall(request.build());
        try {
            final Response response = call.execute();
            final int status = response.code();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                log.info("Dynamic fees not modified at {}, took {}", url, watch);
            } else if (status == HttpURLConnection.HTTP_OK) {
                final ResponseBody body = response.body();
                final FileOutputStream os = new FileOutputStream(tempFile);
                Io.copy(body.byteStream(), os);
                os.close();
                final Date lastModified = response.headers().getDate("Last-Modified");
                if (lastModified != null)
                    tempFile.setLastModified(lastModified.getTime());
                body.close();
                if (!tempFile.renameTo(targetFile))
                    throw new IllegalStateException("Cannot rename " + tempFile + " to " + targetFile);
                watch.stop();
                log.info("Dynamic fees fetched from {}, took {}", url, watch);
            } else {
                log.warn("HTTP status {} when fetching dynamic fees from {}", response.code(), url);
            }
        } catch (final Exception x) {
            log.warn("Problem when fetching dynamic fees rates from " + url, x);
        }
    }
}
