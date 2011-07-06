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
import java.util.List;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

/**
 * @author Andreas Schildbach
 */
public class WalletTransactionsFragment extends Fragment
{
	private Application application;

	private ListView transactionsList;
	private ArrayAdapter<Transaction> transactionsListAdapter;
	private List<Transaction> transactions;

	private final WalletEventListener walletEventListener = new WalletEventListener()
	{
		@Override
		public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			getActivity().runOnUiThread(new Runnable()
			{
				public void run()
				{
					updateView();
				}
			});
		}

		@Override
		public void onReorganize()
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

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_transactions_fragment, container, false);
		transactionsList = (ListView) view.findViewById(R.id.transactions);

		application = (Application) getActivity().getApplication();
		final Wallet wallet = application.getWallet();

		transactionsListAdapter = new ArrayAdapter<Transaction>(getActivity(), 0)
		{
			@Override
			public View getView(final int position, View row, final ViewGroup parent)
			{
				try
				{
					if (row == null)
						row = getLayoutInflater(null).inflate(R.layout.transaction_row, null);

					final Transaction tx = transactions.get(position);
					final boolean sent = tx.sent(wallet);
					final boolean pending = wallet.isPending(tx);
					final int textColor = pending ? Color.LTGRAY : Color.BLACK;
					final Address address = sent ? tx.outputs.get(0).getScriptPubKey().getToAddress() : tx.getInputs().get(0).getFromAddress();

					final TextView rowTo = (TextView) row.findViewById(R.id.transaction_to);
					rowTo.setVisibility(sent ? View.VISIBLE : View.INVISIBLE);
					rowTo.setTextColor(textColor);

					final TextView rowFrom = (TextView) row.findViewById(R.id.transaction_from);
					rowFrom.setVisibility(sent ? View.INVISIBLE : View.VISIBLE);
					rowFrom.setTextColor(textColor);

					final TextView rowLabel = (TextView) row.findViewById(R.id.transaction_address);
					rowLabel.setTextColor(textColor);
					// rowLabel.setText(WalletUtils.splitIntoLines(address.toString(), 1));
					rowLabel.setText(address.toString());

					final TextView rowValue = (TextView) row.findViewById(R.id.transaction_value);
					rowValue.setTextColor(textColor);
					rowValue.setText((sent ? "-" : "+") + Utils.bitcoinValueToFriendlyString(tx.amount(wallet)));
				}
				catch (final ScriptException x)
				{
					throw new RuntimeException(x);
				}

				return row;
			}
		};
		transactionsList.setAdapter(transactionsListAdapter);

		wallet.addEventListener(walletEventListener);

		updateView();

		return view;
	}

	@Override
	public void onDestroyView()
	{
		application.getWallet().removeEventListener(walletEventListener);

		super.onDestroyView();
	}

	public void updateView()
	{
		transactions = application.getWallet().getAllTransactions();

		transactionsListAdapter.clear();
		for (final Transaction tx : transactions)
			transactionsListAdapter.add(tx);
	}
}
