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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
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
	private TransactionsListAdapter adapter;

	private Direction direction;

	private final Handler handler = new Handler();

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
			adapter.clearLabelCache();
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

		adapter = new TransactionsListAdapter(activity, wallet);
		setListAdapter(adapter);

		activity.getContentResolver().registerContentObserver(AddressBookProvider.CONTENT_URI, true, addressBookObserver);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		setEmptyText(getString(direction == Direction.SENT ? R.string.wallet_transactions_fragment_empty_text_sent
				: R.string.wallet_transactions_fragment_empty_text_received));
	}

	@Override
	public void onDestroyView()
	{
		adapter.clearLabelCache();

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

					address = sent ? WalletUtils.getToAddress(tx) : WalletUtils.getFromAddress(tx);

					final String label;
					if (tx.isCoinBase())
						label = getString(R.string.wallet_transactions_fragment_coinbase);
					else if (address != null)
						label = AddressBookProvider.resolveLabel(resolver, address.toString());
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
			private final AtomicLong lastMessageTime = new AtomicLong(0);
			private static final int THROTTLE_MS = 200;
			private final Handler handler = new Handler();

			@Override
			public void onChange()
			{
				handler.removeCallbacksAndMessages(null);

				final long now = System.currentTimeMillis();

				if (now - lastMessageTime.get() > THROTTLE_MS)
					handler.post(runnable);
				else
					handler.postDelayed(runnable, THROTTLE_MS);
			}

			private final Runnable runnable = new Runnable()
			{
				public void run()
				{
					lastMessageTime.set(System.currentTimeMillis());

					forceLoad();
				}
			};
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
