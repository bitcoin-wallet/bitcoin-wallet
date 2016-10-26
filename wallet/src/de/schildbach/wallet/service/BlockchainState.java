/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.service;

import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

import android.content.Intent;

/**
 * @author Andreas Schildbach
 */
public class BlockchainState {
    private static final String EXTRA_BEST_CHAIN_DATE = "best_chain_date";
    private static final String EXTRA_BEST_CHAIN_HEIGHT = "best_chain_height";
    private static final String EXTRA_REPLAYING = "replaying";
    private static final String EXTRA_IMPEDIMENTS = "impediment";

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

    public static BlockchainState fromIntent(final Intent intent) {
        final Date bestChainDate = (Date) intent.getSerializableExtra(EXTRA_BEST_CHAIN_DATE);
        final int bestChainHeight = intent.getIntExtra(EXTRA_BEST_CHAIN_HEIGHT, -1);
        final boolean replaying = intent.getBooleanExtra(EXTRA_REPLAYING, false);
        @SuppressWarnings("unchecked")
        final Set<Impediment> impediments = (Set<Impediment>) intent.getSerializableExtra(EXTRA_IMPEDIMENTS);

        return new BlockchainState(bestChainDate, bestChainHeight, replaying, impediments);
    }

    public void putExtras(final Intent intent) {
        intent.putExtra(EXTRA_BEST_CHAIN_DATE, bestChainDate);
        intent.putExtra(EXTRA_BEST_CHAIN_HEIGHT, bestChainHeight);
        intent.putExtra(EXTRA_REPLAYING, replaying);
        intent.putExtra(EXTRA_IMPEDIMENTS, impediments);
    }
}
