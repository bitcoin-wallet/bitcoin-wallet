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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.bitcoinj.wallet.listeners.WalletReorganizeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.ThrottelingLiveData;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressFragment extends Fragment {
    private Activity activity;
    @Nullable
    private NfcAdapter nfcAdapter;

    private ImageView currentAddressQrView;

    private ViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(WalletAddressFragment.class);

    public static class ViewModel extends AndroidViewModel {
        private final WalletApplication application;
        private CurrentAddressLiveData currentAddress;

        public ViewModel(final Application application) {
            super(application);
            this.application = (WalletApplication) application;
        }

        public CurrentAddressLiveData getCurrentAddress() {
            if (currentAddress == null)
                currentAddress = new CurrentAddressLiveData(application);
            return currentAddress;
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (Activity) context;
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        viewModel.getCurrentAddress().observe(this, new Observer<CurrentAddressData>() {
            @Override
            public void onChanged(final CurrentAddressData currentAddress) {
                final BitmapDrawable qrDrawable = new BitmapDrawable(getResources(), currentAddress.getQrCode());
                qrDrawable.setFilterBitmap(false);
                currentAddressQrView.setImageDrawable(qrDrawable);
                final NfcAdapter nfcAdapter = WalletAddressFragment.this.nfcAdapter;
                if (nfcAdapter != null)
                    nfcAdapter.setNdefPushMessage(createNdefMessage(currentAddress.uri()), activity);
            }
        });
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.wallet_address_fragment, container, false);
        currentAddressQrView = (ImageView) view.findViewById(R.id.bitcoin_address_qr);

        final CardView currentAddressQrCardView = (CardView) view.findViewById(R.id.bitcoin_address_qr_card);
        currentAddressQrCardView.setCardBackgroundColor(Color.WHITE);
        currentAddressQrCardView.setPreventCornerOverlap(false);
        currentAddressQrCardView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleShowQRCode();
            }
        });

        return view;
    }

    private void handleShowQRCode() {
        final CurrentAddressData currentAddress = viewModel.getCurrentAddress().getValue();
        WalletAddressDialogFragment.show(getFragmentManager(), currentAddress.address, currentAddress.label);
        log.info("Current address enlarged: {}", currentAddress.address);
    }

    public static class CurrentAddressData {
        public final Address address;
        @Nullable
        public final String label;
        @Nullable
        private Bitmap qrCode;

        public CurrentAddressData(final Address address, final String label) {
            this.address = checkNotNull(address);
            this.label = label;
        }

        public String uri() {
            return BitcoinURI.convertToBitcoinURI(address, null, label, null);
        }

        public void generateQrCode() {
            qrCode = Qr.bitmap(uri());
        }

        public Bitmap getQrCode() {
            return qrCode;
        }
    }

    private static class CurrentAddressLiveData extends ThrottelingLiveData<CurrentAddressData> {
        private final LocalBroadcastManager broadcastManager;
        private final Wallet wallet;
        private final Configuration config;
        private final Handler handler = new Handler();

        public CurrentAddressLiveData(final WalletApplication application) {
            this.broadcastManager = LocalBroadcastManager.getInstance(application);
            this.wallet = application.getWallet();
            this.config = application.getConfiguration();
        }

        @Override
        protected void onActive() {
            wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addReorganizeEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addChangeEventListener(Threading.SAME_THREAD, walletListener);
            broadcastManager.registerReceiver(walletChangeReceiver,
                    new IntentFilter(WalletApplication.ACTION_WALLET_REFERENCE_CHANGED));
            config.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            load();
        }

        @Override
        protected void onInactive() {
            config.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
            broadcastManager.unregisterReceiver(walletChangeReceiver);
            wallet.removeChangeEventListener(walletListener);
            wallet.removeReorganizeEventListener(walletListener);
            wallet.removeCoinsSentEventListener(walletListener);
            wallet.removeCoinsReceivedEventListener(walletListener);
        }

        @Override
        protected void load() {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                    final Address address = wallet.currentReceiveAddress();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            final CurrentAddressData currentAddressData = new CurrentAddressData(address,
                                    config.getOwnName());
                            final CurrentAddressData previousAddressData = getValue();
                            if (previousAddressData != null
                                    && currentAddressData.address.equals(previousAddressData.address))
                                return;
                            setValue(currentAddressData);
                            AsyncTask.execute(new Runnable() {
                                @Override
                                public void run() {
                                    currentAddressData.generateQrCode();
                                    postValue(currentAddressData);
                                }
                            });
                        }
                    });
                }
            });
        }

        private final WalletListener walletListener = new WalletListener();

        private class WalletListener implements WalletCoinsReceivedEventListener, WalletCoinsSentEventListener,
                WalletReorganizeEventListener, WalletChangeEventListener {
            @Override
            public void onCoinsReceived(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                triggerLoad();
            }

            @Override
            public void onCoinsSent(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                triggerLoad();
            }

            @Override
            public void onReorganize(final Wallet wallet) {
                triggerLoad();
            }

            @Override
            public void onWalletChanged(final Wallet wallet) {
                triggerLoad();
            }
        }

        private final BroadcastReceiver walletChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                load();
            }
        };

        private final OnSharedPreferenceChangeListener preferenceChangeListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
                if (Configuration.PREFS_KEY_OWN_NAME.equals(key))
                    load();
            }
        };
    }

    private static NdefMessage createNdefMessage(final String uri) {
        if (uri != null)
            return new NdefMessage(new NdefRecord[] { NdefRecord.createUri(uri) });
        else
            return null;
    }
}
