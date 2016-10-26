/*
 * Copyright 2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.util;

import java.io.IOException;

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

/**
 * @author Andreas Schildbach
 */
public class WalletUtilsTest {
    @Test
    public void restoreWalletFromProtobufOrBase58() throws Exception {
        WalletUtils.restoreWalletFromProtobufOrBase58(getClass().getResourceAsStream("backup-protobuf-testnet"),
                TestNet3Params.get());
        WalletUtils.restoreWalletFromProtobufOrBase58(getClass().getResourceAsStream("backup-base58-testnet"),
                TestNet3Params.get());
    }

    @Test(expected = IOException.class)
    public void restoreWalletFromProtobuf_wrongNetwork() throws Exception {
        WalletUtils.restoreWalletFromProtobufOrBase58(getClass().getResourceAsStream("backup-protobuf-testnet"),
                MainNetParams.get());
    }

    @Test(expected = IOException.class)
    public void restoreWalletFromBase58_wrongNetwork() throws Exception {
        WalletUtils.restoreWalletFromProtobufOrBase58(getClass().getResourceAsStream("backup-base58-testnet"),
                MainNetParams.get());
    }
}
