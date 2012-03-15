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

import java.math.BigInteger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.CurrencyAmountView.Listener;
import de.schildbach.wallet.util.ActionBarFragment;
import de.schildbach.wallet.util.NfcTools;
import de.schildbach.wallet.util.QrDialog;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class RequestCoinsFragment extends Fragment
{
	private WalletApplication application;
	private Object nfcManager;

	private ImageView qrView;
	private Bitmap qrCodeBitmap;
	private CurrencyAmountView amountView;
	private View nfcEnabledView;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		nfcManager = getActivity().getSystemService(Context.NFC_SERVICE);

		application = (WalletApplication) getActivity().getApplication();

		final View view = inflater.inflate(R.layout.request_coins_fragment, container);

		qrView = (ImageView) view.findViewById(R.id.request_coins_qr);
		qrView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				new QrDialog(getActivity(), qrCodeBitmap).show();
			}
		});

		amountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount);
		amountView.setListener(new Listener()
		{
			public void changed()
			{
				updateView();
			}

			public void done()
			{
			}
		});
		amountView.setContextButton(R.drawable.ic_input_calculator, new OnClickListener()
		{
			public void onClick(final View v)
			{
				final FragmentTransaction ft = getFragmentManager().beginTransaction();
				final Fragment prev = getFragmentManager().findFragmentByTag(AmountCalculatorFragment.FRAGMENT_TAG);
				if (prev != null)
					ft.remove(prev);
				ft.addToBackStack(null);
				final DialogFragment newFragment = new AmountCalculatorFragment(new AmountCalculatorFragment.Listener()
				{
					public void use(final BigInteger amount)
					{
						amountView.setAmount(amount);
					}
				});
				newFragment.show(ft, AmountCalculatorFragment.FRAGMENT_TAG);
			}
		});

		nfcEnabledView = view.findViewById(R.id.request_coins_fragment_nfc_enabled);

		return view;
	}

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		final ActionBarFragment actionBar = ((AbstractWalletActivity) activity).getActionBar();

		actionBar.addButton(R.drawable.ic_action_share).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(Intent.createChooser(
						new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, determineAddressStr()).setType("text/plain"), getActivity()
								.getString(R.string.request_coins_share_dialog_title)));
			}
		});

		actionBar.addButton(R.drawable.ic_action_copy).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final ClipboardManager clipboardManager = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
				final String addressStr = determineAddressStr();
				clipboardManager.setText(addressStr);
				((AbstractWalletActivity) getActivity()).toast(R.string.request_coins_clipboard_msg);

				System.out.println("bitcoin request uri: " + addressStr + (Constants.TEST ? " [testnet]" : ""));
			}
		});
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

	private void updateView()
	{
		final String addressStr = determineAddressStr();

		if (qrCodeBitmap != null)
			qrCodeBitmap.recycle();

		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		qrCodeBitmap = WalletUtils.getQRCodeBitmap(addressStr, size);
		qrView.setImageBitmap(qrCodeBitmap);

		if (nfcManager != null)
		{
			final boolean success = NfcTools.publishUri(nfcManager, getActivity(), addressStr);
			if (success)
				nfcEnabledView.setVisibility(View.VISIBLE);
		}
	}

	private String determineAddressStr()
	{
		final Address address = application.determineSelectedAddress();
		final BigInteger amount = amountView.getAmount();

		return BitcoinURI.convertToBitcoinURI(address, amount, null, null).toString();
	}
}
