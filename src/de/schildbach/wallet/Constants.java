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

import java.math.BigInteger;

import android.content.Context;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class Constants
{
	public static final boolean TEST = R.class.getPackage().getName().contains('_' + "test"); // replace protected

	public static final NetworkParameters NETWORK_PARAMETERS = TEST ? NetworkParameters.testNet() : NetworkParameters.prodNet();

	public static final String WALLET_FILENAME_PROD = "wallet";
	public static final String WALLET_FILENAME_TEST = "wallet-testnet";

	private static final String WALLET_KEY_BACKUP_ASN1_PROD = "key-backup-asn1";
	private static final String WALLET_KEY_BACKUP_ASN1_TEST = "key-backup-asn1-testnet";
	public static final String WALLET_KEY_BACKUP_ASN1 = Constants.TEST ? WALLET_KEY_BACKUP_ASN1_TEST : WALLET_KEY_BACKUP_ASN1_PROD;

	private static final String WALLET_KEY_BACKUP_BASE58_PROD = "key-backup-base58";
	private static final String WALLET_KEY_BACKUP_BASE58_TEST = "key-backup-base58-testnet";
	public static final String WALLET_KEY_BACKUP_BASE58 = Constants.TEST ? WALLET_KEY_BACKUP_BASE58_TEST : WALLET_KEY_BACKUP_BASE58_PROD;

	public static final int WALLET_MODE_PROD = Context.MODE_PRIVATE;
	public static final int WALLET_MODE_TEST = Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE;
	public static final int WALLET_MODE = Constants.TEST ? WALLET_MODE_TEST : WALLET_MODE_PROD;

	public static final String BLOCKCHAIN_SNAPSHOT_FILENAME_PROD = "blockchain-snapshot.jpg";
	public static final String BLOCKCHAIN_SNAPSHOT_FILENAME_TEST = "blockchain-snapshot-testnet.jpg";

	public static final long BLOCKCHAIN_SNAPSHOT_COPY_THRESHOLD = Constants.TEST ? 3000000 : 10000000;

	private static final String BLOCKCHAIN_FILENAME_PROD = "blockchain";
	public static final String BLOCKCHAIN_FILENAME_TEST = "blockchain-testnet";
	public static final String BLOCKCHAIN_FILENAME = TEST ? BLOCKCHAIN_FILENAME_TEST : BLOCKCHAIN_FILENAME_PROD;

	public static final String PEER_DISCOVERY_IRC_CHANNEL_PROD = "#bitcoin";
	public static final String PEER_DISCOVERY_IRC_CHANNEL_TEST = "#bitcoinTEST";

	public static final String PACKAGE_NAME_PROD = "de.schildbach.wallet";
	public static final String PACKAGE_NAME_TEST = "de.schildbach.wallet" + '_' + "test"; // replace protected

	public static final int APP_ICON_RESID = Constants.TEST ? R.drawable.app_icon_test : R.drawable.app_icon;

	public static final int MAX_CONNECTED_PEERS = 6;
	public static final int BLOCKCHAIN_PROGRESS_TIMEOUT = 120 * 1000;

	public static final String DONATION_ADDRESS = "1PZmMahjbfsTy6DsaRyfStzoWTPppWwDnZ";

	public static final String LICENSE_URL = "http://www.gnu.org/licenses/gpl-3.0.txt";
	public static final String SOURCE_URL = "http://code.google.com/p/bitcoin-wallet/";
	public static final String CREDITS_BITCOINJ_URL = "http://code.google.com/p/bitcoinj/";
	public static final String CREDITS_ZXING_URL = "http://code.google.com/p/zxing/";
	public static final String CREDITS_ICON_URL = "http://www.bitcoin.org/smf/index.php?action=profile;u=2062";
	public static final String AUTHOR_TWITTER_URL = "http://twitter.com/android_bitcoin";
	public static final String AUTHOR_GOOGLEPLUS_URL = "https://profiles.google.com/andreas.schildbach";
	public static final String MARKET_APP_URL = "market://details?id=%s";
	public static final String WEBMARKET_APP_URL = "https://market.android.com/details?id=%s";
	public static final String MARKET_PUBLISHER_URL = "market://search?q=pub:\"Andreas Schildbach\"";

	public static final String PREFS_KEY_LAST_VERSION = "last_version";
	public static final String PREFS_KEY_SELECTED_ADDRESS = "selected_address";
	public static final String PREFS_KEY_EXCHANGE_CURRENCY = "exchange_currency";
	public static final String PREFS_KEY_TRUSTED_PEER = "trusted_peer";
	public static final String PREFS_KEY_INITIATE_RESET = "initiate_reset";

	public static final BigInteger DEFAULT_TX_FEE = Utils.CENT.divide(BigInteger.valueOf(20));
}
