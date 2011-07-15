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

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressesFragment extends ListFragment
{
	private Application application;
	private List<ECKey> keys;

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

	private void updateView()
	{
		final ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

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

			if (row == null)
				row = getLayoutInflater(null).inflate(android.R.layout.simple_list_item_1, null);

			((TextView) row.findViewById(android.R.id.text1)).setText(key.toAddress(application.getNetworkParameters()).toString());

			return row;
		}
	}
}
