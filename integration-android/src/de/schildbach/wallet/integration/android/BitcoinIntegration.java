/**
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.integration.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/**
 * @author Andreas Schildbach
 */
public final class BitcoinIntegration
{
	private static final String INTENT_EXTRA_TRANSACTION_HASH = "transaction_hash";

	/**
	 * Request any amount of Bitcoins (probably a donation) from user, without feedback from the app.
	 * 
	 * @param context
	 *            Android context
	 * @param address
	 *            Bitcoin address
	 */
	public static void request(final Context context, final String address)
	{
		final Intent intent = makeIntent(address, null);

		start(context, intent);
	}

	/**
	 * Request specific amount of Bitcoins from user, without feedback from the app.
	 * 
	 * @param context
	 *            Android context
	 * @param address
	 *            Bitcoin address
	 * @param amount
	 *            Bitcoin amount in nanocoins
	 */
	public static void request(final Context context, final String address, final long amount)
	{
		final Intent intent = makeIntent(address, amount);

		start(context, intent);
	}

	/**
	 * Request any amount of Bitcoins (probably a donation) from user, with feedback from the app. Result intent can be
	 * received by overriding {@link android.app.Activity#onActivityResult()}. Result indicates either
	 * {@link Activity#RESULT_OK} or {@link Activity#RESULT_CANCELED}. In the success case, use
	 * {@link #transactionHashFromResult(Intent)} to read the transaction hash from the intent.
	 * 
	 * Warning: A success indication is no guarantee! To be on the safe side, you must drive your own Bitcoin
	 * infrastructure and validate the transaction.
	 * 
	 * @param context
	 *            Android context
	 * @param address
	 *            Bitcoin address
	 */
	public static void requestForResult(final Activity activity, final int requestCode, final String address)
	{
		final Intent intent = makeIntent(address, null);

		startForResult(activity, requestCode, intent);
	}

	/**
	 * Request specific amount of Bitcoins from user, with feedback from the app. Result intent can be received by
	 * overriding {@link android.app.Activity#onActivityResult()}. Result indicates either {@link Activity#RESULT_OK} or
	 * {@link Activity#RESULT_CANCELED}. In the success case, use {@link #transactionHashFromResult(Intent)} to read the
	 * transaction hash from the intent.
	 * 
	 * Warning: A success indication is no guarantee! To be on the safe side, you must drive your own Bitcoin
	 * infrastructure and validate the transaction.
	 * 
	 * @param context
	 *            Android context
	 * @param address
	 *            Bitcoin address
	 */
	public static void requestForResult(final Activity activity, final int requestCode, final String address, final long amount)
	{
		final Intent intent = makeIntent(address, amount);

		startForResult(activity, requestCode, intent);
	}

	/**
	 * Put transaction hash into result intent. Meant for usage by Bitcoin wallet applications.
	 * 
	 * @param result
	 *            result intent
	 * @param txHash
	 *            transaction hash
	 */
	public static void transactionHashToResult(final Intent result, final String txHash)
	{
		result.putExtra(INTENT_EXTRA_TRANSACTION_HASH, txHash);
		result.putExtra(INTENT_EXTRA_TRANSACTION_HASH_OLD, txHash);
	}

	/**
	 * Get transaction hash from result intent. Meant for usage by applications initiating a Bitcoin payment.
	 * 
	 * You can use this hash to request the transaction from the Bitcoin network, in order to validate. For this, you
	 * need your own Bitcoin infrastructure though. There is no guarantee that the transaction has ever been broadcasted
	 * to the Bitcoin network.
	 * 
	 * @param result
	 *            result intent
	 * @return transaction hash
	 */
	public static String transactionHashFromResult(final Intent result)
	{
		final String txHash = result.getStringExtra(INTENT_EXTRA_TRANSACTION_HASH);

		return txHash;
	}

	private static final int NANOCOINS_PER_COIN = 100000000;

	private static Intent makeIntent(final String address, final Long amount)
	{
		final StringBuilder uri = new StringBuilder("litecoin:");
		if (address != null)
			uri.append(address);
		if (amount != null)
			uri.append("?amount=").append(String.format("%d.%08d", amount / NANOCOINS_PER_COIN, amount % NANOCOINS_PER_COIN));

		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));

		return intent;
	}

	private static void start(final Context context, final Intent intent)
	{
		final PackageManager pm = context.getPackageManager();
		if (pm.resolveActivity(intent, 0) != null)
			context.startActivity(intent);
		else
			redirectToDownload(context);
	}

	private static void startForResult(final Activity activity, final int requestCode, final Intent intent)
	{
		final PackageManager pm = activity.getPackageManager();
		if (pm.resolveActivity(intent, 0) != null)
			activity.startActivityForResult(intent, requestCode);
		else
			redirectToDownload(activity);
	}

	private static void redirectToDownload(final Context context)
	{
		Toast.makeText(context, "No Litecoin application found.\nPlease install Litecoin Wallet.", Toast.LENGTH_LONG).show();

		final Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=de.schildbach.wallet"));
		final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://code.google.com/p/bitcoin-wallet/downloads/list"));

		final PackageManager pm = context.getPackageManager();
		if (pm.resolveActivity(marketIntent, 0) != null)
			context.startActivity(marketIntent);
		else if (pm.resolveActivity(binaryIntent, 0) != null)
			context.startActivity(binaryIntent);
		// else out of luck
	}

	private static final String INTENT_EXTRA_TRANSACTION_HASH_OLD = "transaction_id";
}
