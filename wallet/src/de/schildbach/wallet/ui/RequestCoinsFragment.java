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

package de.schildbach.wallet.ui;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.ExchangeRate;
import de.schildbach.wallet.data.ExchangeRateLiveData;
import de.schildbach.wallet.offline.AcceptBluetoothService;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ShareCompat;
import android.support.v7.widget.CardView;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public final class RequestCoinsFragment extends Fragment {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private ClipboardManager clipboardManager;
    @Nullable
    private BluetoothAdapter bluetoothAdapter;
    @Nullable
    private NfcAdapter nfcAdapter;

    private ImageView qrView;
    private BitmapDrawable qrCodeBitmap;
    private CheckBox acceptBluetoothPaymentView;
    private TextView initiateRequestView;

    @Nullable
    private String bluetoothMac;
    @Nullable
    private Intent bluetoothServiceIntent;

    private static final int REQUEST_CODE_ENABLE_BLUETOOTH = 0;

    private Address address;
    private CurrencyCalculatorLink amountCalculatorLink;

    private ViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(RequestCoinsFragment.class);

    public static class ViewModel extends AndroidViewModel {
        private final WalletApplication application;
        private ExchangeRateLiveData exchangeRate;

        public ViewModel(final Application application) {
            super(application);
            this.application = (WalletApplication) application;
        }

        public ExchangeRateLiveData getExchangeRate() {
            if (exchangeRate == null)
                exchangeRate = new ExchangeRateLiveData(application);
            return exchangeRate;
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        if (Constants.ENABLE_EXCHANGE_RATES) {
            viewModel.getExchangeRate().observe(this, new Observer<ExchangeRate>() {
                @Override
                public void onChanged(final ExchangeRate exchangeRate) {
                    amountCalculatorLink.setExchangeRate(exchangeRate.rate);
                    updateView();
                }
            });
        }

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            address = wallet.freshReceiveAddress();
            log.info("request coins started: {}", address);
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.request_coins_fragment, container, false);

        qrView = (ImageView) view.findViewById(R.id.request_coins_qr);

        final CardView qrCardView = (CardView) view.findViewById(R.id.request_coins_qr_card);
        qrCardView.setCardBackgroundColor(Color.WHITE);
        qrCardView.setPreventCornerOverlap(false);
        qrCardView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                BitmapFragment.show(getFragmentManager(), qrCodeBitmap.getBitmap());
            }
        });

        final CurrencyAmountView btcAmountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount_btc);
        btcAmountView.setCurrencySymbol(config.getFormat().code());
        btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
        btcAmountView.setHintFormat(config.getFormat());

        final CurrencyAmountView localAmountView = (CurrencyAmountView) view
                .findViewById(R.id.request_coins_amount_local);
        localAmountView.setInputFormat(Constants.LOCAL_FORMAT);
        localAmountView.setHintFormat(Constants.LOCAL_FORMAT);
        amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);

        final BluetoothAdapter bluetoothAdapter = this.bluetoothAdapter;
        acceptBluetoothPaymentView = (CheckBox) view.findViewById(R.id.request_coins_accept_bluetooth_payment);
        acceptBluetoothPaymentView.setVisibility(bluetoothAdapter != null ? View.VISIBLE : View.GONE);
        acceptBluetoothPaymentView.setChecked(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
        acceptBluetoothPaymentView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                if (bluetoothAdapter != null && isChecked) {
                    if (bluetoothAdapter.isEnabled()) {
                        maybeStartBluetoothListening();
                    } else {
                        // ask for permission to enable bluetooth
                        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                                REQUEST_CODE_ENABLE_BLUETOOTH);
                    }
                } else {
                    stopBluetoothListening();
                }

                updateView();
            }
        });

        initiateRequestView = (TextView) view.findViewById(R.id.request_coins_fragment_initiate_request);

        return view;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());
        amountCalculatorLink.requestFocus();
    }

    @Override
    public void onResume() {
        super.onResume();

        amountCalculatorLink.setListener(new CurrencyAmountView.Listener() {
            @Override
            public void changed() {
                updateView();
            }

            @Override
            public void focusChanged(final boolean hasFocus) {
            }
        });

        final BluetoothAdapter bluetoothAdapter = this.bluetoothAdapter;
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && acceptBluetoothPaymentView.isChecked())
            maybeStartBluetoothListening();

        updateView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
    }

    @Override
    public void onPause() {
        amountCalculatorLink.setListener(null);

        super.onPause();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        saveInstanceState(outState);
    }

    private void saveInstanceState(final Bundle outState) {
        outState.putByteArray("receive_address", address.getHash160());
    }

    private void restoreInstanceState(final Bundle savedInstanceState) {
        address = new Address(Constants.NETWORK_PARAMETERS, savedInstanceState.getByteArray("receive_address"));
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            boolean started = false;
            if (resultCode == Activity.RESULT_OK && bluetoothAdapter != null)
                started = maybeStartBluetoothListening();
            acceptBluetoothPaymentView.setChecked(started);

            if (isResumed())
                updateView();
        }
    }

    private boolean maybeStartBluetoothListening() {
        final String bluetoothAddress = Bluetooth.getAddress(bluetoothAdapter);
        if (bluetoothAddress != null) {
            bluetoothMac = Bluetooth.compressMac(bluetoothAddress);
            bluetoothServiceIntent = new Intent(activity, AcceptBluetoothService.class);
            activity.startService(bluetoothServiceIntent);
            return true;
        } else {
            return false;
        }
    }

    private void stopBluetoothListening() {
        if (bluetoothServiceIntent != null) {
            activity.stopService(bluetoothServiceIntent);
            bluetoothServiceIntent = null;
        }

        bluetoothMac = null;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.request_coins_fragment_options, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.request_coins_options_copy:
            handleCopy();
            return true;

        case R.id.request_coins_options_share:
            handleShare();
            return true;

        case R.id.request_coins_options_local_app:
            handleLocalApp();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void handleCopy() {
        final Uri request = Uri.parse(determineBitcoinRequestStr(false));
        clipboardManager.setPrimaryClip(ClipData.newRawUri("Bitcoin payment request", request));
        log.info("payment request copied to clipboard: {}", request);
        new Toast(activity).toast(R.string.request_coins_clipboard_msg);
    }

    private void handleShare() {
        final String request = determineBitcoinRequestStr(false);
        final ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity);
        builder.setType("text/plain");
        builder.setText(request);
        builder.setChooserTitle(R.string.request_coins_share_dialog_title);
        builder.startChooser();
        log.info("payment request shared via intent: {}", request);
    }

    private void handleLocalApp() {
        final ComponentName component = new ComponentName(activity, SendCoinsActivity.class);
        final PackageManager pm = activity.getPackageManager();
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(determineBitcoinRequestStr(false)));

        try {
            // launch intent chooser with ourselves excluded
            pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            startActivity(intent);
        } catch (final ActivityNotFoundException x) {
            new Toast(activity).longToast(R.string.request_coins_no_local_app_msg);
        } finally {
            pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }

        activity.finish();
    }

    private void updateView() {
        final String bitcoinRequest = determineBitcoinRequestStr(true);
        final byte[] paymentRequest = determinePaymentRequest(true);
        final NfcAdapter nfcAdapter = this.nfcAdapter;

        // update qr-code
        qrCodeBitmap = new BitmapDrawable(getResources(), Qr.bitmap(bitcoinRequest));
        qrCodeBitmap.setFilterBitmap(false);
        qrView.setImageDrawable(qrCodeBitmap);

        // update initiate request message
        final SpannableStringBuilder initiateText = new SpannableStringBuilder(
                getString(R.string.request_coins_fragment_initiate_request_qr));
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            initiateText.append(' ').append(getString(R.string.request_coins_fragment_initiate_request_nfc));
            nfcAdapter.setNdefPushMessage(createNdefMessage(paymentRequest), activity);
        }
        initiateRequestView.setText(initiateText);

        // focus linking
        final int activeAmountViewId = amountCalculatorLink.activeTextView().getId();
        acceptBluetoothPaymentView.setNextFocusUpId(activeAmountViewId);
    }

    private String determineBitcoinRequestStr(final boolean includeBluetoothMac) {
        final Coin amount = amountCalculatorLink.getAmount();
        final String ownName = config.getOwnName();

        final StringBuilder uri = new StringBuilder(BitcoinURI.convertToBitcoinURI(address, amount, ownName, null));
        if (includeBluetoothMac && bluetoothMac != null) {
            uri.append(amount == null && ownName == null ? '?' : '&');
            uri.append(Bluetooth.MAC_URI_PARAM).append('=').append(bluetoothMac);
        }
        return uri.toString();
    }

    private byte[] determinePaymentRequest(final boolean includeBluetoothMac) {
        final Coin amount = amountCalculatorLink.getAmount();
        final String paymentUrl = includeBluetoothMac && bluetoothMac != null ? "bt:" + bluetoothMac : null;

        return PaymentProtocol.createPaymentRequest(Constants.NETWORK_PARAMETERS, amount, address, config.getOwnName(),
                paymentUrl, null).build().toByteArray();
    }

    private static NdefMessage createNdefMessage(final byte[] paymentRequest) {
        if (paymentRequest != null)
            return new NdefMessage(
                    new NdefRecord[] { Nfc.createMime(PaymentProtocol.MIMETYPE_PAYMENTREQUEST, paymentRequest) });
        else
            return null;
    }
}
