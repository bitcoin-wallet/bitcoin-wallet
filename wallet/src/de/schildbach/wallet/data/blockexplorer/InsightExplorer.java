package de.schildbach.wallet.data.blockexplorer;

import android.net.Uri;

import org.bitcoinj.core.Address;

/**
 * Created by Hash Engineering on 1/21/2016.
 *
 * https://chainz.cryptoid.info/
 */
public class InsightExplorer extends BlockExplorer {

    public InsightExplorer(String url, String urlTest)
    {
        super(url, urlTest, "block/", "tx/", "address/", "unspent/");
    }
}
