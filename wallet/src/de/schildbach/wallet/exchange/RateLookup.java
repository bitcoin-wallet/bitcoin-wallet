package de.schildbach.wallet.exchange;

import android.util.Log;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.util.Io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Exchange rate lookup abstract class
 */
public abstract class RateLookup {
    private static final String TAG = RateLookup.class.getName();
    protected String urlString;
    protected URL url;
    protected String data;

    RateLookup(String urlString) {
        this.urlString = urlString;
    }
    public abstract Map<String, ExchangeRatesProvider.ExchangeRate> getRates(ExchangeRatesProvider.ExchangeRate usdRate);
    protected boolean getData()
    {
        // Make sure our URL is set
        if(urlString == null)
            return false;
        // Attempt to parse URL
        try {
            url = new URL(this.urlString);
        } catch(MalformedURLException e) {
            Log.i(TAG, "Failed to parse URL");
            return false;
        }
        HttpURLConnection connection = null;
        Reader reader = null;
        // Attempt HTTP connection
        try
        {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
            connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
            connection.connect();

            final int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                // Get final response string and return
                reader = new InputStreamReader(new BufferedInputStream(connection.getInputStream(), 1024), Constants.UTF_8);
                final StringBuilder content = new StringBuilder();
                Io.copy(reader, content);
                this.data = content.toString();
                return true;
            }
            else
            {
                // HTTP error
                return false;
            }
        }
        catch (IOException e)
        {
            Log.i(TAG, "Failed to connect to URL " + this.urlString);
            return false;
        }
        catch (final Exception x)
        {
            Log.w(TAG, "Problem fetching exchange rates", x);
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (final IOException x)
                {
                    // swallow
                }
            }

            if (connection != null)
                connection.disconnect();
        }
        // Fallthrough return, just in case.
        return false;
    }
}