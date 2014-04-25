/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.annotation.Nonnull;

import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.PBEParametersGenerator;
import org.spongycastle.crypto.engines.AESFastEngine;
import org.spongycastle.crypto.generators.OpenSSLPBEParametersGenerator;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.ParametersWithIV;

import com.google.common.io.BaseEncoding;

/**
 * This class encrypts and decrypts a string in a manner that is compatible with OpenSSL.
 * 
 * If you encrypt a string with this class you can decrypt it with the OpenSSL command: openssl enc -d -aes-256-cbc -a
 * -in cipher.txt -out plain.txt -pass pass:aTestPassword
 * 
 * where: cipher.txt = file containing the cipher text plain.txt - where you want the plaintext to be saved
 * 
 * substitute your password for "aTestPassword" or remove the "-pass" parameter to be prompted.
 * 
 * @author jim
 * @author Andreas Schildbach
 */
public class Crypto
{
	private static final BaseEncoding BASE64 = BaseEncoding.base64().omitPadding();
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	/**
	 * number of times the password & salt are hashed during key creation.
	 */
	private static final int NUMBER_OF_ITERATIONS = 1024;

	/**
	 * Key length.
	 */
	private static final int KEY_LENGTH = 256;

	/**
	 * Initialization vector length.
	 */
	private static final int IV_LENGTH = 128;

	/**
	 * The length of the salt.
	 */
	private static final int SALT_LENGTH = 8;

	/**
	 * OpenSSL salted prefix text.
	 */
	private static final String OPENSSL_SALTED_TEXT = "Salted__";

	/**
	 * OpenSSL salted prefix bytes - also used as magic number for encrypted key file.
	 */
	private static final byte[] OPENSSL_SALTED_BYTES = OPENSSL_SALTED_TEXT.getBytes(UTF_8);

	/**
	 * Magic text that appears at the beginning of every OpenSSL encrypted file. Used in identifying encrypted key
	 * files.
	 */
	private static final String OPENSSL_MAGIC_TEXT = BASE64.encode(Crypto.OPENSSL_SALTED_BYTES).substring(0,
			Crypto.NUMBER_OF_CHARACTERS_TO_MATCH_IN_OPENSSL_MAGIC_TEXT);

	private static final int NUMBER_OF_CHARACTERS_TO_MATCH_IN_OPENSSL_MAGIC_TEXT = 10;

	private static final SecureRandom secureRandom = new SecureRandom();

	/**
	 * Get password and generate key and iv.
	 * 
	 * @param password
	 *            The password to use in key generation
	 * @param salt
	 *            The salt to use in key generation
	 * @return The CipherParameters containing the created key
	 */
	private static CipherParameters getAESPasswordKey(final char[] password, final byte[] salt)
	{
		final PBEParametersGenerator generator = new OpenSSLPBEParametersGenerator();
		generator.init(PBEParametersGenerator.PKCS5PasswordToBytes(password), salt, NUMBER_OF_ITERATIONS);

		final ParametersWithIV key = (ParametersWithIV) generator.generateDerivedParameters(KEY_LENGTH, IV_LENGTH);

		return key;
	}

	/**
	 * Password based encryption using AES - CBC 256 bits.
	 * 
	 * @param plainText
	 *            The text to encrypt
	 * @param password
	 *            The password to use for encryption
	 * @return The encrypted string
	 * @throws IOException
	 */
	public static String encrypt(@Nonnull final String plainText, @Nonnull final char[] password) throws IOException
	{
		final byte[] plainTextAsBytes = plainText.getBytes(UTF_8);

		final byte[] encryptedBytes = encrypt(plainTextAsBytes, password);

		// OpenSSL prefixes the salt bytes + encryptedBytes with Salted___ and then base64 encodes it
		final byte[] encryptedBytesPlusSaltedText = concat(OPENSSL_SALTED_BYTES, encryptedBytes);

		return BASE64.encode(encryptedBytesPlusSaltedText);
	}

	/**
	 * Password based encryption using AES - CBC 256 bits.
	 * 
	 * @param plainBytes
	 *            The bytes to encrypt
	 * @param password
	 *            The password to use for encryption
	 * @return SALT_LENGTH bytes of salt followed by the encrypted bytes.
	 * @throws IOException
	 */
	private static byte[] encrypt(final byte[] plainTextAsBytes, final char[] password) throws IOException
	{
		try
		{
			// Generate salt - each encryption call has a different salt.
			final byte[] salt = new byte[SALT_LENGTH];
			secureRandom.nextBytes(salt);

			final ParametersWithIV key = (ParametersWithIV) getAESPasswordKey(password, salt);

			// The following code uses an AES cipher to encrypt the message.
			final BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESFastEngine()));
			cipher.init(true, key);
			final byte[] encryptedBytes = new byte[cipher.getOutputSize(plainTextAsBytes.length)];
			final int processLen = cipher.processBytes(plainTextAsBytes, 0, plainTextAsBytes.length, encryptedBytes, 0);
			final int doFinalLen = cipher.doFinal(encryptedBytes, processLen);

