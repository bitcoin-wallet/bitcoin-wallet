package de.schildbach.wallet.crypto;

import android.content.Context;

import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterFactory;

public class HWKeyCrypterFactory implements KeyCrypterFactory {
    private Context context;

    public HWKeyCrypterFactory(Context context) {
        this.context = context;
    }

    @Override
    public KeyCrypter createKeyCrypter() {
        return new HWKeyCrypter(context);
    }
}
