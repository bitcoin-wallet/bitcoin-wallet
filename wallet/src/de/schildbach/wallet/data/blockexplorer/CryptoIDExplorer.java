package de.schildbach.wallet.data.blockexplorer;

import android.net.Uri;

import org.bitcoinj.core.Address;

/**
 * Created by Hash Engineering on 1/21/2016.
 *
 * https://chainz.cryptoid.info/
 */
public class CryptoIDExplorer extends BlockExplorer {

    String apiKey = "&key=d47da926b82e";
    public CryptoIDExplorer(String url, String urlTest)
    {
        super(url, urlTest, "block.dws?", "tx.dws?", "address.dws?", "api.dws?q=unspent");
    }

    @Override
    public Uri getUnspentUrl(Address address)
    {
        return Uri.withAppendedPath(getUri(), pathForUnspent + apiKey + "&active="+address);
    }
}
