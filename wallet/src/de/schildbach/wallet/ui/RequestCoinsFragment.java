/*
 * Copyright 2011-2014 the original author or authors.
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
import java.util.LinkedList;
import java.util.List;

import javax.annotation.CheckForNull;

import de.schildbach.wallet.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.ShareCompat.IntentBuilder;
import android.support.v4.content.Loader;
import android.text.SpannableStringBuilder;
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
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.offline.AcceptBluetoothService;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class RequestCoinsFragment extends SherlockFragment
{
	private AbstractBindServiceActivity activity;
	private WalletApplication application;
	private Wallet wallet;
	private SharedPreferences prefs;
	private NfcManager nfcManager;
	private LoaderManager loaderManager;
	private AbstractClipboardManager clipboardManager;
	private ShareActionProvider shareActionProvider;
	@CheckForNull
	private BluetoothAdapter bluetoothAdapter;

	private int btcPrecision;
	private int btcShift;

	private ImageView qrView;
	private Bitmap qrCodeBitmap;
	private Spinner addressView;
	private CheckBox includeLabelView;
	private TextView initiateRequestView;
	private View bluetoothEnabledView;

	private String bluetoothMac;
	private Intent bluetoothServiceIntent;

	private static final int REQUEST_CODE_ENABLE_BLUETOOTH = 0;

	private CurrencyCalculatorLink amountCalculatorLink;

	private static final int ID_RATE_LOADER = 0;

	private static final Logger log = LoggerFactory.getLogger(RequestCoinsFragment.class);

	private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		@Override
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			return new ExchangeRateLoader(activity);
		}

		@Override
		public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
		{
			if (data != null)
			{
				data.moveToFirst();
				final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);

				amountCalculatorLink.setExchangeRate(exchangeRate);
				updateView();
			}
		}

		@Override
		public void onLoaderReset(final Loader<Cursor> loader)
		{
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractBindServiceActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.loaderManager = getLoaderManager();
		this.nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);
		this.clipboardManager = new AbstractClipboardManager(activity);
		this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
		btcPrecision = precision.charAt(0) - '0';
		btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.request_coins_fragment, container, false);

		qrView = (ImageView) view.findViewById(R.id.request_coins_qr);
		qrView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
			}
		});

		final CurrencyAmountView btcAmountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount_btc);
		btcAmountView.setCurrencySymbol(btcShift == 0 ? Constants.CURRENCY_CODE_BTC : Constants.CURRENCY_CODE_MBTC);
		btcAmountView.setInputPrecision(btcShift == 0 ? Constants.BTC_MAX_PRECISION : Constants.MBTC_MAX_PRECISION);
		btcAmountView.setHintPrecision(btcPrecision);
		btcAmountView.setShift(btcShift);

		final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount_local);
		localAmountView.setInputPrecision(Constants.LOCAL_PRECISION);
		localAmountView.setHintPrecision(Constants.LOCAL_PRECISION);
		amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);

		addressView = (Spinner) view.findViewById(R.id.request_coins_fragment_address);
		final List<ECKey> keys = new LinkedList<ECKey>();
		for (final ECKey key : application.getWallet().getKeys())
			if (!wallet.isKeyRotating(key))
				keys.add(key);
		final WalletAddressesAdapter adapter = new WalletAddressesAdapter(activity, wallet, false);
		adapter.replace(keys);
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

		initiateRequestView = (TextView) view.findViewById(R.id.request_coins_fragment_initiate_request);

		bluetoothEnabledView = view.findViewById(R.id.request_coins_fragment_bluetooth_enabled);

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

		amountCalculatorLink.setListener(new CurrencyAmountView.Listener()
		{
			@Override
			public void changed()
			{
				updateView();
				updateShareIntent();
			}

			@Override
			public void done()
			{
			}

			@Override
			public void focusChanged(final boolean hasFocus)
			{
			}
		});

		addressView.setOnItemSelectedListener(new OnItemSelectedListener()
		{
			@Override
			public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id)
			{
				// ignore layout operations
				if (view == null)
					return;

				updateView();
				updateShareIntent();
			}

			@Override
			public void onNothingSelected(final AdapterView<?> parent)
			{
			}
		});

		includeLabelView.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
			{
				updateView();
				updateShareIntent();
			}
		});

		loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

		final boolean labsBluetoothOfflineTransactions = prefs.getBoolean(Constants.PREFS_KEY_LABS_BLUETOOTH_OFFLINE_TRANSACTIONS, false);
		if (bluetoothAdapter != null && labsBluetoothOfflineTransactions)
			maybeInitBluetoothListening();

		updateView();
	}

	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_RATE_LOADER);

		Nfc.unpublish(nfcManager, activity);

		amountCalculatorLink.setListener(null);

		addressView.setOnItemSelectedListener(null);

		includeLabelView.setOnCheckedChangeListener(null);

		super.onPause();
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH && resultCode == Activity.RESULT_OK)
		{
			maybeInitBluetoothListening();

			if (isResumed())
				updateView();
		}
	}

	private void maybeInitBluetoothListening()
	{
		if (!bluetoothAdapter.isEnabled())
		{
			// try to enable bluetooth
			startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_ENABLE_BLUETOOTH);
		}
		else
		{
			bluetoothMac = Bluetooth.compressMac(bluetoothAdapter.getAddress());

			bluetoothServiceIntent = new Intent(activity, AcceptBluetoothService.class);
			activity.startService(bluetoothServiceIntent);
		}
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
		final String request = determineRequestStr(false);
		clipboardManager.setText("address", request);
		activity.toast(R.string.request_coins_clipboard_msg);
	}

	private void handleLocalApp()
	{
		final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(determineRequestStr(false)));
		startActivity(intent);
		activity.finish();
	}

	private void updateView()
	{
		if (!isResumed())
			return;

		final String request = determineRequestStr(true);

		// update qr code
		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		qrCodeBitmap = Qr.bitmap(request, size);
		qrView.setImageBitmap(qrCodeBitmap);

		// update ndef message
		final boolean nfcSuccess = Nfc.publishUri(nfcManager, getActivity(), request);

		// update initiate request message
		final SpannableStringBuilder initiateText = new SpannableStringBuilder(getString(R.string.request_coins_fragment_initiate_request_qr));
		if (nfcSuccess)
			initiateText.append(' ').append(getString(R.string.request_coins_fragment_initiate_request_nfc));
		initiateRequestView.setText(initiateText);

		// update bluetooth message
		final boolean serviceRunning = application.isServiceRunning(AcceptBluetoothService.class);
		bluetoothEnabledView.setVisibility(bluetoothAdapter != null && bluetoothAdapter.isEnabled() && serviceRunning ? View.VISIBLE : View.GONE);
	}

	private void updateShareIntent()
	{
		// update share intent
		final IntentBuilder builder = IntentBuilder.from(activity);
		builder.setText(determineRequestStr(false));
		builder.setType("text/plain");
		builder.setChooserTitle(R.string.request_coins_share_dialog_title);
		shareActionProvider.setShareIntent(builder.getIntent());
	}

	private String determineRequestStr(final boolean includeBluetoothMac)
	{
		final boolean includeLabel = includeLabelView.isChecked();

		final ECKey key = (ECKey) addressView.getSelectedItem();
		final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
		final String label = includeLabel ? AddressBookProvider.resolveLabel(activity, address.toString()) : null;
		final BigInteger amount = amountCalculatorLink.getAmount();

		final StringBuilder uri = new StringBuilder(BitcoinURI.convertToBitcoinURI(
                Constants.NETWORK_PARAMETERS, address, amount, label, null));
		if (includeBluetoothMac && bluetoothMac != null)
		{
			uri.append(amount == null && label == null ? '?' : '&');
			uri.append(Bluetooth.MAC_URI_PARAM).append('=').append(bluetoothMac);
		}
		return uri.toString();
	}
}
