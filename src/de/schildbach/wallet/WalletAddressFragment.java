/*
 * Copyright 2010 the original author or authors.
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

package de.schildbach.wallet;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressFragment extends Fragment
{
	private Bitmap qrCodeBitmap;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_address_fragment, container, false);

		final Application application = (Application) getActivity().getApplication();
		final Wallet wallet = application.getWallet();

		System.out.println(wallet.keychain.size() + " key(s) in keychain");
		final ECKey key = wallet.keychain.get(0);
		final Address address = key.toAddress(application.getNetworkParameters());

		final TextView bitcoinAddressView = (TextView) view.findViewById(R.id.bitcoin_address);
		bitcoinAddressView.setText(WalletUtils.splitIntoLines(address.toString(), 3));

		final ImageView bitcoinAddressQrView = (ImageView) view.findViewById(R.id.bitcoin_address_qr);

		// populate qrcode representation of bitcoin address
		qrCodeBitmap = WalletUtils.getQRCodeBitmap("bitcoin:" + address.toString(), 256);
		bitcoinAddressQrView.setImageBitmap(qrCodeBitmap);

		bitcoinAddressView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				ClipboardManager clipboardManager = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
				clipboardManager.setText(address.toString());
				((AbstractWalletActivity) getActivity()).toast("Bitcoin address pasted to clipboard");

				System.out.println("my bitcoin address: " + address + (application.isTest() ? " (testnet!)" : ""));
			}
		});

		bitcoinAddressView.setOnLongClickListener(new OnLongClickListener()
		{
			public boolean onLongClick(final View v)
			{
				startActivity(Intent.createChooser(
						new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "bitcoin:" + address).setType("text/plain"),
						"Share your bitcoin address..."));
				return false;
			}
		});

		bitcoinAddressQrView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final Dialog dialog = new Dialog(getActivity());
				dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
				dialog.setContentView(R.layout.bitcoin_address_qr_dialog);
				final ImageView imageView = (ImageView) dialog.findViewById(R.id.bitcoin_address_qr);
				imageView.setImageBitmap(qrCodeBitmap);
				dialog.setCanceledOnTouchOutside(true);
				dialog.show();
				imageView.setOnClickListener(new OnClickListener()
				{
					public void onClick(final View v)
					{
						dialog.dismiss();
					}
				});
			}
		});

		return view;
	}

	@Override
	public void onDestroyView()
	{
		if (qrCodeBitmap != null)
		{
			qrCodeBitmap.recycle();
			qrCodeBitmap = null;
		}

		super.onDestroyView();
	}
}
