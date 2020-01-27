/*
 * Copyright the original author or authors.
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

import com.google.common.io.BaseEncoding;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.engines.AESFastEngine;
import org.bouncycastle.crypto.generators.OpenSSLPBEParametersGenerator;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * This class encrypts and decrypts a string in a manner that is compatible with OpenSSL.
 * 
 * If you encrypt a string with this class you can decrypt it with the OpenSSL command: openssl enc -d
 * -aes-256-cbc -a -in cipher.txt -out plain.txt -pass pass:aTestPassword
 * 
 * where: cipher.txt = file containing the cipher text plain.txt - where you want the plaintext to be saved
 * 
 * substitute your password for "aTestPassword" or remove the "-pass" parameter to be prompted.
 * 
 * @author jim
 * @author Andreas Schildbach
 */
public class Crypto {
    private static final BaseEncoding BASE64_ENCRYPT = BaseEncoding.base64().withSeparator("\n", 76);
    private static final BaseEncoding BASE64_DECRYPT = BaseEncoding.base64().withSeparator("\r\n", 76);

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
    private static final byte[] OPENSSL_SALTED_BYTES = OPENSSL_SALTED_TEXT.getBytes(StandardCharsets.UTF_8);

    /**
     * Magic text that appears at the beginning of every OpenSSL encrypted file. Used in identifying encrypted
     * key files.
     */
    private static final String OPENSSL_MAGIC_TEXT = BASE64_ENCRYPT.encode(Crypto.OPENSSL_SALTED_BYTES).substring(0,
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
    private static CipherParameters getAESPasswordKey(final char[] password, final byte[] salt) {
        final PBEParametersGenerator generator = new OpenSSLPBEParametersGenerator();
        generator.init(PBEParametersGenerator.PKCS5PasswordToBytes(password), salt, NUMBER_OF_ITERATIONS);
        return (ParametersWithIV) generator.generateDerivedParameters(KEY_LENGTH, IV_LENGTH);
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
    public static String encrypt(final String plainText, final char[] password) throws IOException {
        final byte[] plainTextAsBytes = plainText.getBytes(StandardCharsets.UTF_8);

        return encrypt(plainTextAsBytes, password);
    }

    /**
     * Password based encryption using AES - CBC 256 bits.
     * 
     * @param plainTextAsBytes
     *            The bytes to encrypt
     * @param password
     *            The password to use for encryption
     * @return The encrypted string
     * @throws IOException
     */
    public static String encrypt(final byte[] plainTextAsBytes, final char[] password) throws IOException {
        final byte[] encryptedBytes = encryptRaw(plainTextAsBytes, password);

        // OpenSSL prefixes the salt bytes + encryptedBytes with Salted___ and then base64 encodes it
        final byte[] encryptedBytesPlusSaltedText = concat(OPENSSL_SALTED_BYTES, encryptedBytes);

        return BASE64_ENCRYPT.encode(encryptedBytesPlusSaltedText);
    }

    /**
     * Password based encryption using AES - CBC 256 bits.
     * 
     * @param plainTextAsBytes
     *            The bytes to encrypt
     * @param password
     *            The password to use for encryption
     * @return SALT_LENGTH bytes of salt followed by the encrypted bytes.
     * @throws IOException
     */
    private static byte[] encryptRaw(final byte[] plainTextAsBytes, final char[] password) throws IOException {
        try {
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
        } catch (final InvalidCipherTextException | DataLengthException x) {
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
    public static String decrypt(final String textToDecode, final char[] password) throws IOException {
        final byte[] decryptedBytes = decryptBytes(textToDecode, password);

        return new String(decryptedBytes, StandardCharsets.UTF_8).trim();
    }

    /**
     * Decrypt bytes previously encrypted with this class.
     * 
     * @param textToDecode
     *            The code to decrypt
     * @param password
     *            password to use for decryption
     * @return The decrypted bytes
     * @throws IOException
     */
    public static byte[] decryptBytes(final String textToDecode, final char[] password) throws IOException {
        if (textToDecode.isEmpty())
            throw new IOException("empty ciphertext");

        final byte[] decodeTextAsBytes;
        try {
            decodeTextAsBytes = BASE64_DECRYPT.decode(textToDecode);
        } catch (final IllegalArgumentException x) {
            throw new IOException("invalid base64 encoding");
        }

        if (decodeTextAsBytes.length < OPENSSL_SALTED_BYTES.length)
            throw new IOException("out of salt");

        final byte[] cipherBytes = new byte[decodeTextAsBytes.length - OPENSSL_SALTED_BYTES.length];
        System.arraycopy(decodeTextAsBytes, OPENSSL_SALTED_BYTES.length, cipherBytes, 0,
                decodeTextAsBytes.length - OPENSSL_SALTED_BYTES.length);

        return decryptRaw(cipherBytes, password);
    }

    /**
     * Decrypt bytes previously encrypted with this class.
     * 
     * @param bytesToDecode
     *            The bytes to decrypt
     * @param password
     *            password to use for decryption
     * @return The decrypted bytes
     * @throws IOException
     */
    private static byte[] decryptRaw(final byte[] bytesToDecode, final char[] password) throws IOException {
        try {
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
        } catch (final InvalidCipherTextException | DataLengthException x) {
            throw new IOException("Could not decrypt bytes", x);
        }
    }

    /**
     * Concatenate two byte arrays.
     */
    private static byte[] concat(final byte[] arrayA, final byte[] arrayB) {
        final byte[] result = new byte[arrayA.length + arrayB.length];
        System.arraycopy(arrayA, 0, result, 0, arrayA.length);
        System.arraycopy(arrayB, 0, result, arrayA.length, arrayB.length);

        return result;
    }

    public final static FileFilter OPENSSL_FILE_FILTER = new FileFilter() {
        private final char[] buf = new char[OPENSSL_MAGIC_TEXT.length()];

        @Override
        public boolean accept(final File file) {
            try (final Reader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                if (in.read(buf) == -1)
                    return false;
                final String str = new String(buf);
                if (!str.equals(OPENSSL_MAGIC_TEXT))
                    return false;
                return true;
            } catch (final IOException x) {
                return false;
            }
        }
    };
}
