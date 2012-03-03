/*
 * Copyright 2011-2014 the original author or authors.
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

import android.os.Build;
import android.os.Environment;
import android.text.format.DateUtils;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.TestNet3Params;

import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class Constants
{
	public static final boolean TEST = R.class.getPackage().getName().contains("_test");

	/** Network this wallet is on (e.g. testnet or mainnet). */
	public static final NetworkParameters NETWORK_PARAMETERS = TEST ? TestNet3Params.get() : MainNetParams.get();

	public final static class Files
	{
		private static final String FILENAME_NETWORK_SUFFIX = NETWORK_PARAMETERS.getId().equals(NetworkParameters.ID_MAINNET) ? "" : "-testnet";

		/** Filename of the wallet. */
		public static final String WALLET_FILENAME_PROTOBUF = "wallet-protobuf" + FILENAME_NETWORK_SUFFIX;

		/** Filename of the automatic key backup (old format, can only be read). */
		public static final String WALLET_KEY_BACKUP_BASE58 = "key-backup-base58" + FILENAME_NETWORK_SUFFIX;

		/** Filename of the automatic wallet backup. */
		public static final String WALLET_KEY_BACKUP_PROTOBUF = "key-backup-protobuf" + FILENAME_NETWORK_SUFFIX;

		/** Manual backups go here. */
		public static final File EXTERNAL_WALLET_BACKUP_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

		/** Filename of the manual key backup (old format, can only be read). */
		public static final String EXTERNAL_WALLET_KEY_BACKUP = "bitcoin-wallet-keys" + FILENAME_NETWORK_SUFFIX;

		/** Filename of the manual wallet backup. */
		public static final String EXTERNAL_WALLET_BACKUP = "bitcoin-wallet-backup" + FILENAME_NETWORK_SUFFIX;

		/** Filename of the block store for storing the chain. */
		public static final String BLOCKCHAIN_FILENAME = "blockchain" + FILENAME_NETWORK_SUFFIX;

		/** Filename of the block checkpoints file. */
		public static final String CHECKPOINTS_FILENAME = "checkpoints" + FILENAME_NETWORK_SUFFIX;
	}

	/** Maximum size of backups. Files larger will be rejected. */
	public static final long BACKUP_MAX_CHARS = 10000000;

	private static final String EXPLORE_BASE_URL_PROD = "https://www.biteasy.com/";
	private static final String EXPLORE_BASE_URL_TEST = "https://www.biteasy.com/testnet/";
	/** Base URL for browsing transactions, blocks or addresses. */
	public static final String EXPLORE_BASE_URL = NETWORK_PARAMETERS.getId().equals(NetworkParameters.ID_MAINNET) ? EXPLORE_BASE_URL_PROD
			: EXPLORE_BASE_URL_TEST;

	private static final String BITEASY_API_URL_PROD = "https://api.biteasy.com/blockchain/v1/";
	private static final String BITEASY_API_URL_TEST = "https://api.biteasy.com/testnet/v1/";
	/** Base URL for blockchain API. */
	public static final String BITEASY_API_URL = NETWORK_PARAMETERS.getId().equals(NetworkParameters.ID_MAINNET) ? BITEASY_API_URL_PROD
			: BITEASY_API_URL_TEST;

	/** URL to fetch version alerts from. */
	public static final String VERSION_URL = "http://wallet.schildbach.de/version";

	/** MIME type used for transmitting single transactions. */
	public static final String MIMETYPE_TRANSACTION = "application/x-btctx";

	/** MIME type used for transmitting wallet backups. */
	public static final String MIMETYPE_WALLET_BACKUP = "application/x-bitcoin-wallet-backup";

	/** Number of confirmations until a transaction is fully confirmed. */
	public static final int MAX_NUM_CONFIRMATIONS = 7;

	/** User-agent to use for network access. */
	public static final String USER_AGENT = "Bitcoin Wallet";

	/** Default currency to use if all default mechanisms fail. */
	public static final String DEFAULT_EXCHANGE_CURRENCY = "USD";

	/** Currency code for base 1 Bitcoin. */
	public static final String CURRENCY_CODE_BTC = "BTC";

	/** Currency code for base 1/1000 Bitcoin. */
	public static final String CURRENCY_CODE_MBTC = "mBTC";

	/** Currency code for base 1/1000000 Bitcoin. */
	public static final String CURRENCY_CODE_UBTC = "µBTC";

	/** Donation address for tip/donate action. */
	public static final String DONATION_ADDRESS = "18CK5k1gajRKKSC7yVSTXT9LUzbheh1XY4";

	/** Recipient e-mail address for reports. */
	public static final String REPORT_EMAIL = "bitcoin.wallet.developers@gmail.com";

	/** Subject line for manually reported issues. */
	public static final String REPORT_SUBJECT_ISSUE = "Reported issue";

	/** Subject line for crash reports. */
	public static final String REPORT_SUBJECT_CRASH = "Crash report";

	public static final char CHAR_HAIR_SPACE = '\u200a';
	public static final char CHAR_THIN_SPACE = '\u2009';
	public static final char CHAR_ALMOST_EQUAL_TO = '\u2248';
	public static final char CHAR_CHECKMARK = '\u2713';
	public static final String CURRENCY_PLUS_SIGN = "+" + CHAR_THIN_SPACE;
	public static final String CURRENCY_MINUS_SIGN = "-" + CHAR_THIN_SPACE;
	public static final String PREFIX_ALMOST_EQUAL_TO = Character.toString(CHAR_ALMOST_EQUAL_TO) + CHAR_THIN_SPACE;
	public static final int ADDRESS_FORMAT_GROUP_SIZE = 4;
	public static final int ADDRESS_FORMAT_LINE_SIZE = 12;

	public static final int BTC_MAX_PRECISION = 8;
	public static final int MBTC_MAX_PRECISION = 5;
	public static final int UBTC_MAX_PRECISION = 2;
	public static final int LOCAL_PRECISION = 4;

	public static final String LICENSE_URL = "http://www.gnu.org/licenses/gpl-3.0.txt";
	public static final String SOURCE_URL = "https://github.com/schildbach/bitcoin-wallet";
	public static final String BINARY_URL = "https://github.com/schildbach/bitcoin-wallet/releases";
	public static final String CREDITS_BITCOINJ_URL = "https://bitcoinj.github.io";
	public static final String CREDITS_ZXING_URL = "https://github.com/zxing/zxing";
	public static final String CREDITS_ICON_URL = "https://bitcointalk.org/index.php?action=profile;u=2062";
	public static final String TWITTER_URL = "https://twitter.com/#!/bitcoin_wallet";
	public static final String COMMUNITY_GOOGLEPLUS_URL = "https://plus.google.com/communities/105515929887248493912";
	public static final String MARKET_APP_URL = "market://details?id=%s";
	public static final String WEBMARKET_APP_URL = "https://play.google.com/store/apps/details?id=%s";

	public static final int HTTP_TIMEOUT_MS = 15 * (int) DateUtils.SECOND_IN_MILLIS;
	public static final int PEER_TIMEOUT_MS = 15 * (int) DateUtils.SECOND_IN_MILLIS;

	public static final long LAST_USAGE_THRESHOLD_JUST_MS = DateUtils.HOUR_IN_MILLIS;
	public static final long LAST_USAGE_THRESHOLD_RECENTLY_MS = 2 * DateUtils.DAY_IN_MILLIS;

	public static final int SDK_JELLY_BEAN = 16;
	public static final int SDK_JELLY_BEAN_MR2 = 18;

	public static final int SDK_DEPRECATED_BELOW = Build.VERSION_CODES.ICE_CREAM_SANDWICH;

	public static final boolean BUG_OPENSSL_HEARTBLEED = Build.VERSION.SDK_INT == Constants.SDK_JELLY_BEAN
			&& Build.VERSION.RELEASE.startsWith("4.1.1");

	public static final int MEMORY_CLASS_LOWEND = 48;
}
