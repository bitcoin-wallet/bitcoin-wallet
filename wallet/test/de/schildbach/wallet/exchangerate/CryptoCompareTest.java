/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.exchangerate;

import com.squareup.moshi.Moshi;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Andreas Schildbach
 */
public class CryptoCompareTest {
    private final CryptoCompare cryptoCompare = new CryptoCompare(new Moshi.Builder().build());

    @Test
    public void parse() throws Exception {
        final BufferedSource json = Okio.buffer(Okio.source(getClass().getResourceAsStream("cryptocompare.json")));
        final List<ExchangeRateEntry> rates = cryptoCompare.parse(json);
        assertEquals(44, rates.size());
    }
}
