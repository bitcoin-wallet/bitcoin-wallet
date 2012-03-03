/*
 * Copyright 2015 the original author or authors.
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

import org.bitcoinj.core.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = WalletAddressDialogFragment.class.getName();

	private static final String KEY_BITMAP = "bitmap";
	private static final String KEY_ADDRESS = "address";

	private static final Logger log = LoggerFactory.getLogger(WalletAddressDialogFragment.class);

	public static void show(final FragmentManager fm, final Bitmap bitmap, final Address address)
	{
		instance(bitmap, address).show(fm, FRAGMENT_TAG);
	}

	private static WalletAddressDialogFragment instance(final Bitmap bitmap, final Address address)
	{
		final WalletAddressDialogFragment fragment = new WalletAddressDialogFragment();

		final Bundle args = new Bundle();
		args.putParcelable(KEY_BITMAP, bitmap);
		args.putString(KEY_ADDRESS, address.toBase58());
		fragment.setArguments(args);

		return fragment;
	}

	private Activity activity;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Bundle args = getArguments();
		final Bitmap bitmap = (Bitmap) args.getParcelable(KEY_BITMAP);
		final String address = args.getString(KEY_ADDRESS);

		final Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.wallet_address_dialog);
		dialog.setCanceledOnTouchOutside(true);

		final ImageView imageView = (ImageView) dialog.findViewById(R.id.wallet_address_dialog_image);
		imageView.setImageBitmap(bitmap);

		final View labelButtonView = dialog.findViewById(R.id.wallet_address_dialog_label_button);
		final TextView labelView = (TextView) dialog.findViewById(R.id.wallet_address_dialog_label);
		final CharSequence label = WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE);
		labelView.setText(label);
		labelButtonView.setVisibility(View.VISIBLE);
		labelButtonView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, address);
				startActivity(Intent.createChooser(intent, getString(R.string.bitmap_fragment_share)));
				log.info("wallet address shared via intent: {}", address);
			}
		});

		final View hintView = dialog.findViewById(R.id.wallet_address_dialog_hint);
		hintView.setVisibility(getResources().getBoolean(R.bool.show_wallet_address_dialog_hint) ? View.VISIBLE : View.GONE);

		final View dialogView = dialog.findViewById(R.id.wallet_address_dialog_group);
		dialogView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				dismiss();
			}
		});

		return dialog;
	}
}
