/*
 * Copyright 2012-2014 the original author or authors.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressesAdapter extends BaseAdapter
{
	private final Context context;
	private final Wallet wallet;
	private final DateFormat dateFormat;
	private final int colorSignificant;
	private final int colorInsignificant;
	private final int colorLessSignificant;
	private final LayoutInflater inflater;

	private final List<ECKey> keys = new ArrayList<ECKey>();
	private final boolean showKeyCreationTime;
	private String selectedAddress = null;

	public WalletAddressesAdapter(final Context context, @Nonnull final Wallet wallet, final boolean showKeyCreationTime)
	{
		final Resources res = context.getResources();

		this.context = context;
		this.wallet = wallet;
		dateFormat = android.text.format.DateFormat.getDateFormat(context);
		colorSignificant = res.getColor(R.color.fg_significant);
		colorInsignificant = res.getColor(R.color.fg_insignificant);
		colorLessSignificant = res.getColor(R.color.fg_less_significant);
		inflater = LayoutInflater.from(context);

		this.showKeyCreationTime = showKeyCreationTime;
	}

	public void replace(@Nonnull final Collection<ECKey> keys)
	{
		this.keys.clear();
		this.keys.addAll(keys);

		notifyDataSetChanged();
	}

	public void setSelectedAddress(final String selectedAddress)
	{
		this.selectedAddress = selectedAddress;

		notifyDataSetChanged();
	}

	@Override
	public int getCount()
	{
		return keys.size();
	}

	@Override
	public Object getItem(final int position)
	{
		return keys.get(position);
	}

	@Override
	public long getItemId(final int position)
	{
		return keys.get(position).hashCode();
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	@Override
	public View getView(final int position, View row, final ViewGroup parent)
	{
		final ECKey key = (ECKey) getItem(position);
		final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
		final boolean isRotateKey = wallet.isKeyRotating(key);

		if (row == null)
			row = inflater.inflate(R.layout.address_book_row, null);

		final boolean isDefaultAddress = address.toString().equals(selectedAddress);

		row.setBackgroundResource(isDefaultAddress ? R.color.bg_list_selected : R.color.bg_list);

		final TextView addressView = (TextView) row.findViewById(R.id.address_book_row_address);
		addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
		addressView.setTextColor(isRotateKey ? colorInsignificant : colorSignificant);

		final TextView labelView = (TextView) row.findViewById(R.id.address_book_row_label);
		final String label = AddressBookProvider.resolveLabel(context, address.toString());
		if (label != null)
		{
			labelView.setText(label);
			labelView.setTextColor(isRotateKey ? colorInsignificant : colorLessSignificant);
		}
		else
		{
			labelView.setText(R.string.address_unlabeled);
			labelView.setTextColor(colorInsignificant);
		}

		if (showKeyCreationTime)
		{
			final TextView createdView = (TextView) row.findViewById(R.id.address_book_row_created);
			final long createdMs = key.getCreationTimeSeconds() * DateUtils.SECOND_IN_MILLIS;
			if (createdMs != 0)
			{
				createdView.setText(dateFormat.format(new Date(createdMs)));
				createdView.setVisibility(View.VISIBLE);
			}
			else
			{
				createdView.setVisibility(View.GONE);
			}
		}

		final TextView messageView = (TextView) row.findViewById(R.id.address_book_row_message);
		messageView.setVisibility(isRotateKey ? View.VISIBLE : View.GONE);

		return row;
	}
}
