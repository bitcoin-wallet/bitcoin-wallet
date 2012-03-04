/*
 * Copyright 2011-2012 the original author or authors.
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.Application;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.NfcTools;
import de.schildbach.wallet.util.QrDialog;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressFragment extends Fragment
{
	private Application application;
	private Object nfcManager;

	private final Handler handler = new Handler();

	private View bitcoinAddressButton;
	private TextView bitcoinAddressLabel;
	private ImageView bitcoinAddressQrView;

	private Bitmap qrCodeBitmap;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final FragmentActivity activity = getActivity();

		nfcManager = activity.getSystemService(Context.NFC_SERVICE);

		application = (Application) activity.getApplication();

		final View view = inflater.inflate(R.layout.wallet_address_fragment, container, false);
		bitcoinAddressButton = view.findViewById(R.id.bitcoin_address_button);
		bitcoinAddressLabel = (TextView) view.findViewById(R.id.bitcoin_address_label);
		bitcoinAddressQrView = (ImageView) view.findViewById(R.id.bitcoin_address_qr);

		bitcoinAddressButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				AddressBookActivity.start(activity, false);
			}
		});

		bitcoinAddressButton.setOnLongClickListener(new OnLongClickListener()
		{
			public boolean onLongClick(final View v)
			{
				final Address address = application.determineSelectedAddress();

				System.out.println("selected bitcoin address: " + address + (Constants.TEST ? " [testnet]" : ""));

				new AlertDialog.Builder(activity).setItems(R.array.wallet_address_fragment_context, new DialogInterface.OnClickListener()
				{
					public void onClick(final DialogInterface dialog, final int which)
					{
						if (which == 0)
							AddressBookActivity.start(activity, false);
						else if (which == 1)
							showQRCode();
						else if (which == 2)
							copyToClipboard(address.toString());
						else if (which == 3)
							share(address.toString());
					}
				}).show();

				return true;
			}

			private void copyToClipboard(final String address)
			{
				ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
				clipboardManager.setText(address);
				((AbstractWalletActivity) activity).toast(R.string.wallet_address_fragment_clipboard_msg);
			}

			private void share(final String addressStr)
			{
				try
				{
					final Address address = new Address(Constants.NETWORK_PARAMETERS, addressStr);
					final Intent intent = new Intent(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_TEXT, BitcoinURI.convertToBitcoinURI(address, null, null, null));
					intent.setType("text/plain");
					startActivity(Intent.createChooser(intent, getString(R.string.wallet_address_fragment_share_dialog_title)));
				}
				catch (final AddressFormatException x)
				{
					throw new RuntimeException(x);
				}
			}
		});

		bitcoinAddressQrView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showQRCode();
			}
		});

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onPause()
	{
		super.onPause();

		if (nfcManager != null)
			NfcTools.unpublish(nfcManager, getActivity());
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

	public void updateView()
	{
		final Address selectedAddress = application.determineSelectedAddress();

		bitcoinAddressLabel.setText(WalletUtils.splitIntoLines(selectedAddress.toString(), 3));

		final String addressStr = BitcoinURI.convertToBitcoinURI(selectedAddress, null, null, null);

		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		qrCodeBitmap = WalletUtils.getQRCodeBitmap(addressStr, size);
		bitcoinAddressQrView.setImageBitmap(qrCodeBitmap);

		if (nfcManager != null)
			NfcTools.publishUri(nfcManager, getActivity(), addressStr);
	}

	private Runnable resetColorRunnable = new Runnable()
	{
		public void run()
		{
			bitcoinAddressLabel.setTextColor(Color.BLACK);
		}
	};

	public void flashAddress()
	{
		bitcoinAddressLabel.setTextColor(Color.parseColor("#cc5500"));
		handler.removeCallbacks(resetColorRunnable);
		handler.postDelayed(resetColorRunnable, 500);
	}

	private void showQRCode()
	{
		new QrDialog(getActivity(), qrCodeBitmap).show();
	}
}
