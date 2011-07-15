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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.bitcoin.core.Address;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class RequestCoinsFragment extends Fragment
{
	private Application application;

	private ImageView qrView;
	private EditText amountView;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		application = (Application) getActivity().getApplication();

		final View view = inflater.inflate(R.layout.request_coins_fragment, container);

		qrView = (ImageView) view.findViewById(R.id.request_coins_qr);
		qrView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final ClipboardManager clipboardManager = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
				final String addressStr = determineAddressStr();
				clipboardManager.setText(addressStr);
				((AbstractWalletActivity) getActivity()).toast(R.string.request_coins_clipboard_msg);

				System.out.println("bitcoin request uri: " + addressStr + (Constants.TEST ? " (testnet!)" : ""));
			}
		});

		amountView = (EditText) view.findViewById(R.id.request_coins_amount);
		final float density = getResources().getDisplayMetrics().density;
		amountView.setCompoundDrawablesWithIntrinsicBounds(new BtcDrawable(24f * density, 10.5f * density), null, null, null);
		amountView.addTextChangedListener(new TextWatcher()
		{
			public void afterTextChanged(final Editable s)
			{
				updateView();
			}

			public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
			{
			}

			public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
			{
			}
		});

		updateView();

		return view;
	}

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		final ActionBarFragment actionBar = (ActionBarFragment) getFragmentManager().findFragmentById(R.id.action_bar_fragment);
		actionBar.addButton(R.drawable.ic_menu_share).setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(Intent.createChooser(
						new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, determineAddressStr()).setType("text/plain"), getActivity()
								.getString(R.string.request_coins_share_dialog_title)));
			}
		});
	}

	private void updateView()
	{
		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		qrView.setImageBitmap(WalletUtils.getQRCodeBitmap(determineAddressStr(), size));
	}

	private String determineAddressStr()
	{
		final Address address = application.determineSelectedAddress();

		final StringBuilder builder = new StringBuilder("bitcoin:");
		builder.append(address.toString());
		if (amountView.getText().length() > 0)
			builder.append("?amount=").append(amountView.getText());

		return builder.toString();
	}
}
