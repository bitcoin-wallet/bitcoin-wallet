package de.schildbach.wallet.exchange;

import android.util.Log;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.util.GenericUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

public class YahooRateLookup extends RateLookup {
    private static final String TAG = YahooRateLookup.class.getName();

    public YahooRateLookup()
    {
        super("http://query.yahooapis.com/v1/public/yql?q=select%20id%2C%20Rate%20from%20yahoo.finance.xchange" +
                "%20where%20pair%20in%20(%22USDEUR%22%2C%20%22USDJPY%22%2C%20%22USDBGN%22%2C%20%22USDCZK%22%2C%20" +
                "%22USDDKK%22%2C%20%22USDGBP%22%2C%20%22USDHUF%22%2C%20%22USDLTL%22%2C%20%22USDLVL%22%2C%20%22USDPLN" +
                "%22%2C%20%22USDRON%22%2C%20%22USDSEK%22%2C%20%22USDCHF%22%2C%20%22USDNOK%22%2C%20%22USDHRK%22%2C%20" +
                "%22USDRUB%22%2C%20%22USDTRY%22%2C%20%22USDAUD%22%2C%20%22USDBRL%22%2C%20%22USDCAD%22%2C%20%22USDCNY" +
                "%22%2C%20%22USDHKD%22%2C%20%22USDIDR%22%2C%20%22USDILS%22%2C%20%22USDINR%22%2C%20%22USDKRW%22%2C%20" +
                "%22USDMXN%22%2C%20%22USDMYR%22%2C%20%22USDNZD%22%2C%20%22USDPHP%22%2C%20%22USDSGD%22%2C%20%22USDTHB" +
                "%22%2C%20%22USDZAR%22%2C%20%22USDISK%22)&format=json&env=store%3A%2F%2Fdatatables.org" +
                "%2Falltableswithkeys&callback=");
    }

    public Map<String, ExchangeRatesProvider.ExchangeRate> getRates(ExchangeRatesProvider.ExchangeRate usdRate) {
        if(usdRate == null)
            return null;
        final BigDecimal decUsdRate = GenericUtils.fromNanoCoins(usdRate.rate, 0);
        if(getData())
        {
            // We got data from the HTTP connection
            final Map<String, ExchangeRatesProvider.ExchangeRate> rates =
                    new TreeMap<String, ExchangeRatesProvider.ExchangeRate>();
            JSONObject head;
            JSONArray resultArray;
            try {
                head = new JSONObject(this.data);
                head = head.getJSONObject("query");
                head = head.getJSONObject("results");
                resultArray = head.getJSONArray("rate");

                for(int i = 0; i < resultArray.length(); ++i) {
                    final JSONObject rateObj = resultArray.getJSONObject(i);
                    String currencyCd = rateObj.getString("id").substring(3);
                    Log.d(TAG, "Currency: " + currencyCd);
                    String rateStr = rateObj.getString("Rate");
                    Log.d(TAG, "Rate: " + rateStr);
                    Log.d(TAG, "USD Rate: " + decUsdRate.toString());
                    BigDecimal rate = new BigDecimal(rateStr);
                    Log.d(TAG, "Converted Rate: " + rate.toString());
                    rate = decUsdRate.multiply(rate);
                    Log.d(TAG, "Final Rate: " + rate.toString());
                    if (rate.signum() > 0)
                    {
                        rates.put(currencyCd, new ExchangeRatesProvider.ExchangeRate(currencyCd,
                                GenericUtils.toNanoCoinsRounded(rate.toString(), 0), url.getHost()));
                    }
                }
            } catch(JSONException e) {
                Log.i(TAG, "Bad JSON response from Yahoo!: " + data);
                return null;
            }
            Log.i(TAG, "Fetched exchange rates from " + url);
            return rates;
        }
        return null;
    }
}