/*
 * Copyright 2016 the original author or authors.
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
package de.schildbach.wallet.ui.channels;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import de.schildbach.wallet.channels.PaymentChannelService;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet_test.R;

/**
 * A dialog that asks the user if they wish to accept the incrementing of a payment channel.
 */
public class ChannelIncrementActivity extends Activity implements View.OnClickListener {
    public static final String INTENT_EXTRA_AMOUNT =
            ChannelIncrementActivity.class.getCanonicalName() + ".amount";
    public static final String INTENT_EXTRA_PIN_WAS_INVALID =
            ChannelIncrementActivity.class.getCanonicalName() + ".pin_was_invalid";
    public static final String INTENT_EXTRA_CALLER_PACKAGE =
            ChannelIncrementActivity.class.getCanonicalName() + ".caller_package";
    public static final String INTENT_EXTRA_CHANNEL_ID =
            ChannelIncrementActivity.class.getCanonicalName() + ".channel_id";
    public static final String INTENT_EXTRA_PASSWORD_REQUIRED =
            ChannelIncrementActivity.class.getCanonicalName() + ".password_required";
    public static final String INTENT_EXTRA_INCREMENT_ID =
            ChannelIncrementActivity.class.getCanonicalName() + ".increment_id";
    private static final Logger log = LoggerFactory.getLogger(ChannelIncrementActivity.class);

    private TextView channelDestinationTitle;
    private TextView channelIncrementDescription;
    private TextView channelIncrementPackageId;
    private CurrencyTextView channelAmount;
    private EditText channelPassword;
    private Button cancel, ok;
    private TextView badPassword;

    private int channelId;
    private boolean passwordRequired;

    private long pendingIncrementId;

    private boolean resultPassed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.channel_increment);

        channelAmount = (CurrencyTextView) findViewById(R.id.channel_amount);
        channelDestinationTitle = (TextView) findViewById(R.id.channel_destination_title);
        channelIncrementDescription = (TextView) findViewById(R.id.channel_increment_description);
        channelIncrementPackageId = (TextView) findViewById(R.id.channel_increment_package_id);
        cancel = (Button) findViewById(R.id.cancel);
        ok = (Button) findViewById(R.id.ok);
        badPassword = (TextView) findViewById(R.id.channel_bad_password);

        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);

        long amount = getIntent().getLongExtra(INTENT_EXTRA_AMOUNT, 0);
        if (amount == 0) {
            log.warn("ChannelIncrementActivity created with no amount set");
            finish();
            return;
        }

        String callerPackage = getIntent().getStringExtra(INTENT_EXTRA_CALLER_PACKAGE);
        CharSequence appName;
        if (callerPackage == null) {
            appName = "";
            callerPackage = "an unknown application";
        } else {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(callerPackage, PackageManager.GET_META_DATA);
                appName = getPackageManager().getApplicationLabel(info.applicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                appName = "";
            }
        }

        channelId = getIntent().getIntExtra(INTENT_EXTRA_CHANNEL_ID, -1);
        if (channelId == -1) {
            finish();
            return;
        }

        pendingIncrementId = getIntent().getLongExtra(INTENT_EXTRA_INCREMENT_ID, -1);

        passwordRequired = getIntent().getBooleanExtra(INTENT_EXTRA_PASSWORD_REQUIRED, true);
        channelPassword = (EditText) findViewById(R.id.channel_password);
        channelPassword.setVisibility(passwordRequired ? View.VISIBLE : View.GONE);
        channelPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                ok.setEnabled(!passwordRequired || s.length() > 0);
            }
        });
        ok.setEnabled(!passwordRequired);

        if (getIntent().getBooleanExtra(INTENT_EXTRA_PIN_WAS_INVALID, false)) {
            badPassword.setVisibility(View.VISIBLE);
        }

        channelDestinationTitle.setText(appName);
        channelAmount.setFormat(MonetaryFormat.MBTC);
        channelAmount.setAmount(Coin.valueOf(amount));
        channelAmount.setAlwaysSigned(false);
        channelIncrementDescription.setText(getString(
                R.string.channel_increment_description,
                Coin.valueOf(amount).toFriendlyString(),
                appName
        ));
        channelIncrementPackageId.setText(getString(
                R.string.channel_create_package_id,
                callerPackage
        ));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancel:
                notifyService(false, channelId, null);
                finish();
                break;
            case R.id.ok:
                final String password = passwordRequired ? channelPassword.getText().toString() : null;
                notifyService(true, channelId, password);
                finish();
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private boolean notifyService(boolean confirm, int id, @Nullable String password) {
        Intent intent = new Intent(PaymentChannelService.BROADCAST_CONFIRM_INCREMENT);
        intent.putExtra(PaymentChannelService.BROADCAST_CONFIRM_INCREMENT_EXTRA_PASSWORD, password);
        intent.putExtra(PaymentChannelService.BROADCAST_CONFIRM_INCREMENT_EXTRA_CHANNEL_ID, id);
        intent.putExtra(PaymentChannelService.BROADCAST_CONFIRM_INCREMENT_EXTRA_CONFIRMED, confirm);
        intent.putExtra(PaymentChannelService.BROADCAST_CONFIRM_INCREMENT_EXTRA_INCREMENT_ID, pendingIncrementId);
        resultPassed = true;
        return LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
