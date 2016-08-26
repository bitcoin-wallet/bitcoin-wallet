/*
 * Copyright 2014-2015 the original author or authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import de.schildbach.wallet.Constants;

import android.os.Handler;
import android.os.Looper;

/**
 * @author Andreas Schildbach
 */
public abstract class DeriveKeyTask
{
	private final Handler backgroundHandler;
	private final Handler callbackHandler;

	private static final Logger log = LoggerFactory.getLogger(DeriveKeyTask.class);

	public DeriveKeyTask(final Handler backgroundHandler)
	{
		this.backgroundHandler = backgroundHandler;
		this.callbackHandler = new Handler(Looper.myLooper());
	}

	public final void deriveKey(final Wallet wallet, final String password)
	{
		checkState(wallet.isEncrypted());
		final KeyCrypter keyCrypter = checkNotNull(wallet.getKeyCrypter());

		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

				// Key derivation takes time.
				KeyParameter key = keyCrypter.deriveKey(password);

				// If the key isn't derived using the desired parameters, derive a new key.
				if (keyCrypter instanceof KeyCrypterScrypt)
				{
					final long scryptIterations = ((KeyCrypterScrypt) keyCrypter).getScryptParameters().getN();

					if (scryptIterations != Constants.SCRYPT_ITERATIONS_TARGET)
					{
						log.info("upgrading scrypt iterations from {} to {}; re-encrypting wallet", scryptIterations,
								Constants.SCRYPT_ITERATIONS_TARGET);

						final KeyCrypterScrypt newKeyCrypter = new KeyCrypterScrypt(Constants.SCRYPT_ITERATIONS_TARGET);
						final KeyParameter newKey = newKeyCrypter.deriveKey(password);

						// Re-encrypt wallet with new key.
						try
						{
							wallet.changeEncryptionKey(newKeyCrypter, key, newKey);
							key = newKey;
							log.info("scrypt upgrade succeeded");
						}
						catch (final KeyCrypterException x)
						{
							log.info("scrypt upgrade failed: {}", x.getMessage());
						}
					}
				}

				// Hand back the (possibly changed) encryption key.
				final KeyParameter keyToReturn = key;
				callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						onSuccess(keyToReturn);
					}
				});
			}
		});
	}

	protected abstract void onSuccess(KeyParameter encryptionKey);
}
