package de.schildbach.wallet.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;


import org.bitcoinj.core.Context;
import org.bitcoinj.crypto.AesKey;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.crypto.HWKeyCrypter;

public class CryptActivity extends Activity {

    protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

    private Wallet wallet;
    private WalletApplication application;
    private Configuration config;


    @SuppressLint("WrongThread")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("Referrer: {}", getReferrer());
        setContentView(R.layout.crypt_content);

        this.application = (WalletApplication) getApplication();
        this.wallet = application.getWallet();
        this.config = application.getConfiguration();
        final Context context = Context.get();

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Context.propagate(context);
            doCrypto();
            finish();
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Close the activity when the back arrow is pressed
        if (item.getItemId() == android.R.id.home) {
            setResult(Activity.RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void doCrypto() {
        try {
            final HWKeyCrypter keyCrypter = new HWKeyCrypter(getApplicationContext());
            final AesKey newKey = wallet.isEncrypted() != true ? keyCrypter.deriveKey(null) : null;

            // Decrypt wallet
            if (wallet.isEncrypted()) {
                wallet.decrypt("unusedPassword");
                log.info("wallet successfully decrypted");
                setResult(Activity.RESULT_OK);
            }
            // Use opportunity to maybe upgrade wallet
            if (wallet.isDeterministicUpgradeRequired(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE)
                    && !wallet.isEncrypted())
                wallet.upgradeToDeterministic(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE, null);

            // Encrypt with new key in the KeyStore
            if (newKey != null && !wallet.isEncrypted()) {
                wallet.encrypt(keyCrypter, newKey);
                config.updateLastEncryptKeysTime();
                log.info("wallet successfully encrypted, using Android KeyStore");
                setResult(Activity.RESULT_OK);
            }
        } catch (Exception e) {
            setResult(Activity.RESULT_CANCELED);
            throw e;
        }
    }
}