/*
 * Copyright 2012-2013 the original author or authors.
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

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

public class WalletAddressesAdapter extends BaseAdapter
{
	private final DateFormat dateFormat;
	private final int colorInsignificant;
	private final int colorLessSignificant;
	private final LayoutInflater inflater;
	private final ContentResolver contextResolver;

	private final List<ECKey> keys;
	private final boolean showKeyCreationTime;
	private String selectedAddress = null;

	public WalletAddressesAdapter(final Context context, final List<ECKey> keys, final boolean showKeyCreationTime)
	{
		final Resources res = context.getResources();

		dateFormat = android.text.format.DateFormat.getDateFormat(context);
		colorInsignificant = res.getColor(R.color.fg_insignificant);
		colorLessSignificant = res.getColor(R.color.fg_less_significant);
		inflater = LayoutInflater.from(context);
		contextResolver = context.getContentResolver();

		this.keys = keys;
		this.showKeyCreationTime = showKeyCreationTime;
	}

	public void setSelectedAddress(final String selectedAddress)
	{
		this.selectedAddress = selectedAddress;

		notifyDataSetChanged();
	}

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
		final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);

		if (row == null)
			row = inflater.inflate(R.layout.address_book_row, null);

		final boolean isDefaultAddress = address.toString().equals(selectedAddress);

		row.setBackgroundResource(isDefaultAddress ? R.color.bg_less_bright : R.color.bg_bright);

		final TextView addressView = (TextView) row.findViewById(R.id.address_book_row_address);
		addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));

		final TextView labelView = (TextView) row.findViewById(R.id.address_book_row_label);
		final String label = AddressBookProvider.resolveLabel(contextResolver, address.toString());
		if (label != null)
		{
			labelView.setText(label);
			labelView.setTextColor(colorLessSignificant);
		}
		else
		{
			labelView.setText(R.string.address_unlabeled);
			labelView.setTextColor(colorInsignificant);
		}

		if (showKeyCreationTime)
		{
			final TextView createdView = (TextView) row.findViewById(R.id.address_book_row_created);
			final long created = key.getCreationTimeSeconds();
			if (created != 0)
			{
				createdView.setText(dateFormat.format(new Date(created * 1000)));
				createdView.setVisibility(View.VISIBLE);
			}
			else
			{
				createdView.setVisibility(View.GONE);
			}
		}

		return row;
	}
}
