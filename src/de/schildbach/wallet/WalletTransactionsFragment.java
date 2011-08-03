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

import java.math.BigInteger;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletTransactionsFragment extends Fragment
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

	public static class ListFragment extends android.support.v4.app.ListFragment
	{
		private Application application;

		private ArrayAdapter<Transaction> transactionsListAdapter;
		private int mode;

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

		private final WalletEventListener walletEventListener = new WalletEventListener()
		{
			@Override
			public void onPendingCoinsReceived(final Wallet wallet, final Transaction tx)
			{
				onEverything();
			}

			@Override
			public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
			{
				onEverything();
			}

			@Override
			public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
			{
				onEverything();
			}

			@Override
			public void onReorganize()
			{
				onEverything();
			}

			@Override
			public void onDeadTransaction(final Transaction deadTx, final Transaction replacementTx)
			{
				onEverything();
			}

			private void onEverything()
			{
				getActivity().runOnUiThread(new Runnable()
				{
					public void run()
					{
						updateView();
					}
				});
			}
		};

		private final ContentObserver contentObserver = new ContentObserver(handler)
		{
			@Override
			public void onChange(final boolean selfChange)
			{
				transactionsListAdapter.notifyDataSetChanged();
			}
		};

		private final OnItemClickListener itemClickListener = new OnItemClickListener()
		{
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id)
			{
				try
				{
					final Transaction tx = transactionsListAdapter.getItem(position);
					final boolean sent = tx.sent(application.getWallet());
					final Address address = sent ? tx.outputs.get(0).getScriptPubKey().getToAddress() : tx.getInputs().get(0).getFromAddress();

					System.out.println("clicked on tx " + tx.getHash());

					final FragmentTransaction ft = getFragmentManager().beginTransaction();
					final Fragment prev = getFragmentManager().findFragmentByTag(EditAddressBookEntryFragment.FRAGMENT_TAG);
					if (prev != null)
						ft.remove(prev);
					ft.addToBackStack(null);
					final DialogFragment newFragment = new EditAddressBookEntryFragment(getLayoutInflater(null), address.toString());
					newFragment.show(ft, EditAddressBookEntryFragment.FRAGMENT_TAG);
				}
				catch (final ScriptException x)
				{
					throw new RuntimeException(x);
				}
			}
		};

		@Override
		public void onCreate(final Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			this.mode = getArguments().getInt(KEY_MODE);

			application = (Application) getActivity().getApplication();
			final Wallet wallet = application.getWallet();

			transactionsListAdapter = new ArrayAdapter<Transaction>(getActivity(), 0)
			{
				final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(getActivity());
				final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getActivity());

				@Override
				public View getView(final int position, View row, final ViewGroup parent)
				{
					try
					{
						if (row == null)
							row = getLayoutInflater(null).inflate(R.layout.transaction_row, null);

						final Transaction tx = getItem(position);
						final boolean sent = tx.sent(wallet);
						final boolean pending = wallet.isPending(tx);
						final boolean dead = wallet.isDead(tx);
						final int textColor;
						if (dead)
							textColor = Color.RED;
						else if (pending)
							textColor = Color.LTGRAY;
						else
							textColor = Color.BLACK;
						final String address = (sent ? tx.outputs.get(0).getScriptPubKey().getToAddress() : tx.getInputs().get(0).getFromAddress())
								.toString();

						final Uri uri = AddressBookProvider.CONTENT_URI.buildUpon().appendPath(address).build();

						final String label;
						final Cursor cursor = getActivity().managedQuery(uri, null, null, null, null);
						if (cursor.moveToFirst())
							label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
						else
							label = null;

						final TextView rowTime = (TextView) row.findViewById(R.id.transaction_time);
						final Date time = tx.updatedAt;
						rowTime.setText(time != null ? DateUtils.isToday(time.getTime()) ? timeFormat.format(time) : dateFormat.format(time) : null);
						rowTime.setTextColor(textColor);

						final TextView rowTo = (TextView) row.findViewById(R.id.transaction_to);
						rowTo.setVisibility(sent ? View.VISIBLE : View.INVISIBLE);
						rowTo.setTextColor(textColor);

						final TextView rowFrom = (TextView) row.findViewById(R.id.transaction_from);
						rowFrom.setVisibility(sent ? View.INVISIBLE : View.VISIBLE);
						rowFrom.setTextColor(textColor);

						final TextView rowLabel = (TextView) row.findViewById(R.id.transaction_address);
						rowLabel.setTextColor(textColor);
						rowLabel.setText(label != null ? label : address);

						final TextView rowValue = (TextView) row.findViewById(R.id.transaction_value);
						rowValue.setTextColor(textColor);
						rowValue.setText((sent ? "-" : "+") + "\u2009" /* thin space */+ Utils.bitcoinValueToFriendlyString(tx.amount(wallet)));
					}
					catch (final ScriptException x)
					{
						throw new RuntimeException(x);
					}

					return row;
				}
			};
			setListAdapter(transactionsListAdapter);

			wallet.addEventListener(walletEventListener);

			getActivity().getContentResolver().registerContentObserver(AddressBookProvider.CONTENT_URI, true, contentObserver);
		}

		@Override
		public void onActivityCreated(final Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);

			setEmptyText(getString(mode == 2 ? R.string.wallet_transactions_fragment_empty_text_sent
					: R.string.wallet_transactions_fragment_empty_text_received));
		}

		@Override
		public void onViewCreated(final View view, final Bundle savedInstanceState)
		{
			super.onViewCreated(view, savedInstanceState);

			getListView().setOnItemClickListener(itemClickListener);
		}

		@Override
		public void onResume()
		{
			super.onResume();

			updateView();
		}

		@Override
		public void onDestroy()
		{
			getActivity().getContentResolver().unregisterContentObserver(contentObserver);

			application.getWallet().removeEventListener(walletEventListener);

			super.onDestroy();
		}

		public void updateView()
		{
			final Wallet wallet = application.getWallet();
			final List<Transaction> transactions = wallet.getAllTransactions();

			Collections.sort(transactions, new Comparator<Transaction>()
			{
				public int compare(final Transaction tx1, final Transaction tx2)
				{
					final boolean pending1 = wallet.isPending(tx1);
					final boolean pending2 = wallet.isPending(tx2);

					if (pending1 != pending2)
						return pending1 ? -1 : 1;

					final long time1 = tx1.updatedAt != null ? tx1.updatedAt.getTime() : 0;
					final long time2 = tx2.updatedAt != null ? tx2.updatedAt.getTime() : 0;

					if (time1 != time2)
						return time1 > time2 ? -1 : 1;

					return 0;
				}
			});

			transactionsListAdapter.clear();
			for (final Transaction tx : transactions)
			{
				final boolean sent = tx.sent(wallet);
				if ((mode == 0 && !sent) || mode == 1 || (mode == 2 && sent))
					transactionsListAdapter.add(tx);
			}
		}
	}
}
