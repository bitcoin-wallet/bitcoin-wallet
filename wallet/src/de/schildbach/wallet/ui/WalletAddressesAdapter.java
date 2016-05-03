/*
 * Copyright 2012-2015 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.wallet.Wallet;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
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
	private final int colorSignificant;
	private final int colorInsignificant;
	private final int colorLessSignificant;
	private final LayoutInflater inflater;

	private final List<ECKey> derivedKeys = new ArrayList<ECKey>();
	private final List<ECKey> randomKeys = new ArrayList<ECKey>();

	public WalletAddressesAdapter(final Context context, final Wallet wallet)
	{
		final Resources res = context.getResources();

		this.context = context;
		this.wallet = wallet;
		colorSignificant = res.getColor(R.color.fg_significant);
		colorInsignificant = res.getColor(R.color.fg_insignificant);
		colorLessSignificant = res.getColor(R.color.fg_less_significant);
		inflater = LayoutInflater.from(context);
	}

	public void replaceDerivedKeys(final Collection<ECKey> keys)
	{
		this.derivedKeys.clear();
		this.derivedKeys.addAll(keys);

		notifyDataSetChanged();
	}

	public void replaceRandomKeys(final Collection<ECKey> keys)
	{
		this.randomKeys.clear();
		this.randomKeys.addAll(keys);

		notifyDataSetChanged();
	}

	@Override
	public int getCount()
	{
		int count = derivedKeys.size();
		if (!randomKeys.isEmpty())
			count += randomKeys.size() + 1;
		return count;
	}

	@Override
	public Object getItem(final int position)
	{
		final int numDerivedKeys = derivedKeys.size();
		if (position < numDerivedKeys)
			return derivedKeys.get(position);
		else if (position == numDerivedKeys)
			return null;
		else
			return randomKeys.get(position - numDerivedKeys - 1);
	}

	@Override
	public long getItemId(final int position)
	{
		final Object key = getItem(position);
		return key != null ? key.hashCode() : 0;
	}

	@Override
	public int getViewTypeCount()
	{
		return 2;
	}

	@Override
	public int getItemViewType(final int position)
	{
		final int numDerivedKeys = derivedKeys.size();
		if (position < numDerivedKeys)
			return 0;
		else if (position == numDerivedKeys)
			return 1;
		else
			return 0;
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent)
	{
		if (getItemViewType(position) == 0)
			return rowKey(position, convertView);
		else
			return rowSeparator(convertView);
	}

	private View rowKey(final int position, View row)
	{
		final ECKey key = (ECKey) getItem(position);
		final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
		final boolean isRotateKey = wallet.isKeyRotating(key);

		if (row == null)
			row = inflater.inflate(R.layout.address_book_row, null);

		final TextView addressView = (TextView) row.findViewById(R.id.address_book_row_address);
		addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
		addressView.setTextColor(isRotateKey ? colorInsignificant : colorSignificant);

		final TextView labelView = (TextView) row.findViewById(R.id.address_book_row_label);
		final String label = AddressBookProvider.resolveLabel(context, address.toBase58());
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

		final TextView messageView = (TextView) row.findViewById(R.id.address_book_row_message);
		messageView.setVisibility(isRotateKey ? View.VISIBLE : View.GONE);

		return row;
	}

	private View rowSeparator(View row)
	{
		if (row == null)
			row = inflater.inflate(R.layout.row_separator, null);

		final TextView textView = (TextView) row.findViewById(android.R.id.text1);
		textView.setText(R.string.address_book_list_receiving_random);

		return row;
	}
}
