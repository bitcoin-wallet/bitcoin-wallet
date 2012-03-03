/*
 * Copyright 2012-2014 the original author or authors.
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

import java.util.List;

import javax.annotation.CheckForNull;

import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.StoredBlock;

import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public interface BlockchainService
{
	public static final String ACTION_PEER_STATE = R.class.getPackage().getName() + ".peer_state";
	public static final String ACTION_PEER_STATE_NUM_PEERS = "num_peers";

	public static final String ACTION_BLOCKCHAIN_STATE = R.class.getPackage().getName() + ".blockchain_state";
	public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE = "best_chain_date";
	public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_HEIGHT = "best_chain_height";
	public static final String ACTION_BLOCKCHAIN_STATE_REPLAYING = "replaying";
	public static final String ACTION_BLOCKCHAIN_STATE_DOWNLOAD = "download";
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK = 0;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM = 1;
	public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM = 2;

	public static final String ACTION_CANCEL_COINS_RECEIVED = R.class.getPackage().getName() + ".cancel_coins_received";
	public static final String ACTION_RESET_BLOCKCHAIN = R.class.getPackage().getName() + ".reset_blockchain";
	public static final String ACTION_BROADCAST_TRANSACTION = R.class.getPackage().getName() + ".broadcast_transaction";
	public static final String ACTION_BROADCAST_TRANSACTION_HASH = "hash";

	@CheckForNull
	List<Peer> getConnectedPeers();

	List<StoredBlock> getRecentBlocks(int maxBlocks);
}
