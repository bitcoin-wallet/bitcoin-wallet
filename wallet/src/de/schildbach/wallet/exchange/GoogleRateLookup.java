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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleRateLookup extends RateLookup {
    private static final String TAG = GoogleRateLookup.class.getName();

    public GoogleRateLookup()
    {
        super("http://spreadsheets.google.com/feeds/list/0Av2v4lMxiJ1AdE9laEZJdzhmMzdmcW90VWNfUTYtM2c/2/public/basic?alt=json");
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
            try
            {
                JSONObject head = new JSONObject(this.data);
                JSONArray resultArray;

                head = head.getJSONObject("feed");
                resultArray = head.getJSONArray("entry");
                // Format: eg. _cpzh4: 3.673
                Pattern p = Pattern.compile("_cpzh4: ([\\d\\.]+)");
                for(int i = 0; i < resultArray.length(); ++i) {
                    String currencyCd = resultArray.getJSONObject(i).getJSONObject("title").getString("$t");
                    String rateStr = resultArray.getJSONObject(i).getJSONObject("content").getString("$t");
                    Matcher m = p.matcher(rateStr);
                    if(m.matches())
                    {
                        // Just get the good part
                        rateStr = m.group(1);
                        Log.d(TAG, "Currency: " + currencyCd);
                        Log.d(TAG, "Rate: " + rateStr);
                        Log.d(TAG, "USD Rate: " + decUsdRate.toString());
                        BigDecimal rate = new BigDecimal(rateStr);
                        Log.d(TAG, "Converted Rate: " + rate.toString());
                        rate = decUsdRate.multiply(rate);
                        Log.d(TAG, "Final Rate: " + rate.toString());
                        if (rate.signum() > 0)
                        {
                            rates.put(currencyCd, new ExchangeRatesProvider.ExchangeRate(currencyCd,
                                    GenericUtils.toNanoCoinsRounded(rate.toString(), 0), this.url.getHost()));
                        }
                    }
                }
            } catch(JSONException e) {
                Log.i(TAG, "Bad JSON response from Google Spreadsheets!: " + data);
                return null;
            }
            return rates;
        }
        return null;
    }
}