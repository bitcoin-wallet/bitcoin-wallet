/*
 * Copyright 2014 the original author or authors.
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

package de.schildbach.wallet.ui.send;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Handler;
import android.os.Looper;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.Io;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class RequestWalletBalanceTask
{
	private final Handler backgroundHandler;
	private final Handler callbackHandler;
	private final ResultCallback resultCallback;
	@CheckForNull
	private final String userAgent;

	private static final Logger log = LoggerFactory.getLogger(RequestWalletBalanceTask.class);

	private final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

	public interface ResultCallback
	{
		void onResult(Collection<Transaction> transactions);

		void onFail(int messageResId, Object... messageArgs);
	}

	public RequestWalletBalanceTask(@Nonnull final Handler backgroundHandler, @Nonnull final ResultCallback resultCallback,
			@Nullable final String userAgent)
	{
		this.backgroundHandler = backgroundHandler;
		this.callbackHandler = new Handler(Looper.myLooper());
		this.resultCallback = resultCallback;
		this.userAgent = userAgent;
	}

	public void requestWalletBalance(final Address... addresses)
	{
		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				final StringBuilder url = new StringBuilder(Constants.BITEASY_API_URL);
				url.append("unspent-outputs");
				url.append("?per_page=MAX");
				for (final Address address : addresses)
					url.append("&address[]=").append(address.toString());

				log.debug("trying to request wallet balance from {}", url);

				HttpURLConnection connection = null;
				Reader reader = null;

				try
				{
					connection = (HttpURLConnection) new URL(url.toString()).openConnection();

					connection.setInstanceFollowRedirects(false);
					connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
					connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
					connection.setUseCaches(false);
					connection.setDoInput(true);
					connection.setDoOutput(false);

					connection.setRequestMethod("GET");
					if (userAgent != null)
						connection.addRequestProperty("User-Agent", userAgent);
					connection.connect();

					final int responseCode = connection.getResponseCode();
					if (responseCode == HttpURLConnection.HTTP_OK)
					{
						reader = new InputStreamReader(new BufferedInputStream(connection.getInputStream(), 1024), Charsets.UTF_8);
						final StringBuilder content = new StringBuilder();
						Io.copy(reader, content);

						final JSONObject json = new JSONObject(content.toString());

						final int status = json.getInt("status");
						if (status != 200)
							throw new IOException("api status " + status + " when fetching unspent outputs");

						final JSONObject jsonData = json.getJSONObject("data");

						final JSONObject jsonPagination = jsonData.getJSONObject("pagination");

						if (!"false".equals(jsonPagination.getString("next_page")))
							throw new IllegalStateException("result set too big");

						final JSONArray jsonOutputs = jsonData.getJSONArray("outputs");

						final Map<Sha256Hash, Transaction> transactions = new HashMap<Sha256Hash, Transaction>(jsonOutputs.length());

						for (int i = 0; i < jsonOutputs.length(); i++)
						{
							final JSONObject jsonOutput = jsonOutputs.getJSONObject(i);

							if (jsonOutput.getInt("is_spent") != 0)
								throw new IllegalStateException("UXTO not spent");

							final Sha256Hash uxtoHash = new Sha256Hash(jsonOutput.getString("transaction_hash"));
							final int uxtoIndex = jsonOutput.getInt("transaction_index");
							final byte[] uxtoScriptBytes = HEX.decode(jsonOutput.getString("script_pub_key"));
							final BigInteger uxtoValue = new BigInteger(jsonOutput.getString("value"));

							Transaction tx = transactions.get(uxtoHash);
							if (tx == null)
							{
								tx = new FakeTransaction(Constants.NETWORK_PARAMETERS, uxtoHash);
								tx.getConfidence().setConfidenceType(ConfidenceType.BUILDING);
								transactions.put(uxtoHash, tx);
							}

							if (tx.getOutputs().size() > uxtoIndex)
								throw new IllegalStateException("cannot reach index " + uxtoIndex + ", tx already has " + tx.getOutputs().size()
										+ " outputs");

							// fill with dummies
							while (tx.getOutputs().size() < uxtoIndex)
								tx.addOutput(new TransactionOutput(Constants.NETWORK_PARAMETERS, tx, Utils.NEGATIVE_ONE, new byte[] {}));

							// add the real output
							final TransactionOutput output = new TransactionOutput(Constants.NETWORK_PARAMETERS, tx, uxtoValue, uxtoScriptBytes);
							tx.addOutput(output);
						}

						log.info("fetched unspent outputs from {}", url);

						onResult(transactions.values());
					}
					else
					{
						final String responseMessage = connection.getResponseMessage();

						log.info("got http error '{}: {}' from {}", responseCode, responseMessage, url);

						onFail(R.string.error_http, responseCode, responseMessage);
					}
				}
				catch (final JSONException x)
				{
					log.info("problem parsing json from " + url, x);

					onFail(R.string.error_parse, x.getMessage());
				}
				catch (final IOException x)
				{
					log.info("problem querying unspent outputs from " + url, x);

					onFail(R.string.error_io, x.getMessage());
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
			}
		});
	}

	protected void onResult(final Collection<Transaction> transactions)
	{
		callbackHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				resultCallback.onResult(transactions);
			}
		});
	}

	protected void onFail(final int messageResId, final Object... messageArgs)
	{
		callbackHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				resultCallback.onFail(messageResId, messageArgs);
			}
		});
	}

	private static class FakeTransaction extends Transaction
	{
		private final Sha256Hash hash;

		public FakeTransaction(final NetworkParameters params, final Sha256Hash hash)
		{
			super(params);
			this.hash = hash;
		}

		@Override
		public Sha256Hash getHash()
		{
			return hash;
		}
	}
}
