package de.schildbach.wallet.data.blockexplorer;

import android.util.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Eric on 1/21/2016.
 */
public class BlockExplorers {
    HashMap<Pair<String, String>, BlockExplorer> explorers;

    public BlockExplorers()
    {
        explorers = new HashMap<Pair<String, String>, BlockExplorer>();
    }

    public BlockExplorer getExplorer(String url, boolean testNet)
    {
        for(Map.Entry<Pair<String, String>, BlockExplorer> entry : explorers.entrySet()) {
            if(url.equals(entry.getKey().first) || url.equals(entry.getKey().second)) {
                return entry.getValue().init(testNet);
            }
        }
        return null;
    }

    public void add(String url, String urlTest, String pathForBlock, String pathForTx, String pathForAddress)
    {
        explorers.put(new Pair(url, urlTest), new BlockExplorer(url, urlTest, pathForBlock, pathForTx, pathForAddress));
    }

    public void add(BlockExplorer explorer)
    {
        explorers.put(new Pair(explorer.url.toString(), explorer.urlTest.toString()), explorer);
    }
}
