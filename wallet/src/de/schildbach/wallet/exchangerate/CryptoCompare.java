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

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

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
import java.lang.reflect.Type;

/**
 * @author Eloy Espinaco
 */
public final class CryptoCompare {
    private static final HttpUrl URL = HttpUrl.parse("https://min-api.cryptocompare.com/data/price?fsym=BTC&tsyms=usd,aed,ars,aud,bdt,bhd,bmd,brl,cad,chf,clp,cny,czk,dkk,eur,gbp,hkd,huf,idr,ils,inr,jpy,krw,kwd,lkr,mmk,mxn,myr,ngn,nok,nzd,php,pkr,pln,rub,sar,sek,sgd,thb,try,twd,uah,vef,vnd,zar,xdr&extraParams=bitcoinwallet");
    private static final MediaType MEDIA_TYPE = MediaType.get("application/json");
    private static final String SOURCE = "CryptoCompare.com";

    private static final Logger log = LoggerFactory.getLogger(CryptoCompare.class);

    private final Moshi moshi;

    public CryptoCompare(final Moshi moshi) {
        this.moshi = moshi;
    }

    public MediaType mediaType() {
        return MEDIA_TYPE;
    }

    public HttpUrl url() {
        return URL;
    }

    public List<ExchangeRateEntry> parse(final BufferedSource jsonSource) throws IOException {
        final Type type = Types.newParameterizedType(Map.class, String.class, String.class);
        final JsonAdapter<Map<String,String>> adapter = moshi.adapter(type);
        final Map<String,String> rates = adapter.fromJson(jsonSource);
        final List<ExchangeRateEntry> result = new ArrayList<>(rates.size());

        for (Map.Entry<String, String> entry : rates.entrySet()) {
            final String symbol = entry.getKey().toUpperCase(Locale.US);
            try {
              final Fiat rate = Fiat.parseFiatInexact(symbol, entry.getValue());
              if (rate.signum() > 0)
                result.add(new ExchangeRateEntry(SOURCE, new ExchangeRate(rate)));
            } catch (final ArithmeticException x) {
              log.warn("problem parsing {} exchange rate from {}: {}", symbol, URL, x.getMessage());
            }
        }
        return result;
    }
}
