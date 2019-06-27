package de.schildbach.wallet.data.blockexplorer;

/**
 * Created by Hash Engineering on 1/21/2016.
 *
 * https://chainz.cryptoid.info/
 */
public class BlockchairExplorer extends BlockExplorer {

    public BlockchairExplorer(String url, String urlTest)
    {
        super(url, urlTest, "block/", "transaction/", "address/", "unspent/");
    }
}
