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
import android.text.format.DateUtils;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.TestNet3Params;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class Constants
{
	public static final boolean TEST = R.class.getPackage().getName().contains("_test");

	public static final String NETWORK_SUFFIX = TEST ? " [testnet3]" : "";

	public static final NetworkParameters NETWORK_PARAMETERS = TEST ? TestNet3Params.get() : MainNetParams.get();

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

	private static final String BLOCKCHAIN_FILENAME_PROD = "blockchain";
	private static final String BLOCKCHAIN_FILENAME_TEST = "blockchain-testnet";
	public static final String BLOCKCHAIN_FILENAME = TEST ? BLOCKCHAIN_FILENAME_TEST : BLOCKCHAIN_FILENAME_PROD;

	public static final String CHECKPOINTS_FILENAME = "checkpoints";

	private static final String BLOCKEXPLORER_BASE_URL_PROD = "https://blockexplorer.com/";
	private static final String BLOCKEXPLORER_BASE_URL_TEST = "https://blockexplorer.com/testnet/";
	public static final String BLOCKEXPLORER_BASE_URL = TEST ? BLOCKEXPLORER_BASE_URL_TEST : BLOCKEXPLORER_BASE_URL_PROD;

	public static final String MIMETYPE_TRANSACTION = "application/x-btctx";

	public static final int MAX_NUM_CONFIRMATIONS = 7;
	public static final String USER_AGENT = "Bitcoin Wallet";
	public static final String DEFAULT_EXCHANGE_CURRENCY = "USD";
	public static final int WALLET_OPERATION_STACK_SIZE = 256 * 1024;
	public static final long BLOCKCHAIN_DOWNLOAD_THRESHOLD_MS = 5 * DateUtils.SECOND_IN_MILLIS;
	public static final long BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;
	public static final long BLOCKCHAIN_UPTODATE_THRESHOLD_MS = DateUtils.HOUR_IN_MILLIS;

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

	public static final String DONATION_ADDRESS = "15P7W9X5xWVLewpnSk5gjLWVakvZ3NRUGN";
	public static final String REPORT_EMAIL = "wallet@schildbach.de";
	public static final String REPORT_SUBJECT_ISSUE = "Reported issue";
	public static final String REPORT_SUBJECT_CRASH = "Crash report";

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

	public static final String VERSION_URL = "http://wallet.schildbach.de/version";

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
	public static final String PREFS_KEY_LABS_TRANSACTION_DETAILS = "labs_transactions_details";
	public static final String PREFS_KEY_BTC_PRECISION = "btc_precision";
	public static final String PREFS_KEY_DISCLAIMER = "disclaimer";

	public static final BigInteger DUST = Utils.CENT.divide(BigInteger.valueOf(100));

	public static final long LAST_USAGE_THRESHOLD_JUST_MS = DateUtils.HOUR_IN_MILLIS;
	public static final long LAST_USAGE_THRESHOLD_RECENTLY_MS = 2 * DateUtils.DAY_IN_MILLIS;

	public static final int SDK_JELLY_BEAN = 16;

	public static final int MEMORY_CLASS_LOWEND = 48;
}
