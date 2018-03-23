/*
 * Copyright the original author or authors.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.R;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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
    private WalletActivity activity;
    @Nullable
    private NfcAdapter nfcAdapter;

    private ImageView currentAddressQrView;
    private CardView currentAddressQrCardView;

    private WalletAddressViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(WalletAddressFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (WalletActivity) context;
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this).get(WalletAddressViewModel.class);
        viewModel.qrCode.observe(this, new Observer<Bitmap>() {
            @Override
            public void onChanged(final Bitmap qrCode) {
                final BitmapDrawable qrDrawable = new BitmapDrawable(getResources(), qrCode);
                qrDrawable.setFilterBitmap(false);
                currentAddressQrView.setImageDrawable(qrDrawable);
                currentAddressQrCardView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        final Address address = viewModel.currentAddress.getValue();
                        WalletAddressDialogFragment.show(getFragmentManager(), address, viewModel.ownName.getValue());
                        log.info("Current address enlarged: {}", address);
                    }
                });
            }
        });
        viewModel.bitcoinUri.observe(this, new Observer<Uri>() {
            @Override
            public void onChanged(final Uri bitcoinUri) {
                final NfcAdapter nfcAdapter = WalletAddressFragment.this.nfcAdapter;
                if (nfcAdapter != null)
                    nfcAdapter.setNdefPushMessage(createNdefMessage(bitcoinUri.toString()), activity);
                ViewModelProviders.of(activity).get(WalletActivity.ViewModel.class).addressLoadingFinished();
            }
        });
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.wallet_address_fragment, container, false);
        currentAddressQrView = (ImageView) view.findViewById(R.id.bitcoin_address_qr);

        currentAddressQrCardView = (CardView) view.findViewById(R.id.bitcoin_address_qr_card);
        currentAddressQrCardView.setCardBackgroundColor(Color.WHITE);
        currentAddressQrCardView.setPreventCornerOverlap(false);

        return view;
    }

    private static NdefMessage createNdefMessage(final String uri) {
        if (uri != null)
            return new NdefMessage(new NdefRecord[] { NdefRecord.createUri(uri) });
        else
            return null;
    }
}
