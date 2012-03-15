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
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.util.CircularProgressView;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletTransactionsFragment extends Fragment
{
	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_transactions_fragment, container, false);

		final ViewPagerTabs pagerTabs = (ViewPagerTabs) view.findViewById(R.id.transactions_pager_tabs);
		pagerTabs.addTabLabels(R.string.wallet_transactions_fragment_tab_received, R.string.wallet_transactions_fragment_tab_all,
				R.string.wallet_transactions_fragment_tab_sent);

		final PagerAdapter pagerAdapter = new PagerAdapter(getFragmentManager());

		final ViewPager pager = (ViewPager) view.findViewById(R.id.transactions_pager);
		pager.setAdapter(pagerAdapter);
		pager.setOnPageChangeListener(pagerTabs);
		pager.setCurrentItem(1);
		pager.setPageMargin(2);
		pager.setPageMarginDrawable(R.color.background_less_bright);
		pagerTabs.onPageScrolled(1, 0, 0); // should not be needed

		return view;
	}

	private static class PagerAdapter extends FragmentStatePagerAdapter
	{
		public PagerAdapter(final FragmentManager fm)
		{
			super(fm);
		}

		@Override
		public int getCount()
		{
			return 3;
		}

		@Override
		public Fragment getItem(final int position)
		{
			return ListFragment.instance(position);
		}
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

				final long time1 = tx1.getUpdateTime() != null ? tx1.getUpdateTime().getTime() : 0;
				final long time2 = tx2.getUpdateTime() != null ? tx2.getUpdateTime().getTime() : 0;

				if (time1 != time2)
					return time1 > time2 ? -1 : 1;

				return 0;
			}
		};
	}

	public static class ListFragment extends android.support.v4.app.ListFragment implements LoaderCallbacks<List<Transaction>>
	{
		private WalletApplication application;
		private Wallet wallet;
		private Activity activity;
		private ArrayAdapter<Transaction> adapter;

		private int mode;

		private int bestChainHeight;

		private final Handler handler = new Handler();

		private final static String KEY_MODE = "mode";

		public static ListFragment instance(final int mode)
		{
			final ListFragment fragment = new ListFragment();

			final Bundle args = new Bundle();
			args.putInt(KEY_MODE, mode);
			fragment.setArguments(args);

			return fragment;
		}

		private final ContentObserver contentObserver = new ContentObserver(handler)
		{
			@Override
			public void onChange(final boolean selfChange)
			{
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

			this.activity = activity;
			application = (WalletApplication) activity.getApplication();
			wallet = application.getWallet();
		}

		@Override
		public void onCreate(final Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			this.mode = getArguments().getInt(KEY_MODE);

			adapter = new ArrayAdapter<Transaction>(activity, 0)
			{
				final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);
				final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);
				final int colorSignificant = getResources().getColor(R.color.significant);
				final int colorInsignificant = getResources().getColor(R.color.insignificant);

				@Override
				public View getView(final int position, View row, final ViewGroup parent)
				{
					if (row == null)
						row = getLayoutInflater(null).inflate(R.layout.transaction_row, null);

					final Transaction tx = getItem(position);
					final TransactionConfidence confidence = tx.getConfidence();
					final ConfidenceType confidenceType = confidence.getConfidenceType();

					try
					{
						final BigInteger value = tx.getValue(wallet);
						final boolean sent = value.signum() < 0;

						final CircularProgressView rowConfidenceCircular = (CircularProgressView) row
								.findViewById(R.id.transaction_row_confidence_circular);
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

							rowConfidenceTextual.setText("!");
							rowConfidenceTextual.setTextColor(Color.RED);
						}
						else if (confidenceType == ConfidenceType.OVERRIDDEN_BY_DOUBLE_SPEND)
						{
							rowConfidenceCircular.setVisibility(View.GONE);
							rowConfidenceTextual.setVisibility(View.VISIBLE);
							textColor = Color.RED;

							rowConfidenceTextual.setText("\u271D"); // latin cross
							rowConfidenceTextual.setTextColor(Color.RED);
						}
						else
						{
							rowConfidenceCircular.setVisibility(View.GONE);
							rowConfidenceTextual.setVisibility(View.VISIBLE);
							textColor = colorInsignificant;

							rowConfidenceTextual.setText("?");
							rowConfidenceTextual.setTextColor(colorInsignificant);
						}

						final String address;
						if (sent)
							address = tx.getOutputs().get(0).getScriptPubKey().getToAddress().toString();
						else
							address = tx.getInputs().get(0).getFromAddress().toString();

						final String label = AddressBookProvider.resolveLabel(activity.getContentResolver(), address);

						final TextView rowTime = (TextView) row.findViewById(R.id.transaction_row_time);
						final Date time = tx.getUpdateTime();
						rowTime.setText(time != null ? (DateUtils.isToday(time.getTime()) ? timeFormat.format(time) : dateFormat.format(time)) : null);
						rowTime.setTextColor(textColor);

						final TextView rowTo = (TextView) row.findViewById(R.id.transaction_row_to);
						rowTo.setVisibility(sent ? View.VISIBLE : View.INVISIBLE);
						rowTo.setTextColor(textColor);

						final TextView rowFrom = (TextView) row.findViewById(R.id.transaction_row_from);
						rowFrom.setVisibility(sent ? View.INVISIBLE : View.VISIBLE);
						rowFrom.setTextColor(textColor);

						final TextView rowLabel = (TextView) row.findViewById(R.id.transaction_row_address);
						rowLabel.setTextColor(textColor);
						rowLabel.setText(label != null ? label : address);
						rowLabel.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

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

			activity.getContentResolver().registerContentObserver(AddressBookProvider.CONTENT_URI, true, contentObserver);
		}

		@Override
		public void onActivityCreated(final Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);

			getLoaderManager().initLoader(0, null, this);
		}

		@Override
		public void onViewCreated(final View view, final Bundle savedInstanceState)
		{
			super.onViewCreated(view, savedInstanceState);

			setEmptyText(getString(mode == 2 ? R.string.wallet_transactions_fragment_empty_text_sent
					: R.string.wallet_transactions_fragment_empty_text_received));

			registerForContextMenu(getListView());
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
		public void onDestroy()
		{
			activity.getContentResolver().unregisterContentObserver(contentObserver);

			getLoaderManager().destroyLoader(0);

			super.onDestroy();
		}

		@Override
		public void onListItemClick(final ListView l, final View v, final int position, final long id)
		{
			final Transaction tx = (Transaction) adapter.getItem(position);
			editAddress(tx);
		}

		// workaround http://code.google.com/p/android/issues/detail?id=20065
		private static View lastContextMenuView;

		@Override
		public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo)
		{
			activity.getMenuInflater().inflate(R.menu.wallet_transactions_context, menu);

			lastContextMenuView = v;
		}

		@Override
		public boolean onContextItemSelected(final MenuItem item)
		{
			final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
			final ListAdapter adapter = ((ListView) lastContextMenuView).getAdapter();
			final Transaction tx = (Transaction) adapter.getItem(menuInfo.position);

			switch (item.getItemId())
			{
				case R.id.wallet_transactions_context_edit_address:
					editAddress(tx);
					return true;

				case R.id.wallet_transactions_context_show_transaction:
					TransactionActivity.show(activity, tx);
					return true;

				default:
					return false;
			}
		}

		private void editAddress(final Transaction tx)
		{
			try
			{
				final boolean sent = tx.getValue(wallet).signum() < 0;
				final Address address = sent ? tx.getOutputs().get(0).getScriptPubKey().getToAddress() : tx.getInputs().get(0).getFromAddress();

				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}
			catch (final ScriptException x)
			{
				// ignore click
				x.printStackTrace();
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
					if ((mode == 0 && !sent) || mode == 1 || (mode == 2 && sent))
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
	}
}
