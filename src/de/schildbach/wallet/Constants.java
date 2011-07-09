/*
 * Copyright 2010 the original author or authors.
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

package de.schildbach.wallet;

import android.content.Context;

/**
 * @author Andreas Schildbach
 */
public class Constants
{
	public static final boolean TEST = true;

	public static final String WALLET_FILENAME_PROD = "wallet";
	public static final String WALLET_FILENAME_TEST = "wallet-testnet";
	public static final int WALLET_MODE_PROD = Context.MODE_PRIVATE;
	public static final int WALLET_MODE_TEST = Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE;
	public static final String BLOCKCHAIN_SNAPSHOT_FILENAME_PROD = "blockchain-snapshot";
	public static final String BLOCKCHAIN_SNAPSHOT_FILENAME_TEST = "blockchain-snapshot-testnet";
	public static final String BLOCKCHAIN_FILENAME_PROD = "blockchain";
	public static final String BLOCKCHAIN_FILENAME_TEST = "blockchain-testnet";
	public static final String PEER_DISCOVERY_IRC_CHANNEL_PROD = "#bitcoin";
	public static final String PEER_DISCOVERY_IRC_CHANNEL_TEST = "#bitcoinTEST";

	public static final int MAX_CONNECTED_PEERS = 4;

	public static final String LICENSE_URL = "http://www.gnu.org/licenses/gpl-3.0.txt";
	public static final String SOURCE_URL = "http://code.google.com/p/bitcoin-wallet/";
	public static final String CREDITS_BITCOINJ_URL = "http://code.google.com/p/bitcoinj/";
	public static final String CREDITS_ZXING_URL = "http://code.google.com/p/zxing/";
	public static final String CREDITS_ICON_URL = "http://www.bitcoin.org/smf/index.php?action=profile;u=2062";
	public static final String TWITTER_URL = "http://twitter.com/android_bitcoin";
	public static final String MARKET_APP_URL = "market://details?id=%s";
	public static final String MARKET_PUBLISHER_URL = "market://search?q=pub:\"Andreas Schildbach\"";

	public static final String PREFS_KEY_EXCHANGE_CURRENCY = "exchange_currency";
}
