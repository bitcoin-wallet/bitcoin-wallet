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

package de.schildbach.wallet.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Handler;
import android.os.Looper;

import com.google.bitcoin.core.Transaction;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.PaymentIntent;
import de.schildbach.wallet.offline.DirectPaymentTask;
import de.schildbach.wallet.util.PaymentProtocol;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public abstract class RequestPaymentRequestTask
{
	private final Handler backgroundHandler;
	private final Handler callbackHandler;
	private final ResultCallback resultCallback;

	private static final Logger log = LoggerFactory.getLogger(DirectPaymentTask.class);

	public interface ResultCallback
	{
		void onPaymentIntent(PaymentIntent paymentIntent);

		void onFail(int messageResId, Object... messageArgs);
	}

	public RequestPaymentRequestTask(@Nonnull final Handler backgroundHandler, @Nonnull final ResultCallback resultCallback)
	{
		this.backgroundHandler = backgroundHandler;
		this.callbackHandler = new Handler(Looper.myLooper());
		this.resultCallback = resultCallback;
	}

	public final static class HttpRequestTask extends RequestPaymentRequestTask
	{
		@CheckForNull
		private final String userAgent;

		public HttpRequestTask(@Nonnull final Handler backgroundHandler, @Nonnull final ResultCallback resultCallback,
				@Nullable final String userAgent)
		{
			super(backgroundHandler, resultCallback);

			this.userAgent = userAgent;
		}

		@Override
		public void requestPaymentRequest(@Nonnull final String url)
		{
			super.backgroundHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					log.info("trying to request payment request from {}", url);

					HttpURLConnection connection = null;
					InputStream is = null;

					try
					{
						connection = (HttpURLConnection) new URL(url).openConnection();

						connection.setInstanceFollowRedirects(false);
						connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
						connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
						connection.setUseCaches(false);
						connection.setDoInput(true);
						connection.setDoOutput(false);

						connection.setRequestMethod("GET");
						connection.setRequestProperty("Accept", PaymentProtocol.MIMETYPE_PAYMENTREQUEST);
						if (userAgent != null)
							connection.addRequestProperty("User-Agent", userAgent);
						connection.connect();

						final int responseCode = connection.getResponseCode();
						if (responseCode == HttpURLConnection.HTTP_OK)
						{
							is = connection.getInputStream();

							new InputParser.StreamInputParser(connection.getContentType(), is)
							{
								@Override
								protected void handlePaymentIntent(@Nonnull PaymentIntent paymentIntent)
								{
									log.info("received {} via http", paymentIntent);

									onPaymentIntent(paymentIntent);
								}

								@Override
								protected void handleDirectTransaction(@Nonnull Transaction transaction)
								{
									throw new UnsupportedOperationException();

								}

								@Override
								protected void error(int messageResId, Object... messageArgs)
								{
									onFail(messageResId, messageArgs);
								}
							}.parse();
						}
						else
						{
							final String responseMessage = connection.getResponseMessage();

							log.info("got http error {}: {}", responseCode, responseMessage);

							onFail(R.string.error_http, responseCode, responseMessage);
						}
					}
					catch (final IOException x)
					{
						log.info("problem sending", x);

						onFail(R.string.error_io, x.getMessage());
					}
					finally
					{
						if (is != null)
						{
							try
							{
								is.close();
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
	}

	public abstract void requestPaymentRequest(@Nonnull String url);

	protected void onPaymentIntent(final PaymentIntent paymentIntent)
	{
		callbackHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				resultCallback.onPaymentIntent(paymentIntent);
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
}