			// The result bytes are the SALT_LENGTH bytes followed by the encrypted bytes.
			return concat(salt, Arrays.copyOf(encryptedBytes, processLen + doFinalLen));
		}
		catch (final InvalidCipherTextException x)
		{
			throw new IOException("Could not encrypt bytes", x);
		}
		catch (final DataLengthException x)
		{
			throw new IOException("Could not encrypt bytes", x);
		}
	}

	/**
	 * Decrypt text previously encrypted with this class.
	 * 
	 * @param textToDecode
	 *            The code to decrypt
	 * @param password
	 *            password to use for decryption
	 * @return The decrypted text
	 * @throws IOException
	 */
	public static String decrypt(@Nonnull final String textToDecode, @Nonnull final char[] password) throws IOException
	{
		final byte[] decodeTextAsBytes = BASE64.decode(textToDecode);

		if (decodeTextAsBytes.length < OPENSSL_SALTED_BYTES.length)
			throw new IOException("out of salt");

		final byte[] cipherBytes = new byte[decodeTextAsBytes.length - OPENSSL_SALTED_BYTES.length];
		System.arraycopy(decodeTextAsBytes, OPENSSL_SALTED_BYTES.length, cipherBytes, 0, decodeTextAsBytes.length - OPENSSL_SALTED_BYTES.length);

		final byte[] decryptedBytes = decrypt(cipherBytes, password);

		return new String(decryptedBytes, UTF_8).trim();
	}

	/**
	 * Decrypt bytes previously encrypted with this class.
	 * 
	 * @param bytesToDecode
	 *            The bytes to decrypt
	 * @param passwordbThe
	 *            password to use for decryption
	 * @return The decrypted bytes
	 * @throws IOException
	 */
	private static byte[] decrypt(final byte[] bytesToDecode, final char[] password) throws IOException
	{
		try
		{
			// separate the salt and bytes to decrypt
			final byte[] salt = new byte[SALT_LENGTH];

			System.arraycopy(bytesToDecode, 0, salt, 0, SALT_LENGTH);

			final byte[] cipherBytes = new byte[bytesToDecode.length - SALT_LENGTH];
			System.arraycopy(bytesToDecode, SALT_LENGTH, cipherBytes, 0, bytesToDecode.length - SALT_LENGTH);

			final ParametersWithIV key = (ParametersWithIV) getAESPasswordKey(password, salt);

			// decrypt the message
			final BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESFastEngine()));
			cipher.init(false, key);

			final byte[] decryptedBytes = new byte[cipher.getOutputSize(cipherBytes.length)];
			final int processLen = cipher.processBytes(cipherBytes, 0, cipherBytes.length, decryptedBytes, 0);
			final int doFinalLen = cipher.doFinal(decryptedBytes, processLen);

			return Arrays.copyOf(decryptedBytes, processLen + doFinalLen);
		}
		catch (final InvalidCipherTextException x)
		{
			throw new IOException("Could not decrypt input string", x);
		}
		catch (final DataLengthException x)
		{
			throw new IOException("Could not decrypt input string", x);
		}
	}

	/**
	 * Concatenate two byte arrays.
	 */
	private static byte[] concat(final byte[] arrayA, final byte[] arrayB)
	{
		final byte[] result = new byte[arrayA.length + arrayB.length];
		System.arraycopy(arrayA, 0, result, 0, arrayA.length);
		System.arraycopy(arrayB, 0, result, arrayA.length, arrayB.length);

		return result;
	}

	public final static FileFilter OPENSSL_FILE_FILTER = new FileFilter()
	{
		private final char[] buf = new char[OPENSSL_MAGIC_TEXT.length()];

		@Override
		public boolean accept(final File file)
		{
			Reader in = null;
			try
			{
				in = new InputStreamReader(new FileInputStream(file), UTF_8);
				if (in.read(buf) == -1)
					return false;
				final String str = new String(buf);
				if (!str.toString().equals(OPENSSL_MAGIC_TEXT))
					return false;
				return true;
			}
			catch (final IOException x)
			{
				return false;
			}
			finally
			{
				if (in != null)
				{
					try
					{
						in.close();
					}
					catch (final IOException x2)
					{
					}
				}
			}
		}
	};
}
