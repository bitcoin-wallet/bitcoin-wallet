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

package de.schildbach.wallet;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.util.ActionBarFragment;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressesFragment extends ListFragment
{
	private Application application;
	private Activity activity;
	private List<ECKey> keys;

	private ImageButton addButton;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		activity = getActivity();
		application = (Application) activity.getApplication();
		final Wallet wallet = application.getWallet();
		keys = wallet.keychain;

		setListAdapter(new Adapter());
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		registerForContextMenu(getListView());
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onHiddenChanged(final boolean hidden)
	{
		final ActionBarFragment actionBar = ((AbstractWalletActivity) activity).getActionBar();

		if (!hidden)
		{
			addButton = actionBar.addButton(R.drawable.ic_action_add);
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

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		prefs.edit().putString(Constants.PREFS_KEY_SELECTED_ADDRESS, address.toString()).commit();

		final WalletAddressFragment walletAddressFragment = (WalletAddressFragment) getFragmentManager().findFragmentById(
				R.id.wallet_address_fragment);
		if (walletAddressFragment != null)
		{
			walletAddressFragment.updateView();
			walletAddressFragment.flashAddress();
		}
	}

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo info)
	{
		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) info;
		final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);
		final boolean enabled = key.getCreationTimeSeconds() == 0;

		activity.getMenuInflater().inflate(R.menu.wallet_addresses_context, menu);
		final MenuItem item = menu.findItem(R.id.wallet_addresses_context_determine_creation_time);
		item.setEnabled(enabled);
		item.setVisible(enabled);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		final ECKey key = (ECKey) getListAdapter().getItem(menuInfo.position);

		switch (item.getItemId())
		{
			case R.id.wallet_addresses_context_determine_creation_time:
				determineCreationTime(key);
				return true;

			default:
				return false;
		}
	}

	private void determineCreationTime(final ECKey key)
	{
		new DetermineFirstSeenThread(key.toAddress(Constants.NETWORK_PARAMETERS).toString())
		{
			@Override
			protected void succeed(final Date firstSeen)
			{
				activity.runOnUiThread(new Runnable()
				{
					public void run()
					{
						key.setCreationTimeSeconds(firstSeen.getTime() / 1000);
						updateView();
						application.saveWallet();
					}
				});
			}
		};
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
			new AlertDialog.Builder(activity).setTitle(R.string.wallet_addresses_fragment_add_dialog_title)
					.setMessage(R.string.wallet_addresses_fragment_add_dialog_message)
					.setPositiveButton(R.string.wallet_addresses_fragment_add_dialog_positive, new DialogInterface.OnClickListener()
					{
						public void onClick(final DialogInterface dialog, final int which)
						{
							application.addNewKeyToWallet();

							updateView();
						}
					}).setNegativeButton(R.string.button_cancel, null).show();
		}
	};

	private class Adapter extends BaseAdapter
	{
		final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);

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

			final TextView createdView = (TextView) row.findViewById(R.id.address_row_created);
			final long created = key.getCreationTimeSeconds();
			createdView.setText(created != 0 ? dateFormat.format(new Date(created * 1000)) : null);

			return row;
		}
	}
}
