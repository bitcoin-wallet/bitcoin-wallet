
package de.schildbach.wallet.crypto;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.hardware.biometrics.BiometricPrompt;

import org.bitcoinj.crypto.AesKey;
import org.bitcoinj.protobuf.wallet.Protos;
import org.bitcoinj.protobuf.wallet.Protos.ScryptParameters;
import org.bitcoinj.protobuf.wallet.Protos.Wallet.EncryptionType;

import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import static de.schildbach.wallet.Constants.KEY_STORE_KEY_REF;
import static de.schildbach.wallet.Constants.KEY_STORE_PROVIDER;
import static de.schildbach.wallet.Constants.KEY_STORE_TRANSFORMATION;

import androidx.annotation.RequiresApi;

import com.google.protobuf.ByteString;

import de.schildbach.wallet.R;

public class HWKeyCrypter extends KeyCrypterScrypt {

    private static final Logger log = LoggerFactory.getLogger(HWKeyCrypter.class);
    private static final int TAG_LENGTH = 128; // bits
    private static final int KEY_LENGTH = 256; // bits
    private static final int KEY_AUTHENTICATION_DURATION = 5; // seconds
    private final Context context;
    private CompletableFuture<EncryptedData> encryptionFuture;
    private CompletableFuture<byte[]> decryptionFuture;
    private byte[] currentPlainBytes;
    private EncryptedData currentEncryptedData;
    private ScryptParameters scryptParameters;

    public HWKeyCrypter(Context context) {
        this.context = context;
        // ScryptParameters are only set because they are used to check if
        // the wallet is encrypted or not when reading the wallet from protobuf
        ScryptParameters.Builder scryptParametersBuilder = Protos.ScryptParameters.newBuilder().setSalt(
                ByteString.copyFrom(new byte[0]));
        this.scryptParameters = scryptParametersBuilder.build();
    }

    /**
     * Generates a key in the android key store.
     * The provided password and the returned AesKey are not used and only provided because
     * the class is extended by KeyCrypterScrypt which requires a password.
     *
     * @return AesKey
     * @throws KeyCrypterException
     */

