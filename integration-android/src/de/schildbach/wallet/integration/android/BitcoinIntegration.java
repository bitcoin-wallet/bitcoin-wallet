/*
 * Copyright the original author or authors.
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
public final class BitcoinIntegration {
    private static final String INTENT_EXTRA_PAYMENTREQUEST = "paymentrequest";
    private static final String INTENT_EXTRA_PAYMENT = "payment";
    private static final String INTENT_EXTRA_TRANSACTION_HASH = "transaction_hash";

    private static final String MIMETYPE_PAYMENTREQUEST = "application/bitcoin-paymentrequest"; // BIP 71

    /**
     * Request any amount of Bitcoins (probably a donation) from user, without feedback from the app.
     * 
     * @param context
     *            Android context
     * @param address
     *            Bitcoin address
     */
    public static void request(final Context context, final String address) {
        final Intent intent = makeBitcoinUriIntent(address, null);

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
     *            Bitcoin amount in satoshis
     */
    public static void request(final Context context, final String address, final long amount) {
        final Intent intent = makeBitcoinUriIntent(address, amount);

        start(context, intent);
    }

    /**
     * Request payment from user, without feedback from the app.
     * 
     * @param context
     *            Android context
     * @param paymentRequest
     *            BIP70 formatted payment request
     */
    public static void request(final Context context, final byte[] paymentRequest) {
        final Intent intent = makePaymentRequestIntent(paymentRequest);

        start(context, intent);
    }

    /**
     * Request any amount of Bitcoins (probably a donation) from user, with feedback from the app. Result
     * intent can be received by overriding {@code Activity#onActivityResult(int, int, Intent)}. Result indicates
     * either {@link Activity#RESULT_OK} or {@link Activity#RESULT_CANCELED}. In the success case, use
     * {@link #transactionHashFromResult(Intent)} to read the transaction hash from the intent.
     * 
     * Warning: A success indication is no guarantee! To be on the safe side, you must drive your own Bitcoin
     * infrastructure and validate the transaction.
     * 
     * @param activity
     *            Calling Android activity
     * @param requestCode
     *            Code identifying the call when {@code Activity#onActivityResult(int, int, Intent)} is called
     *            back
     * @param address
     *            Bitcoin address
     */
    public static void requestForResult(final Activity activity, final int requestCode, final String address) {
        final Intent intent = makeBitcoinUriIntent(address, null);

        startForResult(activity, requestCode, intent);
    }

    /**
     * Request specific amount of Bitcoins from user, with feedback from the app. Result intent can be
     * received by overriding {@code Activity#onActivityResult(int, int, Intent)}. Result indicates either
     * {@link Activity#RESULT_OK} or {@link Activity#RESULT_CANCELED}. In the success case, use
     * {@link #transactionHashFromResult(Intent)} to read the transaction hash from the intent.
     * 
     * Warning: A success indication is no guarantee! To be on the safe side, you must drive your own Bitcoin
     * infrastructure and validate the transaction.
     * 
     * @param activity
     *            Calling Android activity
     * @param requestCode
     *            Code identifying the call when {@code Activity#onActivityResult(int, int, Intent)} is called
     *            back
     * @param address
     *            Bitcoin address
     */
    public static void requestForResult(final Activity activity, final int requestCode, final String address,
            final long amount) {
        final Intent intent = makeBitcoinUriIntent(address, amount);

        startForResult(activity, requestCode, intent);
    }

    /**
     * Request payment from user, with feedback from the app. Result intent can be received by overriding
     * {@code Activity#onActivityResult(int, int, Intent)}. Result indicates either {@link Activity#RESULT_OK} or
     * {@link Activity#RESULT_CANCELED}. In the success case, use {@link #transactionHashFromResult(Intent)}
     * to read the transaction hash from the intent.
     * 
     * Warning: A success indication is no guarantee! To be on the safe side, you must drive your own Bitcoin
     * infrastructure and validate the transaction.
     * 
     * @param activity
     *            Calling Android activity
     * @param requestCode
     *            Code identifying the call when {@code Activity#onActivityResult(int, int, Intent)} is called
     *            back
     * @param paymentRequest
     *            BIP70 formatted payment request
     */
    public static void requestForResult(final Activity activity, final int requestCode, final byte[] paymentRequest) {
        final Intent intent = makePaymentRequestIntent(paymentRequest);

        startForResult(activity, requestCode, intent);
    }

    /**
     * Get payment request from intent. Meant for usage by applications accepting payment requests.
     * 
     * @param intent
     *            intent
     * @return payment request or null
     */
    public static byte[] paymentRequestFromIntent(final Intent intent) {

        return intent.getByteArrayExtra(INTENT_EXTRA_PAYMENTREQUEST);
    }

    /**
     * Put BIP70 payment message into result intent. Meant for usage by Bitcoin wallet applications.
     * 
     * @param result
     *            result intent
     * @param payment
     *            payment message
     */
    public static void paymentToResult(final Intent result, final byte[] payment) {
        result.putExtra(INTENT_EXTRA_PAYMENT, payment);
    }

    /**
     * Get BIP70 payment message from result intent. Meant for usage by applications initiating a Bitcoin
     * payment.
     * 
     * You can use the transactions contained in the payment to validate the payment. For this, you need your
     * own Bitcoin infrastructure though. There is no guarantee that the payment will ever confirm.
     * 
     * @param result
     *            result intent
     * @return payment message
     */
    public static byte[] paymentFromResult(final Intent result) {

        return result.getByteArrayExtra(INTENT_EXTRA_PAYMENT);
    }

    /**
     * Put transaction hash into result intent. Meant for usage by Bitcoin wallet applications.
     * 
     * @param result
     *            result intent
     * @param txHash
     *            transaction hash
     */
    public static void transactionHashToResult(final Intent result, final String txHash) {
        result.putExtra(INTENT_EXTRA_TRANSACTION_HASH, txHash);
    }

    /**
     * Get transaction hash from result intent. Meant for usage by applications initiating a Bitcoin payment.
     * 
     * You can use this hash to request the transaction from the Bitcoin network, in order to validate. For
     * this, you need your own Bitcoin infrastructure though. There is no guarantee that the transaction has
     * ever been broadcasted to the Bitcoin network.
     * 
     * @param result
     *            result intent
     * @return transaction hash
     */
    public static String transactionHashFromResult(final Intent result) {

        return result.getStringExtra(INTENT_EXTRA_TRANSACTION_HASH);
    }

    private static final int SATOSHIS_PER_COIN = 100000000;

    private static Intent makeBitcoinUriIntent(final String address, final Long amount) {
        final StringBuilder uri = new StringBuilder("bitcoin:");
        if (address != null)
            uri.append(address);
        if (amount != null)
            uri.append("?amount=")
                    .append(String.format("%d.%08d", amount / SATOSHIS_PER_COIN, amount % SATOSHIS_PER_COIN));

        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));
    }

    private static Intent makePaymentRequestIntent(final byte[] paymentRequest) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setType(MIMETYPE_PAYMENTREQUEST);
        intent.putExtra(INTENT_EXTRA_PAYMENTREQUEST, paymentRequest);

        return intent;
    }

    private static void start(final Context context, final Intent intent) {
        final PackageManager pm = context.getPackageManager();
        if (pm.resolveActivity(intent, 0) != null)
            context.startActivity(intent);
        else
            redirectToDownload(context);
    }

    private static void startForResult(final Activity activity, final int requestCode, final Intent intent) {
        final PackageManager pm = activity.getPackageManager();
        if (pm.resolveActivity(intent, 0) != null)
            activity.startActivityForResult(intent, requestCode);
        else
            redirectToDownload(activity);
    }

    private static void redirectToDownload(final Context context) {
        Toast.makeText(context, "No Bitcoin application found.\nPlease install Bitcoin Wallet.", Toast.LENGTH_LONG)
                .show();

        final Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=de.schildbach.wallet"));
        final Intent binaryIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/bitcoin-wallet/bitcoin-wallet/releases"));

        final PackageManager pm = context.getPackageManager();
        if (pm.resolveActivity(marketIntent, 0) != null)
            context.startActivity(marketIntent);
        else if (pm.resolveActivity(binaryIntent, 0) != null)
            context.startActivity(binaryIntent);
        // else out of luck
    }
}
