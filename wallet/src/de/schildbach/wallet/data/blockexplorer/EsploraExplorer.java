package de.schildbach.wallet.data.blockexplorer;

/**
 * Created by Hash Engineering on 7/5/2021.
 *
 * https://esplora.groestlcoin.org/
 */
public class EsploraExplorer extends BlockExplorer {

    public EsploraExplorer(String url, String urlTest)
    {
        super(url, urlTest, "block/", "tx/", "address/", "address/utxo");
    }
}