    @Override
    public AesKey deriveKey(CharSequence unusedPassword) throws KeyCrypterException {
        log.info("Available KeyStore providers: {}", Arrays.toString(Security.getProviders()));
        try {
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER);
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_STORE_KEY_REF)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_PROVIDER);
                KeyGenParameterSpec keyGenParameterSpec = buildKeyGenParameterSpec();
                keyGenerator.init(keyGenParameterSpec);
                keyGenerator.generateKey();
            }
        } catch (NoSuchAlgorithmException | IOException | KeyStoreException | CertificateException |
                 InvalidAlgorithmParameterException | NoSuchProviderException e) {
            throw new KeyCrypterException("Key generation error", e);
        }

        // Unused return
        return new AesKey(new byte[0]);
    }

    /**
     * Builds the {@link KeyGenParameterSpec} based on the device's Android version and hardware capabilities.
     *
     * @return A configured {@link KeyGenParameterSpec} instance for key generation.
     * @throws KeyCrypterException If the Android version is not supported or if any other configuration error occurs.
     */
    private KeyGenParameterSpec buildKeyGenParameterSpec() throws KeyCrypterException {
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_STORE_KEY_REF,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_LENGTH)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean isStrongBoxAvailable = context.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);
            builder.setIsStrongBoxBacked(isStrongBoxAvailable)
                    .setUserAuthenticationParameters(KEY_AUTHENTICATION_DURATION, KeyProperties.AUTH_BIOMETRIC_STRONG);
            log.info("Using StrongBox: " + isStrongBoxAvailable);
        } else {
            log.info("Android version 30 or higher required for forced biometric encryption");
            throw new KeyCrypterException("Android version 30 or higher required");
        }
        return builder.build();
    }

    /**
     * Decrypt bytes previously encrypted with this class.
     *
     * @param dataToDecrypt    IV and data to decrypt
     * @param unusedAesKey              Required only by the interface. The key doesn't actually get used.
     * @return                          The decrypted bytes
     * @throws                          KeyCrypterException if bytes could not be decrypted
     */
    @Override
    public byte[] decrypt(EncryptedData dataToDecrypt, AesKey unusedAesKey) throws KeyCrypterException {
        log.info("Starting KeyStoreKeyCrypter decrypt method");
        try {
            return doDecrypt(dataToDecrypt);
        } catch (UserNotAuthenticatedException e) {
            // User must authenticate so catch exception and continue
        }
        catch (Exception e) {
            log.info("Exception was" + e.getClass());
            throw new KeyCrypterException("Could not decrypt the encrypted data.", e);
        }
        decryptionFuture = new CompletableFuture<>();
        this.currentEncryptedData = dataToDecrypt;

        // Move to the UI thread to call authenticate
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                createBiometricPrompt(false);
            } catch (Exception e) {
                decryptionFuture.completeExceptionally(new KeyCrypterException("Failed to initialize encryption", e));
            }
        });

        try {
            byte[] decryptedBytes = decryptionFuture.get();
            scryptParameters = null;
            return decryptedBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyCrypterException("Decryption was interrupted.", e);
        } catch (ExecutionException e) {
            throw new KeyCrypterException("Decryption failed.", e.getCause());
        }
    }

    public byte[] doDecrypt(EncryptedData dataToDecrypt) throws UserNotAuthenticatedException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER);
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_STORE_KEY_REF, null);
            Cipher cipher = Cipher.getInstance(KEY_STORE_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, dataToDecrypt.initialisationVector);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return cipher.doFinal(dataToDecrypt.encryptedBytes);
        } catch (UserNotAuthenticatedException e) {
            throw new UserNotAuthenticatedException("User needs to authenticate");
        }
        catch (Exception e) {
            log.info("Exception was " + e.getClass());
            throw new KeyCrypterException("Could not encrypt bytes.", e);
        }
    }


    /**
     * Encrypts data with an AES key stored in the Android key store
     *
     * @param plainBytes    data to be encrypted
     * @param unusedAesKey  Required only by the interface. The key doesn't actually get used.
     * @return              IV and encrypted data in an EncryptedData object
     * @throws              KeyCrypterException if bytes could not be decrypted
     */
    @Override
    public EncryptedData encrypt(byte[] plainBytes, AesKey unusedAesKey) throws KeyCrypterException {
        log.info("Starting KeyStoreKeyCrypter encrypt method");
        try {
            return doEncrypt(plainBytes);
        } catch (UserNotAuthenticatedException e) {
            // User must authenticate so catch exception and continue
        }
        catch (Exception e) {
            log.info("Exception was" + e.getClass());
            throw new KeyCrypterException("Could not encrypt bytes.", e);
        }

        // only reach code if user needs to authenticate
        encryptionFuture = new CompletableFuture<>();
        this.currentPlainBytes = plainBytes;

        // Move to the UI thread to call authenticate
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                createBiometricPrompt(true);
            } catch (Exception e) {
                encryptionFuture.completeExceptionally(new KeyCrypterException("Failed to initialize encryption", e));
            }
        });

        try {
            return encryptionFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyCrypterException("Encryption was interrupted.", e);
        } catch (ExecutionException e) {
            throw new KeyCrypterException("Encryption failed.", e.getCause());
        }
    }

    private EncryptedData doEncrypt(byte[] plainBytes) throws UserNotAuthenticatedException {
        Cipher cipher;
        try {
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER);
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_STORE_KEY_REF, null);
            cipher = Cipher.getInstance(KEY_STORE_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedText = cipher.doFinal(plainBytes);
            return new EncryptedData(cipher.getIV(), encryptedText, EncryptionType.ENCRYPTED_KEYSTORE_AES);
        } catch (UserNotAuthenticatedException e) {
            throw new UserNotAuthenticatedException("User must authenticate");
        }
        catch (Exception e) {
            log.info("Exception was" + e.getClass());
            throw new KeyCrypterException("Could not encrypt bytes.", e);
        }
    }

    /**
     * Return the EncryptionType enum value which denotes the type of encryption/ decryption that this KeyCrypter
     * can understand.
     */

    @Override
    public EncryptionType getUnderstoodEncryptionType() {
        return EncryptionType.ENCRYPTED_KEYSTORE_AES;
    }

    private CancellationSignal getCancellationSignal() {
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(() -> log.info("Cancelled via signal"));
        return cancellationSignal;
    }

    private void createBiometricPrompt(boolean isEncrypt) {
        Executor executor = Executors.newSingleThreadExecutor();
        BiometricPrompt.AuthenticationCallback callback = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            callback = new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if (isEncrypt) {
                        encryptionFuture.completeExceptionally(new KeyCrypterException("Biometric authentication error: " + errString));
                    } else {
                        decryptionFuture.completeExceptionally(new KeyCrypterException("Biometric authentication error: " + errString));
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    if (isEncrypt) {
                        encryptionFuture.completeExceptionally(new KeyCrypterException("Biometric authentication failed."));
                    } else {
                        decryptionFuture.completeExceptionally(new KeyCrypterException("Biometric authentication failed."));
                    }
                }

                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    try {
                        if (isEncrypt) {
                            EncryptedData encryptedDataResult = doEncrypt(currentPlainBytes);
                            encryptionFuture.complete(encryptedDataResult);
                        } else {
                            byte[] decryptedData = doDecrypt(currentEncryptedData);
                            decryptionFuture.complete(decryptedData);
                        }
                    } catch (Exception e) {
                        if (isEncrypt) {
                            encryptionFuture.completeExceptionally(e);
                        } else {
                            decryptionFuture.completeExceptionally(e);
                        }
                    }
                }
            };
        }

        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(() -> log.info("Cancelled via signal"));


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            BiometricPrompt biometricPrompt = new BiometricPrompt.Builder(context)
                    .setTitle(context.getString(R.string.biometric_auth))
                    .setSubtitle(context.getString(R.string.biometric_auth_required_info))
                    .setDescription(context.getString(R.string.biometric_auth_utilised_info))
                    .setNegativeButton(context.getString(R.string.button_cancel), context.getMainExecutor(),
                            (dialogInterface, i) -> log.info(context.getString(R.string.biometric_auth_cancelled)))
                    .build();
            biometricPrompt.authenticate(getCancellationSignal(), executor, callback);
        }
    }
}
