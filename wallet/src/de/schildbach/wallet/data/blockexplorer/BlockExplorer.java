package de.schildbach.wallet.data.blockexplorer;

import android.net.Uri;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Sha256Hash;

/**
 * Created by Hash Engineering on 1/21/2016.
 */
public class BlockExplorer {
    Uri url;
    Uri urlTest;
    String pathForBlock;
    String pathForTx;
    String pathForAddress;
    String pathForUnspent;
    boolean testNet;

    public BlockExplorer(String url, String urlTest, String pathForBlock, String pathForTx, String pathForAddress)
    {
        this.url = Uri.parse(url);
        this.urlTest = Uri.parse(urlTest);
        this.pathForAddress = pathForAddress;
        this.pathForTx = pathForTx;
        this.pathForBlock = pathForBlock;
    }

    public BlockExplorer(String url, String urlTest, String pathForBlock, String pathForTx, String pathForAddress, String pathForUnspent)
    {
        this(url, urlTest, pathForBlock, pathForTx, pathForAddress);
        this.pathForUnspent = pathForUnspent;
    }

    public Uri getUri()
    {
        return testNet ? urlTest : url;
    }

    public BlockExplorer init(boolean testNet) {
        this.testNet = testNet;
        return this;
    }

    public Uri getBlockUrl(Sha256Hash hash)
    {
        return Uri.withAppendedPath(getUri(), pathForBlock + hash);
    }

    public Uri getTxUrl(Sha256Hash hash)
    {
        return Uri.withAppendedPath(getUri(), pathForTx + hash);
    }

    public Uri getAddressUrl(Address address)
    {
        return Uri.withAppendedPath(getUri(), pathForAddress + address);
    }

    public Uri getUnspentUrl(Address address)
    {
        return Uri.withAppendedPath(getUri(),pathForUnspent + address);
    }

}
