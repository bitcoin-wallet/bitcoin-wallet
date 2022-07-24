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

package de.schildbach.wallet.exchangerate;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okio.BufferedSource;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Andreas Schildbach
 */
public final class CoinGecko {
    private static final HttpUrl URL = HttpUrl.parse("https://api.coingecko.com/api/v3/coins/groestlcoin?localization=false&community_data=false&developer_data=false&sparkline=false");
    private static final MediaType MEDIA_TYPE = MediaType.get("application/json");
    private static final String SOURCE = "CoinGecko.com";

    private static final Logger log = LoggerFactory.getLogger(CoinGecko.class);

    private final Moshi moshi;

    public CoinGecko(final Moshi moshi) {
        this.moshi = moshi;
    }

    public MediaType mediaType() {
        return MEDIA_TYPE;
    }

    public HttpUrl url() {
        return URL;
    }

    public List<ExchangeRateEntry> parse(final BufferedSource jsonSource) throws IOException {
        final JsonAdapter<Map> jsonAdapter = moshi.adapter(Map.class);
        final Map jsonResponse = jsonAdapter.fromJson(jsonSource);
        Map<String, Double> currentPrice = (Map<String, Double>)((Map<String, Object>)jsonResponse.get("market_data")).get("current_price");
        final List<ExchangeRateEntry> result = new ArrayList<>(currentPrice.size());

        for (Map.Entry<String, Double> entry : currentPrice.entrySet()) {
            final String symbol = entry.getKey().toUpperCase(Locale.US);
            final Double exchangeRate = entry.getValue();
            try {
                final Fiat rate = Fiat.parseFiatInexact(symbol, exchangeRate.toString());
                if (rate.signum() > 0)
                    result.add(new ExchangeRateEntry(SOURCE, new ExchangeRate(rate)));
            } catch (final ArithmeticException x) {
                log.warn("problem parsing {} exchange rate from {}: {}", symbol, URL, x.getMessage());
            }
        }
        return result;
    }

    /*
    {
        "id": "groestlcoin",
        "market_data":{
            "current_price":{
                "aed":1.42,
                "ars":48.41,
                "aud":0.565913,
                "bch":0.00370334,
            }
       }
    }
    */

    // this is not currently used
    private static class MarketData {
        public Map<String, Double>  current_price;
    }

    private static class GroestlcoinResponse {
        public String id;
        public MarketData market_data;
    }
}
