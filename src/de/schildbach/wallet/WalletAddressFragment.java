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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressFragment extends Fragment
{
	private Application application;
	private final Handler handler = new Handler();

	private TextView bitcoinAddressView;
	private ImageView bitcoinAddressQrView;

	private Bitmap qrCodeBitmap;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_address_fragment, container, false);
		bitcoinAddressView = (TextView) view.findViewById(R.id.bitcoin_address);
		bitcoinAddressQrView = (ImageView) view.findViewById(R.id.bitcoin_address_qr);

		application = (Application) getActivity().getApplication();

		bitcoinAddressView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				showAllAddresses();
			}
		});

		bitcoinAddressView.setOnLongClickListener(new OnLongClickListener()
		{
			public boolean onLongClick(final View v)
			{
				final Address address = application.determineSelectedAddress();

				System.out.println("selected bitcoin address: " + address + (Constants.TEST ? " (testnet!)" : ""));

				new AlertDialog.Builder(getActivity()).setItems(R.array.wallet_address_fragment_context, new DialogInterface.OnClickListener()
				{
					public void onClick(final DialogInterface dialog, final int which)
					{
						if (which == 0)
							showAllAddresses();
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
				ClipboardManager clipboardManager = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
				clipboardManager.setText(address);
				((AbstractWalletActivity) getActivity()).toast(R.string.wallet_address_fragment_clipboard_msg);
			}

			private void share(final String address)
			{
				startActivity(Intent.createChooser(
						new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "bitcoin:" + address).setType("text/plain"),
						getString(R.string.wallet_address_fragment_share_dialog_title)));
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

		bitcoinAddressView.setText(WalletUtils.splitIntoLines(selectedAddress.toString(), 3));

		// populate qrcode representation of bitcoin address
		qrCodeBitmap = WalletUtils.getQRCodeBitmap("bitcoin:" + selectedAddress, 256);
		bitcoinAddressQrView.setImageBitmap(qrCodeBitmap);
	}

	private Runnable resetColorRunnable = new Runnable()
	{
		public void run()
		{
			bitcoinAddressView.setTextColor(Color.BLACK);
		}
	};

	public void flashAddress()
	{
		bitcoinAddressView.setTextColor(Color.parseColor("#cc5500"));
		handler.removeCallbacks(resetColorRunnable);
		handler.postDelayed(resetColorRunnable, 500);
	}

	private void showAllAddresses()
	{
		final FragmentManager fm = getFragmentManager();
		final FragmentTransaction ft = fm.beginTransaction();
		ft.hide(fm.findFragmentById(R.id.wallet_balance_fragment));
		ft.hide(fm.findFragmentById(R.id.wallet_transactions_fragment));
		ft.show(fm.findFragmentById(R.id.wallet_addresses_fragment));
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		ft.addToBackStack(null);
		ft.commit();
	}

	private void showQRCode()
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
}
