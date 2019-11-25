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

package de.schildbach.wallet.service;

import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public class BlockchainState {
    public enum Impediment {
        STORAGE, NETWORK
    }

    public final Date bestChainDate;
    public final int bestChainHeight;
    public final boolean replaying;
    public final EnumSet<Impediment> impediments;

    public BlockchainState(final Date bestChainDate, final int bestChainHeight, final boolean replaying,
            final Set<Impediment> impediments) {
        this.bestChainDate = bestChainDate;
        this.bestChainHeight = bestChainHeight;
        this.replaying = replaying;
        this.impediments = EnumSet.copyOf(impediments);
    }
}
