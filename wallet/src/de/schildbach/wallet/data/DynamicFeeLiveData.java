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

import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import androidx.lifecycle.LiveData;
import com.google.common.base.Stopwatch;
import com.google.common.io.ByteStreams;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.send.FeeCategory;
import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.bitcoinj.base.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Andreas Schildbach
 */
public class DynamicFeeLiveData extends LiveData<Map<FeeCategory, Coin>> {
    private final HttpUrl dynamicFeesUrl;
    private final String userAgent;
    private final AssetManager assets;
    private final File dynamicFeesFile;
    private final File tempFile;

    private static final Logger log = LoggerFactory.getLogger(DynamicFeeLiveData.class);

    public DynamicFeeLiveData(final WalletApplication application) {
        final PackageInfo packageInfo = application.packageInfo();
        final int versionNameSplit = packageInfo.versionName.indexOf('-');
        this.dynamicFeesUrl = HttpUrl.parse(Constants.DYNAMIC_FEES_URL
                + (versionNameSplit >= 0 ? packageInfo.versionName.substring(versionNameSplit) : ""));
        this.userAgent = WalletApplication.httpUserAgent(packageInfo.versionName);
        this.assets = application.getAssets();
        this.dynamicFeesFile = new File(application.getFilesDir(), Constants.Files.FEES_ASSET);
        this.tempFile = new File(application.getCacheDir(), Constants.Files.FEES_ASSET + ".temp");
    }

    @Override
    protected void onActive() {
        AsyncTask.execute(() -> {
            final Map<FeeCategory, Coin> dynamicFees = loadInBackground();
            postValue(dynamicFees);
        });
    }

    private Map<FeeCategory, Coin> loadInBackground() {
        try {
            final Map<FeeCategory, Coin> staticFees = parseFees(assets.open(Constants.Files.FEES_ASSET));
            fetchDynamicFees(dynamicFeesUrl, tempFile, dynamicFeesFile, userAgent);
            if (!dynamicFeesFile.exists())
                return staticFees;

            // Check dynamic fees for sanity, based on the hardcoded fees.
            // The bounds are as follows (h is the respective hardcoded fee):
            // ECONOMIC: h/16 to h*16
            // NORMAL: h/16 to h*16
            // PRIORITY: h/16 to h*16
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
                final Coin upperBound = staticFee.shiftLeft(4);
                if (dynamicFee.isGreaterThan(upperBound)) {
                    dynamicFees.put(category, upperBound);
                    log.warn("Down-adjusting dynamic fee: category {} from {}/kB to {}/kB", category,
                            dynamicFee.toFriendlyString(), upperBound.toFriendlyString());
                    continue;
                }
                final Coin lowerBound = staticFee.shiftRight(4);
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
        final Map<FeeCategory, Coin> dynamicFees = new HashMap<>();
        String line = null;
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII))) {
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
            is.close();
        }
        return dynamicFees;
    }

    private static void fetchDynamicFees(final HttpUrl url, final File tempFile, final File targetFile,
            final String userAgent) {
        final Stopwatch watch = Stopwatch.createStarted();

        final Request.Builder request = new Request.Builder();
        request.url(url);
        final Headers.Builder headers = new Headers.Builder();
        headers.add("User-Agent", userAgent);
        if (targetFile.exists())
            headers.add("If-Modified-Since", new Date(targetFile.lastModified()));
        request.headers(headers.build());

        final OkHttpClient.Builder httpClientBuilder = Constants.HTTP_CLIENT.newBuilder();
        httpClientBuilder.connectionSpecs(Collections.singletonList(ConnectionSpec.RESTRICTED_TLS));
        httpClientBuilder.connectTimeout(5, TimeUnit.SECONDS);
        httpClientBuilder.writeTimeout(5, TimeUnit.SECONDS);
        httpClientBuilder.readTimeout(5, TimeUnit.SECONDS);
        final OkHttpClient httpClient = httpClientBuilder.build();
        final Call call = httpClient.newCall(request.build());
        try {
            final Response response = call.execute();
            final int status = response.code();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                log.info("Dynamic fees not modified at {}, took {}", url, watch);
            } else if (status == HttpURLConnection.HTTP_OK) {
                final ResponseBody body = response.body();
                final FileOutputStream os = new FileOutputStream(tempFile);
                ByteStreams.copy(body.byteStream(), os);
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
                log.warn("HTTP status {} {} when fetching dynamic fees from {}", response.code(), response.message(),
                        url);
            }
        } catch (final Exception x) {
            log.warn("Problem when fetching dynamic fees rates from " + url, x);
        }
    }
}
