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

import static com.google.common.base.Preconditions.checkState;

/**
 * A dialog that asks the user if they wish to accept the creation of a payment channel from the wallet.
 */
public class ChannelCreateActivity extends Activity implements View.OnClickListener {
    public static final String INTENT_EXTRA_AMOUNT =
            ChannelCreateActivity.class.getCanonicalName() + ".amount";
    public static final String INTENT_EXTRA_TIME_WINDOW =
            ChannelCreateActivity.class.getCanonicalName() + ".time_window";
    public static final String INTENT_EXTRA_CALLER_PACKAGE =
            ChannelCreateActivity.class.getCanonicalName() + ".caller_package";
    public static final String INTENT_EXTRA_CHANNEL_ID =
            ChannelCreateActivity.class.getCanonicalName() + ".channel_id";
    public static final String INTENT_EXTRA_PASSWORD_REQUIRED =
            ChannelCreateActivity.class.getCanonicalName() + ".password_required";
    private static final Logger log = LoggerFactory.getLogger(ChannelCreateActivity.class);

    private TextView channelDestinationTitle;
    private TextView channelCreateTimeWindow;
    private TextView channelCreateDescription;
    private TextView channelCreatePackageId;
    private CurrencyTextView channelAmount;
    private EditText channelPassword;
    private Button cancel, ok;

    private int channelId;
    private boolean passwordRequired;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.channel_create);

        channelAmount = (CurrencyTextView) findViewById(R.id.channel_amount);
        channelDestinationTitle = (TextView) findViewById(R.id.channel_destination_title);
        channelCreateTimeWindow = (TextView) findViewById(R.id.channel_create_time_window);
        channelCreateDescription = (TextView) findViewById(R.id.channel_create_description);
        channelCreatePackageId = (TextView) findViewById(R.id.channel_create_package_id);
        cancel = (Button) findViewById(R.id.cancel);
        ok = (Button) findViewById(R.id.ok);

        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);

        long amount = getIntent().getLongExtra(INTENT_EXTRA_AMOUNT, 0);
        if (amount == 0) {
            log.warn("ChannelCreateActivity created with no amount set");
            finish();
            return;
        }

        long timeWindow = getIntent().getLongExtra(INTENT_EXTRA_TIME_WINDOW, 0);
        if (timeWindow == 0) {
            log.warn("ChannelCreateActivity created with no time window set");
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

        long hours = Math.round((double)timeWindow / 60. / 60.);
        String timeString = hours < 24 ?
                getString(R.string.period_hours, hours) :
                getString(R.string.period_days, Math.round((double)hours / 24.0));

        passwordRequired = getIntent().getBooleanExtra(INTENT_EXTRA_PASSWORD_REQUIRED, true);
        channelPassword = (EditText) findViewById(R.id.channel_password);
        channelPassword.setVisibility(passwordRequired ? View.VISIBLE : View.GONE);
        channelPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                ok.setEnabled(!passwordRequired || s.length() > 0);
            }
        });
        ok.setEnabled(!passwordRequired);

        channelDestinationTitle.setText(appName);
        channelAmount.setFormat(MonetaryFormat.MBTC);
        channelAmount.setAmount(Coin.valueOf(amount));
        channelAmount.setAlwaysSigned(false);
        channelCreateTimeWindow.setText(getString(
                R.string.channel_create_time_window,
                timeString
        ));
        channelCreateDescription.setText(getString(
                R.string.channel_create_description,
                Coin.valueOf(amount).toFriendlyString(),
                timeString,
                appName
        ));
        channelCreatePackageId.setText(getString(
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

    private void notifyService(boolean confirm, int id, @Nullable String password) {
        Intent intent = new Intent(PaymentChannelService.BROADCAST_CONFIRM_CHANNEL);
        intent.putExtra(PaymentChannelService.BROADCAST_CONFIRM_CHANNEL_EXTRA_PASSWORD, password);
        intent.putExtra(PaymentChannelService.BROADCAST_CONFIRM_CHANNEL_EXTRA_CHANNEL_ID, id);
        intent.putExtra(PaymentChannelService.BROADCAST_CONFIRM_CHANNEL_EXTRA_CONFIRMED, confirm);
        checkState(LocalBroadcastManager.getInstance(this).sendBroadcast(intent));
    }
}
