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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = WalletAddressDialogFragment.class.getName();

    private ImageView imageView;
    private TextView labelView;
    private WalletAddressViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(WalletAddressDialogFragment.class);

    public static void show(final FragmentManager fm) {
        instance().show(fm, FRAGMENT_TAG);
    }

    private static WalletAddressDialogFragment instance() {
        return new WalletAddressDialogFragment();
    }

    private Activity activity;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("opening dialog {}", getClass().getName());

        viewModel = new ViewModelProvider(getParentFragment()).get(WalletAddressViewModel.class);
        viewModel.qrCode.observe(this, qrCode -> {
            final BitmapDrawable qrDrawable = new BitmapDrawable(getResources(), qrCode);
            qrDrawable.setFilterBitmap(false);
            imageView.setImageDrawable(qrDrawable);
        });
        viewModel.currentAddress.observe(this, currentAddress -> {
            final CharSequence label = WalletUtils.formatAddress(currentAddress, Constants.ADDRESS_FORMAT_GROUP_SIZE,
                    Constants.ADDRESS_FORMAT_LINE_SIZE);
            labelView.setText(label);
        });
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.wallet_address_dialog);
        dialog.setCanceledOnTouchOutside(true);

        imageView = dialog.findViewById(R.id.wallet_address_dialog_image);
        labelView = dialog.findViewById(R.id.wallet_address_dialog_label);

        final View labelButtonView = dialog.findViewById(R.id.wallet_address_dialog_label_button);
        labelButtonView.setVisibility(View.VISIBLE);
        labelButtonView.setOnClickListener(v -> {
            final Address address = viewModel.currentAddress.getValue();
            if (address != null) {
                final ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity);
                builder.setType("text/plain");
                builder.setText(address.toString());
                builder.setChooserTitle(R.string.bitmap_fragment_share);
                builder.startChooser();
                log.info("wallet address shared via intent: {}", address.toString());
            }
        });

        final View hintView = dialog.findViewById(R.id.wallet_address_dialog_hint);
        hintView.setVisibility(
                getResources().getBoolean(R.bool.show_wallet_address_dialog_hint) ? View.VISIBLE : View.GONE);

        final View dialogView = dialog.findViewById(R.id.wallet_address_dialog_group);
        dialogView.setOnClickListener(v -> dismissAllowingStateLoss());

        return dialog;
    }
}
