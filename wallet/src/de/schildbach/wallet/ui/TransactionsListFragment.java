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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.util.CircularProgressView;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class TransactionsListFragment extends SherlockListFragment implements LoaderCallbacks<List<Transaction>>
{
	public enum Direction
	{
		RECEIVED, SENT
	}

	private AbstractWalletActivity activity;
	private ContentResolver resolver;
	private SharedPreferences prefs;

	private WalletApplication application;
	private Wallet wallet;
	private ArrayAdapter<Transaction> adapter;

	private Direction direction;

	private int bestChainHeight;

	private final Handler handler = new Handler();

	private final Map<String, String> labelCache = new HashMap<String, String>();
	private final static String NULL_MARKER = "";

	private final static String KEY_DIRECTION = "direction";

	public static TransactionsListFragment instance(final Direction direction)
	{
		final TransactionsListFragment fragment = new TransactionsListFragment();

		final Bundle args = new Bundle();
		args.putSerializable(KEY_DIRECTION, direction);
		fragment.setArguments(args);

		return fragment;
	}

	private final ContentObserver addressBookObserver = new ContentObserver(handler)
	{
		@Override
		public void onChange(final boolean selfChange)
		{
			labelCache.clear();

			adapter.notifyDataSetChanged();
		}
	};

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			bestChainHeight = intent.getIntExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_HEIGHT, 0);

			adapter.notifyDataSetChanged();
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		resolver = activity.getContentResolver();
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		application = (WalletApplication) activity.getApplication();
		wallet = application.getWallet();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setRetainInstance(true);

		this.direction = (Direction) getArguments().getSerializable(KEY_DIRECTION);

		adapter = new ArrayAdapter<Transaction>(activity, 0)
		{
			private final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);
			private final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);
			private final int colorSignificant = getResources().getColor(R.color.fg_significant);
			private final int colorInsignificant = getResources().getColor(R.color.fg_insignificant);
			private final LayoutInflater inflater = getLayoutInflater(null);

			private static final String CONFIDENCE_SYMBOL_NOT_IN_BEST_CHAIN = "!";
			private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
			private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

			@Override
			public View getView(final int position, View row, final ViewGroup parent)
			{
				if (row == null)
					row = inflater.inflate(R.layout.transaction_row, null);

				final Transaction tx = getItem(position);
				final TransactionConfidence confidence = tx.getConfidence();
				final ConfidenceType confidenceType = confidence.getConfidenceType();

				try
				{
					final BigInteger value = tx.getValue(wallet);
					final boolean sent = value.signum() < 0;

					final CircularProgressView rowConfidenceCircular = (CircularProgressView) row
							.findViewById(R.id.transaction_row_confidence_circular);
					rowConfidenceCircular.setMaxProgress(Constants.MAX_NUM_CONFIRMATIONS);
					final TextView rowConfidenceTextual = (TextView) row.findViewById(R.id.transaction_row_confidence_textual);

					final int textColor;
					if (confidenceType == ConfidenceType.NOT_SEEN_IN_CHAIN)
					{
						rowConfidenceCircular.setVisibility(View.VISIBLE);
						rowConfidenceTextual.setVisibility(View.GONE);
						textColor = colorInsignificant;

						rowConfidenceCircular.setProgress(0);
					}
					else if (confidenceType == ConfidenceType.BUILDING)
					{
						rowConfidenceCircular.setVisibility(View.VISIBLE);
						rowConfidenceTextual.setVisibility(View.GONE);
						textColor = colorSignificant;

						if (bestChainHeight > 0)
						{
							final int depth = bestChainHeight - confidence.getAppearedAtChainHeight() + 1;
							rowConfidenceCircular.setProgress(depth > 0 ? depth : 0);
						}
						else
						{
							rowConfidenceCircular.setProgress(0);
						}
					}
					else if (confidenceType == ConfidenceType.NOT_IN_BEST_CHAIN)
					{
						rowConfidenceCircular.setVisibility(View.GONE);
						rowConfidenceTextual.setVisibility(View.VISIBLE);
						textColor = colorSignificant;

						rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_NOT_IN_BEST_CHAIN);
						rowConfidenceTextual.setTextColor(Color.RED);
					}
					else if (confidenceType == ConfidenceType.DEAD)
					{
						rowConfidenceCircular.setVisibility(View.GONE);
						rowConfidenceTextual.setVisibility(View.VISIBLE);
						textColor = Color.RED;

						rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_DEAD);
						rowConfidenceTextual.setTextColor(Color.RED);
					}
					else
					{
						rowConfidenceCircular.setVisibility(View.GONE);
						rowConfidenceTextual.setVisibility(View.VISIBLE);
						textColor = colorInsignificant;

						rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_UNKNOWN);
						rowConfidenceTextual.setTextColor(colorInsignificant);
					}

					final TextView rowTime = (TextView) row.findViewById(R.id.transaction_row_time);
					final Date time = tx.getUpdateTime();
					rowTime.setText(time != null ? (DateUtils.isToday(time.getTime()) ? timeFormat.format(time) : dateFormat.format(time)) : null);
					rowTime.setTextColor(textColor);

					final TextView rowFromTo = (TextView) row.findViewById(R.id.transaction_row_fromto);
					rowFromTo.setText(sent ? R.string.symbol_to : R.string.symbol_from);
					rowFromTo.setTextColor(textColor);

					final TextView rowAddress = (TextView) row.findViewById(R.id.transaction_row_address);
					final Address address = sent ? getToAddress(tx) : getFromAddress(tx);
					final String label;
					if (tx.isCoinBase())
						label = getString(R.string.wallet_transactions_fragment_coinbase);
					else if (address != null)
						label = resolveLabel(address.toString());
					else
						label = "?";
					rowAddress.setTextColor(textColor);
					rowAddress.setText(label != null ? label : address.toString());
					rowAddress.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

					final CurrencyAmountView rowValue = (CurrencyAmountView) row.findViewById(R.id.transaction_row_value);
					rowValue.setCurrencyCode(null);
					rowValue.setAmountSigned(true);
					rowValue.setTextColor(textColor);
					rowValue.setAmount(value);

					return row;
				}
				catch (final ScriptException x)
				{
					throw new RuntimeException(x);
				}
			}
		};
		setListAdapter(adapter);

		activity.getContentResolver().registerContentObserver(AddressBookProvider.CONTENT_URI, true, addressBookObserver);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		final ListView listView = getListView();

		// workaround for flashing background in ViewPager on Android 2.x
		listView.setBackgroundColor(getResources().getColor(R.color.bg_bright));

		setEmptyText(getString(direction == Direction.SENT ? R.string.wallet_transactions_fragment_empty_text_sent
				: R.string.wallet_transactions_fragment_empty_text_received));
	}

	@Override
	public void onResume()
	{
		super.onResume();

		activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
	}

	@Override
	public void onPause()
	{
		activity.unregisterReceiver(broadcastReceiver);

		super.onPause();
	}

	@Override
	public void onDestroyView()
	{
		labelCache.clear();

		super.onDestroyView();
	}

	@Override
	public void onDestroy()
	{
		activity.getContentResolver().unregisterContentObserver(addressBookObserver);

		getLoaderManager().destroyLoader(0);

		super.onDestroy();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final Transaction tx = (Transaction) adapter.getItem(position);

		activity.startActionMode(new ActionMode.Callback()
		{
			private Address address;

			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.wallet_transactions_context, menu);
				menu.findItem(R.id.wallet_transactions_context_show_transaction).setVisible(
						prefs.getBoolean(Constants.PREFS_KEY_LABS_TRANSACTION_DETAILS, false));

				return true;
			}

			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				try
				{
					final Date time = tx.getUpdateTime();
					final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);
					final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);

					mode.setTitle(time != null ? (DateUtils.isToday(time.getTime()) ? getString(R.string.time_today) : dateFormat.format(time))
							+ ", " + timeFormat.format(time) : null);

					final BigInteger value = tx.getValue(wallet);
					final boolean sent = value.signum() < 0;

					address = sent ? getToAddress(tx) : getFromAddress(tx);

					final String label;
					if (tx.isCoinBase())
						label = getString(R.string.wallet_transactions_fragment_coinbase);
					else if (address != null)
						label = resolveLabel(address.toString());
					else
						label = "?";

					final String prefix = getString(sent ? R.string.symbol_to : R.string.symbol_from) + " ";

					mode.setSubtitle(label != null ? prefix + label : WalletUtils.formatAddress(prefix, address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
							Constants.ADDRESS_FORMAT_LINE_SIZE));

					menu.findItem(R.id.wallet_transactions_context_edit_address).setVisible(address != null);

					return true;
				}
				catch (final ScriptException x)
				{
					return false;
				}
			}

			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.wallet_transactions_context_edit_address:
						handleEditAddress(tx);

						mode.finish();
						return true;

					case R.id.wallet_transactions_context_show_transaction:
						TransactionActivity.show(activity, tx);

						mode.finish();
						return true;
				}
				return false;
			}

			public void onDestroyActionMode(final ActionMode mode)
			{
			}

			private void handleEditAddress(final Transaction tx)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}
		});
	}

	private Address getFromAddress(final Transaction tx)
	{
		try
		{
			for (final TransactionInput input : tx.getInputs())
			{
				return input.getFromAddress();
			}

			throw new IllegalStateException();
		}
		catch (final ScriptException x)
		{
			// this will happen on inputs connected to coinbase transactions
			return null;
		}
	}

	private Address getToAddress(final Transaction tx)
	{
		try
		{
			for (final TransactionOutput output : tx.getOutputs())
			{
				return output.getScriptPubKey().getToAddress();
			}

			throw new IllegalStateException();
		}
		catch (final ScriptException x)
		{
			return null;
		}
	}

	private String resolveLabel(final String address)
	{
		final String cachedLabel = labelCache.get(address);
		if (cachedLabel == null)
		{
			final String label = AddressBookProvider.resolveLabel(resolver, address);
			if (label != null)
				labelCache.put(address, label);
			else
				labelCache.put(address, NULL_MARKER);
			return label;
		}
		else
		{
			return cachedLabel != NULL_MARKER ? cachedLabel : null;
		}
	}

	public Loader<List<Transaction>> onCreateLoader(final int id, final Bundle args)
	{
		return new TransactionsLoader(activity, wallet);
	}

	public void onLoadFinished(final Loader<List<Transaction>> loader, final List<Transaction> transactions)
	{
		adapter.clear();

		try
		{
			for (final Transaction tx : transactions)
			{
				final boolean sent = tx.getValue(wallet).signum() < 0;
				if ((direction == Direction.RECEIVED && !sent) || direction == null || (direction == Direction.SENT && sent))
					adapter.add(tx);
			}
		}
		catch (final ScriptException x)
		{
			throw new RuntimeException(x);
		}
	}

	public void onLoaderReset(final Loader<List<Transaction>> loader)
	{
		adapter.clear();
	}

	private static class TransactionsLoader extends AsyncTaskLoader<List<Transaction>>
	{
		private final Wallet wallet;

		private TransactionsLoader(final Context context, final Wallet wallet)
		{
			super(context);

			this.wallet = wallet;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			wallet.addEventListener(walletEventListener);

			forceLoad();
		}

		@Override
		protected void onStopLoading()
		{
			wallet.removeEventListener(walletEventListener);

			super.onStopLoading();
		}

		@Override
		public List<Transaction> loadInBackground()
		{
			final List<Transaction> transactions = new ArrayList<Transaction>(wallet.getTransactions(true, false));

			Collections.sort(transactions, TRANSACTION_COMPARATOR);

			return transactions;
		}

		private final WalletEventListener walletEventListener = new AbstractWalletEventListener()
		{
			@Override
			public void onChange()
			{
				forceLoad();
			}
		};

		private static final Comparator<Transaction> TRANSACTION_COMPARATOR = new Comparator<Transaction>()
		{
			public int compare(final Transaction tx1, final Transaction tx2)
			{
				final boolean pending1 = tx1.getConfidence().getConfidenceType() == ConfidenceType.NOT_SEEN_IN_CHAIN;
				final boolean pending2 = tx2.getConfidence().getConfidenceType() == ConfidenceType.NOT_SEEN_IN_CHAIN;

				if (pending1 != pending2)
					return pending1 ? -1 : 1;

				final Date updateTime1 = tx1.getUpdateTime();
				final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
				final Date updateTime2 = tx2.getUpdateTime();
				final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;

				if (time1 > time2)
					return -1;
				else if (time1 < time2)
					return 1;
				else
					return 0;
			}
		};
	}
}
