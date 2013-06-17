/*
 * Copyright 2011-2013 the original author or authors.
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
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.support.v4.app.ShareCompat.IntentBuilder;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.Spinner;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.NfcTools;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class RequestCoinsFragment extends SherlockFragment implements AmountCalculatorFragment.Listener
{
	private AbstractWalletActivity activity;
	private WalletApplication application;
	private NfcManager nfcManager;
	private ClipboardManager clipboardManager;
	private ShareActionProvider shareActionProvider;

	private ImageView qrView;
	private Bitmap qrCodeBitmap;
	private CurrencyAmountView amountView;
	private Spinner addressView;
	private CheckBox includeLabelView;
	private View nfcEnabledView;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);
		this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.request_coins_fragment, container, false);

		qrView = (ImageView) view.findViewById(R.id.request_coins_qr);
		qrView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
			}
		});

		amountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount);
		amountView.setContextButton(R.drawable.ic_input_calculator, new OnClickListener()
		{
			public void onClick(final View v)
			{
				AmountCalculatorFragment.calculate(getFragmentManager(), RequestCoinsFragment.this);
			}
		});

		addressView = (Spinner) view.findViewById(R.id.request_coins_fragment_address);
		final List<ECKey> keys = application.getWallet().getKeys();
		final WalletAddressesAdapter adapter = new WalletAddressesAdapter(activity, keys, false);
		addressView.setAdapter(adapter);
		final Address selectedAddress = application.determineSelectedAddress();
		for (int i = 0; i < keys.size(); i++)
		{
			final Address address = keys.get(i).toAddress(Constants.NETWORK_PARAMETERS);
			if (address.equals(selectedAddress))
			{
				addressView.setSelection(i);
				break;
			}
		}

		includeLabelView = (CheckBox) view.findViewById(R.id.request_coins_fragment_include_label);

		nfcEnabledView = view.findViewById(R.id.request_coins_fragment_nfc_enabled);

		return view;
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		// don't call in onCreate() because ActionBarSherlock invokes onCreateOptionsMenu() too early
		setHasOptionsMenu(true);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		amountView.setListener(new CurrencyAmountView.Listener()
		{
			public void changed()
			{
				updateView();
				updateShareIntent();
			}

			public void done()
			{
			}

			public void focusChanged(final boolean hasFocus)
			{
			}
		});

		addressView.setOnItemSelectedListener(new OnItemSelectedListener()
		{
			public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id)
			{
				// ignore layout operations
				if (view == null)
					return;

				updateView();
				updateShareIntent();
			}

			public void onNothingSelected(final AdapterView<?> parent)
			{
			}
		});

		includeLabelView.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
			{
				updateView();
				updateShareIntent();
			}
		});

		updateView();
	}

	@Override
	public void onPause()
	{
		NfcTools.unpublish(nfcManager, activity);

		amountView.setListener(null);

		addressView.setOnItemSelectedListener(null);

		includeLabelView.setOnCheckedChangeListener(null);

		super.onPause();
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.request_coins_fragment_options, menu);

		final MenuItem shareItem = menu.findItem(R.id.request_coins_options_share);
		shareActionProvider = (ShareActionProvider) shareItem.getActionProvider();

		updateShareIntent();

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.request_coins_options_copy:
				handleCopy();
				return true;

			case R.id.request_coins_options_local_app:
				handleLocalApp();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleCopy()
	{
		final String request = determineRequestStr();
		clipboardManager.setText(request);
		activity.toast(R.string.request_coins_clipboard_msg);
	}

	private void handleLocalApp()
	{
		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(determineRequestStr()));
		startActivity(intent);
		activity.finish();
	}

	private void updateView()
	{
		final String request = determineRequestStr();

		// update qr code
		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		qrCodeBitmap = WalletUtils.getQRCodeBitmap(request, size);
		qrView.setImageBitmap(qrCodeBitmap);

		// update ndef message
		final boolean success = NfcTools.publishUri(nfcManager, getActivity(), request);
		if (success)
			nfcEnabledView.setVisibility(View.VISIBLE);
	}

	private void updateShareIntent()
	{
		// update share intent
		final IntentBuilder builder = IntentBuilder.from(activity);
		builder.setText(determineRequestStr());
		builder.setType("text/plain");
		builder.setChooserTitle(R.string.request_coins_share_dialog_title);
		shareActionProvider.setShareIntent(builder.getIntent());
	}

	private String determineRequestStr()
	{
		final boolean includeLabel = includeLabelView.isChecked();

		final ECKey key = (ECKey) addressView.getSelectedItem();
		final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
		final String label = includeLabel ? AddressBookProvider.resolveLabel(activity, address.toString()) : null;
		final BigInteger amount = amountView.getAmount();

		return BitcoinURI.convertToBitcoinURI(address, amount, label, null).toString();
	}

	public void useCalculatedAmount(final BigInteger amount)
	{
		amountView.setAmount(amount, true);
	}
}
