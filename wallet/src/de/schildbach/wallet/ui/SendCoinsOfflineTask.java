/*
 * Copyright 2013-2014 the original author or authors.
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

import javax.annotation.Nonnull;

import android.os.Handler;
import android.os.Looper;

import android.util.Log;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.SendRequest;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public abstract class SendCoinsOfflineTask
{
	private final Wallet wallet;
	private final Handler backgroundHandler;
	private final Handler callbackHandler;

	public SendCoinsOfflineTask(@Nonnull final Wallet wallet, @Nonnull final Handler backgroundHandler)
	{
		this.wallet = wallet;
		this.backgroundHandler = backgroundHandler;
		this.callbackHandler = new Handler(Looper.myLooper());
	}

	public final void sendCoinsOffline(@Nonnull final SendRequest sendRequest)
	{
		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
                final Transaction transaction; // can take long
                try {
                    transaction = wallet.sendCoinsOffline(sendRequest);
                } catch (InsufficientMoneyException e) {
                    throw new RuntimeException(e);
                }

                callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						if (transaction != null)
							onSuccess(transaction);
						else
							onFailure();
					}
				});
			}
		});
	}

    public final void commitRequest(@Nonnull final SendRequest sendRequest)
    {
        backgroundHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                final Transaction transaction; // can take long
                wallet.commitTx(sendRequest.tx);

                callbackHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                            onSuccess(sendRequest.tx);
                    }
                });
            }
        });
    }

	protected abstract void onSuccess(@Nonnull Transaction transaction);

	protected abstract void onFailure();
}
