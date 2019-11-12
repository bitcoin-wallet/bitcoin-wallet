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

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Andreas Schildbach
 */
public class ActivityHistory {
    private static final int MIN_COLLECT_HISTORY = 2;
    private static final int IDLE_BLOCK_TIMEOUT_MIN = 1;
    private static final int IDLE_TRANSACTION_TIMEOUT_MIN = 5;
    private static final int MAX_HISTORY_SIZE = Math.max(IDLE_TRANSACTION_TIMEOUT_MIN, IDLE_BLOCK_TIMEOUT_MIN);

    private static final Logger log = LoggerFactory.getLogger(ActivityHistory.class);

    private final Deque<Entry> history = new LinkedList<>();
    private final AtomicInteger transactionsReceived = new AtomicInteger();
    private final AtomicInteger bestChainHeight = new AtomicInteger();

    private int lastChainHeight = 0;

    public void registerTransactionReceived() {
        this.transactionsReceived.incrementAndGet();
    }

    public void registerBestChainHeight(final int bestChainHeight) {
        this.bestChainHeight.set(bestChainHeight);
    }

    /**
     * Invoke this on a regular interval to record history, ideally once a minute.
     */
    public synchronized void tick() {
        final int chainHeight = bestChainHeight.get();

        if (lastChainHeight > 0) {
            final int numBlocksDownloaded = chainHeight - lastChainHeight;
            final int numTransactionsReceived = transactionsReceived.getAndSet(0);

            // push history
            history.addFirst(new Entry(numTransactionsReceived, numBlocksDownloaded));

            // trim
            while (history.size() > MAX_HISTORY_SIZE)
                history.removeLast();

            // print
            log.info(toString());
        }

        lastChainHeight = chainHeight;
    }

    /**
     * Determine if block and transaction activity is idling.
     */
    public synchronized boolean isIdle() {
        boolean isIdle = false;
        if (history.size() >= MIN_COLLECT_HISTORY) {
            isIdle = true;
            int i = 0;
            for (final Entry entry : history) {
                final boolean blocksActive = entry.numBlocksDownloaded > 0 && i <= IDLE_BLOCK_TIMEOUT_MIN;
                final boolean transactionsActive = entry.numTransactionsReceived > 0
                        && i <= IDLE_TRANSACTION_TIMEOUT_MIN;
                i++;

                if (blocksActive || transactionsActive) {
                    isIdle = false;
                    break;
                }
            }
        }
        return isIdle;
    }

    @Override
    public synchronized String toString() {
        final StringBuilder builder = new StringBuilder("secsAgo/txns/blocks: ");
        Joiner.on(", ").appendTo(builder, history);
        return builder.toString();
    }

    public static final class Entry {
        public final long time;
        public final int numTransactionsReceived;
        public final int numBlocksDownloaded;

        public Entry(final int numTransactionsReceived, final int numBlocksDownloaded) {
            this.time = System.currentTimeMillis();
            this.numTransactionsReceived = numTransactionsReceived;
            this.numBlocksDownloaded = numBlocksDownloaded;
        }

        @Override
        public String toString() {
            return toString(System.currentTimeMillis());
        }

        public String toString(final long currentTime) {
            final long secsAgo = (currentTime - time) / 1000;
            return secsAgo + "/" + numTransactionsReceived + "/" + numBlocksDownloaded;
        }
    }
}
