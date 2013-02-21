/*
 * Copyright 2011-2013 the original author or authors.
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

import java.io.File;
import java.math.BigInteger;

import android.os.Environment;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class Constants
{
	public static final boolean TEST = R.class.getPackage().getName().contains("_test");

	public static final String NETWORK_SUFFIX = TEST ? " [testnet3]" : "";

	public static final NetworkParameters NETWORK_PARAMETERS = TEST ? NetworkParameters.testNet3() : NetworkParameters.prodNet();

	private static final String WALLET_FILENAME_PROD = "wallet";
	private static final String WALLET_FILENAME_TEST = "wallet-testnet";
	public static final String WALLET_FILENAME = TEST ? WALLET_FILENAME_TEST : WALLET_FILENAME_PROD;

	private static final String WALLET_FILENAME_PROTOBUF_PROD = "wallet-protobuf";
	private static final String WALLET_FILENAME_PROTOBUF_TEST = "wallet-protobuf-testnet";
	public static final String WALLET_FILENAME_PROTOBUF = TEST ? WALLET_FILENAME_PROTOBUF_TEST : WALLET_FILENAME_PROTOBUF_PROD;

	private static final String WALLET_KEY_BACKUP_BASE58_PROD = "key-backup-base58";
	private static final String WALLET_KEY_BACKUP_BASE58_TEST = "key-backup-base58-testnet";
	public static final String WALLET_KEY_BACKUP_BASE58 = TEST ? WALLET_KEY_BACKUP_BASE58_TEST : WALLET_KEY_BACKUP_BASE58_PROD;

	public static final File EXTERNAL_WALLET_BACKUP_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
	private static final String EXTERNAL_WALLET_KEY_BACKUP_PROD = "bitcoin-wallet-keys";
	private static final String EXTERNAL_WALLET_KEY_BACKUP_TEST = "bitcoin-wallet-keys-testnet";
	public static final String EXTERNAL_WALLET_KEY_BACKUP = TEST ? EXTERNAL_WALLET_KEY_BACKUP_TEST : EXTERNAL_WALLET_KEY_BACKUP_PROD;

	private static final String WALLET_KEY_BACKUP_SNAPSHOT_PROD = "key-backup-snapshot";
	private static final String WALLET_KEY_BACKUP_SNAPSHOT_TEST = "key-backup-snapshot-testnet";
	public static final String WALLET_KEY_BACKUP_SNAPSHOT = TEST ? WALLET_KEY_BACKUP_SNAPSHOT_TEST : WALLET_KEY_BACKUP_SNAPSHOT_PROD;

	private static final String BLOCKCHAIN_SNAPSHOT_FILENAME_PROD = "blockchain-snapshot.jpg";
	private static final String BLOCKCHAIN_SNAPSHOT_FILENAME_TEST = "blockchain-snapshot-testnet.jpg";
	public static final String BLOCKCHAIN_SNAPSHOT_FILENAME = TEST ? BLOCKCHAIN_SNAPSHOT_FILENAME_TEST : BLOCKCHAIN_SNAPSHOT_FILENAME_PROD;

	private static final String BLOCKCHAIN_FILENAME_PROD = "blockchain";
	private static final String BLOCKCHAIN_FILENAME_TEST = "blockchain-testnet";
	public static final String BLOCKCHAIN_FILENAME = TEST ? BLOCKCHAIN_FILENAME_TEST : BLOCKCHAIN_FILENAME_PROD;

	public static final String PEER_DISCOVERY_IRC_CHANNEL_PROD = "#bitcoin";
	public static final String PEER_DISCOVERY_IRC_CHANNEL_TEST = "#bitcoinTEST3";

	private static final String BLOCKEXPLORER_BASE_URL_PROD = "https://blockexplorer.com/";
	private static final String BLOCKEXPLORER_BASE_URL_TEST = "https://blockexplorer.com/testnet/";
	public static final String BLOCKEXPLORER_BASE_URL = TEST ? BLOCKEXPLORER_BASE_URL_TEST : BLOCKEXPLORER_BASE_URL_PROD;

	private static final String PACKAGE_NAME_PROD = "de.schildbach.wallet";
	private static final String PACKAGE_NAME_TEST = "de.schildbach.wallet_test";
	public static final String PACKAGE_NAME = TEST ? PACKAGE_NAME_TEST : PACKAGE_NAME_PROD;

	public static final String MIMETYPE_TRANSACTION = "application/x-btctx";

	public static final int MAX_CONNECTED_PEERS = 6;
	public static final int MAX_NUM_CONFIRMATIONS = 7;
	public static final String USER_AGENT = "Bitcoin Wallet";
	public static final String DEFAULT_EXCHANGE_CURRENCY = "USD";
	public static final int WALLET_OPERATION_STACK_SIZE = 256 * 1024;
	public static final int BLOCKCHAIN_DOWNLOAD_THRESHOLD_MS = 5000;
	public static final int BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = 1000;
	public static final int BLOCKCHAIN_UPTODATE_THRESHOLD_HOURS = 1;
	public static final String LOCK_NAME = PACKAGE_NAME + " blockchain sync";

	public static final String CURRENCY_CODE_BITCOIN = "BTC";
	public static final char CHAR_HAIR_SPACE = '\u200a';
	public static final char CHAR_THIN_SPACE = '\u2009';
	public static final char CHAR_ALMOST_EQUAL_TO = '\u2248';
	public static final String CURRENCY_PLUS_SIGN = "+" + CHAR_THIN_SPACE;
	public static final String CURRENCY_MINUS_SIGN = "-" + CHAR_THIN_SPACE;
	public static final String PREFIX_ALMOST_EQUAL_TO = Character.toString(CHAR_ALMOST_EQUAL_TO) + CHAR_THIN_SPACE;
	public static final int ADDRESS_FORMAT_GROUP_SIZE = 4;
	public static final int ADDRESS_FORMAT_LINE_SIZE = 12;

	public static final int BTC_PRECISION = 8;
	public static final int LOCAL_PRECISION = 4;

	public static final String DONATION_ADDRESS = "1MH7wUsZ8K28q7LUrn2PQgDEykAbEibU4G";

	public static final String LICENSE_URL = "http://www.gnu.org/licenses/gpl-3.0.txt";
	public static final String SOURCE_URL = "http://code.google.com/p/bitcoin-wallet/";
	public static final String BINARY_URL = "http://code.google.com/p/bitcoin-wallet/downloads/list";
	public static final String CREDITS_BITCOINJ_URL = "http://code.google.com/p/bitcoinj/";
	public static final String CREDITS_ZXING_URL = "http://code.google.com/p/zxing/";
	public static final String CREDITS_ICON_URL = "https://bitcointalk.org/index.php?action=profile;u=2062";
	public static final String AUTHOR_TWITTER_URL = "https://twitter.com/#!/bitcoin_wallet";
	public static final String AUTHOR_GOOGLEPLUS_URL = "https://profiles.google.com/andreas.schildbach";
	public static final String MARKET_APP_URL = "market://details?id=%s";
	public static final String WEBMARKET_APP_URL = "https://play.google.com/store/apps/details?id=%s";
	public static final String MARKET_PUBLISHER_URL = "market://search?q=pub:\"Andreas Schildbach\"";

	private static final String VERSION_URL_PROD = "http://wallet.schildbach.de/version";
	private static final String VERSION_URL_TEST = "http://wallet.schildbach.de/version_test";
	public static final String VERSION_URL = TEST ? VERSION_URL_TEST : VERSION_URL_PROD;

	public static final String PREFS_KEY_LAST_VERSION = "last_version";
	public static final String PREFS_KEY_LAST_USED = "last_used";
	public static final String PREFS_KEY_BEST_CHAIN_HEIGHT_EVER = "best_chain_height_ever";
	public static final String PREFS_KEY_ALERT_OLD_SDK_DISMISSED = "alert_old_sdk_dismissed";
	public static final String PREFS_KEY_AUTOSYNC = "autosync";
	public static final String PREFS_KEY_CONNECTIVITY_NOTIFICATION = "connectivity_notification";
	public static final String PREFS_KEY_SELECTED_ADDRESS = "selected_address";
	public static final String PREFS_KEY_EXCHANGE_CURRENCY = "exchange_currency";
	public static final String PREFS_KEY_TRUSTED_PEER = "trusted_peer";
	public static final String PREFS_KEY_TRUSTED_PEER_ONLY = "trusted_peer_only";
	public static final String PREFS_KEY_LABS_SEND_COINS_LOW_FEE = "labs_send_coins_low_fee";
	public static final String PREFS_KEY_LABS_TRANSACTION_DETAILS = "labs_transactions_details";

	public static final BigInteger DEFAULT_TX_FEE = Utils.CENT.divide(BigInteger.valueOf(20));

	public static final long LAST_USAGE_THRESHOLD_JUST_MS = 1000 * 60 * 60 * 1;
	public static final long LAST_USAGE_THRESHOLD_RECENTLY_MS = 1000 * 60 * 60 * 48;
}
