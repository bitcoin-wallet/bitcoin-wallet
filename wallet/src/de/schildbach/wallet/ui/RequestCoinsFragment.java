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
import java.util.LinkedList;
import java.util.List;

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
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.uri.BitcoinURI;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class RequestCoinsFragment extends SherlockFragment
{
	private AbstractBindServiceActivity activity;
	private WalletApplication application;
	private Wallet wallet;
	private SharedPreferences prefs;
	private NfcManager nfcManager;
	private LoaderManager loaderManager;
	private ClipboardManager clipboardManager;
	private ShareActionProvider shareActionProvider;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothListenThread acceptThread;

	private int btcPrecision;

	private ImageView qrView;
	private Bitmap qrCodeBitmap;
	private Spinner addressView;
	private CheckBox includeLabelView;
	private View nfcEnabledView;
	private View bluetoothEnabledView;

	private String bluetoothMac;

	private static final int REQUEST_CODE_ENABLE_BLUETOOTH = 0;

	private CurrencyCalculatorLink amountCalculatorLink;

	private static final int ID_RATE_LOADER = 0;

	private static final Logger log = LoggerFactory.getLogger(RequestCoinsFragment.class);

	private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			return new ExchangeRateLoader(activity);
		}

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
		this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
		this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		btcPrecision = Integer.parseInt(prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION));
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

		final CurrencyAmountView btcAmountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount_btc);
		btcAmountView.setCurrencySymbol(Constants.CURRENCY_CODE_BITCOIN);
		btcAmountView.setHintPrecision(btcPrecision);

		final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount_local);
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

		nfcEnabledView = view.findViewById(R.id.request_coins_fragment_nfc_enabled);

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

		if (acceptThread != null)
		{
			acceptThread.stopAccepting();
			bluetoothEnabledView.setVisibility(View.GONE);
		}

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

			acceptThread = new BluetoothListenThread(bluetoothAdapter)
			{
				@Override
				public boolean handleTx(final byte[] msg)
				{
					try
					{
						final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, msg);
						log.info("tx " + tx.getHashAsString() + " arrived via blueooth");

						try
						{
							if (wallet.isTransactionRelevant(tx))
							{
								wallet.receivePending(tx, null);

								activity.runOnUiThread(new Runnable()
								{
									public void run()
									{
										activity.getBlockchainService().broadcastTransaction(tx);
									}
								});
							}
							else
							{
								log.info("tx " + tx.getHashAsString() + " irrelevant");
							}

							return true;
						}
						catch (final VerificationException x)
						{
							log.info("cannot verify tx " + tx.getHashAsString() + " received via bluetooth", x);
						}
					}
					catch (final ProtocolException x)
					{
						log.info("cannot decode message received via bluetooth", x);
					}

					return false;
				}
			};

			bluetoothEnabledView.setVisibility(View.VISIBLE);
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
		final String request = determineRequestStr() + (bluetoothMac != null ? "&" + Bluetooth.MAC_URI_PARAM + "=" + bluetoothMac : "");

		// update qr code
		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		qrCodeBitmap = Qr.bitmap(request, size);
		qrView.setImageBitmap(qrCodeBitmap);

		// update ndef message
		final boolean success = Nfc.publishUri(nfcManager, getActivity(), request);
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
		final BigInteger amount = amountCalculatorLink.getAmount();

		return BitcoinURI.convertToBitcoinURI(address, amount, label, null).toString();
	}
}
