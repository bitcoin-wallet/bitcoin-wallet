/*
 * Copyright 2011-2015 the original author or authors.
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

package de.schildbach.wallet.ui.send;

import javax.annotation.Nullable;

import org.bitcoinj.core.Coin;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.HelpDialogFragment;
import de.schildbach.wallet.R;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsActivity extends AbstractBindServiceActivity {
    public static final String INTENT_EXTRA_PAYMENT_INTENT = "payment_intent";
    public static final String INTENT_EXTRA_FEE_CATEGORY = "fee_category";

    public static void start(final Context context, final PaymentIntent paymentIntent,
            final @Nullable FeeCategory feeCategory, final int intentFlags) {
        final Intent intent = new Intent(context, SendCoinsActivity.class);
        intent.putExtra(INTENT_EXTRA_PAYMENT_INTENT, paymentIntent);
        if (feeCategory != null)
            intent.putExtra(INTENT_EXTRA_FEE_CATEGORY, feeCategory);
        if (intentFlags != 0)
            intent.setFlags(intentFlags);
        context.startActivity(intent);
    }

    public static void start(final Context context, final PaymentIntent paymentIntent) {
        start(context, paymentIntent, null, 0);
    }

    public static void startDonate(final Context context, final Coin amount, final @Nullable FeeCategory feeCategory,
            final int intentFlags) {
        start(context, PaymentIntent.from(Constants.DONATION_ADDRESS,
                context.getString(R.string.wallet_donate_address_label), amount), feeCategory, intentFlags);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.send_coins_content);

        getWalletApplication().startBlockchainService(false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.send_coins_activity_options, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;

        case R.id.send_coins_options_help:
            HelpDialogFragment.page(getFragmentManager(), R.string.help_send_coins);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
