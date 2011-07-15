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

import java.util.List;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressesFragment extends ListFragment
{
	private Application application;
	private List<ECKey> keys;

	private ImageButton addButton;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = (Application) getActivity().getApplication();
		final Wallet wallet = application.getWallet();
		keys = wallet.keychain;

		setListAdapter(new Adapter());
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = super.onCreateView(inflater, container, savedInstanceState);

		view.setBackgroundColor(Color.WHITE);

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onHiddenChanged(boolean hidden)
	{
		final ActionBarFragment actionBar = (ActionBarFragment) getFragmentManager().findFragmentById(R.id.action_bar_fragment);

		if (!hidden)
		{
			addButton = actionBar.addButton(R.drawable.ic_menu_btn_add);
			addButton.setOnClickListener(addButtonClickListener);
		}
		else
		{
			actionBar.removeButton(addButton);
		}
	}
	
	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final ECKey key = keys.get(position);
		final Address address = key.toAddress(application.getNetworkParameters());
		
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		prefs.edit().putString(Constants.PREFS_KEY_SELECTED_ADDRESS, address.toString()).commit();

		final WalletAddressFragment walletAddressFragment = (WalletAddressFragment) getFragmentManager()
				.findFragmentById(R.id.wallet_address_fragment);
		if (walletAddressFragment != null)
		{
			walletAddressFragment.updateView();
			walletAddressFragment.flashAddress();
		}
	}

	private void updateView()
	{
		final ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

	final OnClickListener addButtonClickListener = new OnClickListener()
	{
		public void onClick(final View v)
		{
			new AlertDialog.Builder(getActivity()).setTitle(R.string.wallet_addresses_fragment_add_dialog_title)
					.setMessage(R.string.wallet_addresses_fragment_add_dialog_message)
					.setPositiveButton(R.string.wallet_addresses_fragment_add_dialog_positive, new DialogInterface.OnClickListener()
					{
						public void onClick(final DialogInterface dialog, final int which)
						{
							application.addNewKeyToWallet();

							updateView();
						}
					}).setNegativeButton(R.string.wallet_addresses_fragment_add_dialog_negative, null).show();
		}
	};

	private class Adapter extends BaseAdapter
	{
		public int getCount()
		{
			return keys.size();
		}

		public Object getItem(final int position)
		{
			return keys.get(position);
		}

		public long getItemId(final int position)
		{
			return keys.get(position).hashCode();
		}

		public View getView(final int position, View row, final ViewGroup parent)
		{
			final ECKey key = (ECKey) getItem(position);
			final Address address = key.toAddress(application.getNetworkParameters());

			if (row == null)
				row = getLayoutInflater(null).inflate(R.layout.address_row, null);

			final TextView addressView = (TextView) row.findViewById(R.id.address_row_address);
			addressView.setText(WalletUtils.splitIntoLines(address.toString(), 2));

			return row;
		}
	}
}
