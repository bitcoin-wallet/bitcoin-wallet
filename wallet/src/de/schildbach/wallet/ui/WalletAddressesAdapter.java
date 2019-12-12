/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.wallet.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.AddressBookEntry;
import de.schildbach.wallet.util.WalletUtils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressesAdapter extends BaseAdapter {
    private final int colorSignificant;
    private final int colorInsignificant;
    private final int colorLessSignificant;
    private final LayoutInflater inflater;

    private final List<Address> derivedAddresses = new ArrayList<>();
    private final List<Address> randomAddresses = new ArrayList<>();
    @Nullable
    private Wallet wallet = null;
    @Nullable
    private Map<String, AddressBookEntry> addressBook = null;

    public WalletAddressesAdapter(final Context context) {
        colorSignificant = ContextCompat.getColor(context, R.color.fg_significant);
        colorInsignificant = ContextCompat.getColor(context, R.color.fg_insignificant);
        colorLessSignificant = ContextCompat.getColor(context, R.color.fg_less_significant);
        inflater = LayoutInflater.from(context);
    }

    public void replaceDerivedAddresses(final Collection<Address> addresses) {
        this.derivedAddresses.clear();
        this.derivedAddresses.addAll(addresses);
        notifyDataSetChanged();
    }

    public void replaceRandomAddresses(final Collection<Address> addresses) {
        this.randomAddresses.clear();
        this.randomAddresses.addAll(addresses);
        notifyDataSetChanged();
    }

    public void setWallet(final Wallet wallet) {
        this.wallet = wallet;
        notifyDataSetChanged();
    }

    public void setAddressBook(final Map<String, AddressBookEntry> addressBook) {
        this.addressBook = addressBook;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        int count = derivedAddresses.size();
        if (!randomAddresses.isEmpty())
            count += randomAddresses.size() + 1;
        return count;
    }

    @Override
    public Object getItem(final int position) {
        final int numDerived = derivedAddresses.size();
        if (position < numDerived)
            return derivedAddresses.get(position);
        else if (position == numDerived)
            return null;
        else
            return randomAddresses.get(position - numDerived - 1);
    }

    @Override
    public long getItemId(final int position) {
        final Object key = getItem(position);
        return key != null ? key.hashCode() : 0;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(final int position) {
        final int numDerived = derivedAddresses.size();
        if (position < numDerived)
            return 0;
        else if (position == numDerived)
            return 1;
        else
            return 0;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        if (getItemViewType(position) == 0)
            return rowKey(position, convertView);
        else
            return rowSeparator(convertView);
    }

    private View rowKey(final int position, View row) {
        final Address address = (Address) getItem(position);
        final Wallet wallet = this.wallet;
        final boolean isRotateKey;
        if (wallet != null) {
            final ECKey key = wallet.findKeyFromAddress(address);
            isRotateKey = wallet != null && wallet.isKeyRotating(key);
        } else {
            isRotateKey = false;
        }

        if (row == null)
            row = inflater.inflate(R.layout.address_book_row, null);

        final TextView addressView = row.findViewById(R.id.address_book_row_address);
        addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
                Constants.ADDRESS_FORMAT_LINE_SIZE));
        addressView.setTextColor(isRotateKey ? colorInsignificant : colorSignificant);

        final TextView labelView = row.findViewById(R.id.address_book_row_label);
        final Map<String, AddressBookEntry> addressBook = this.addressBook;
        if (addressBook != null) {
            final AddressBookEntry entry = addressBook.get(address.toString());
            if (entry != null) {
                labelView.setText(entry.getLabel());
                labelView.setTextColor(isRotateKey ? colorInsignificant : colorLessSignificant);
            } else {
                labelView.setText(R.string.address_unlabeled);
                labelView.setTextColor(colorInsignificant);
            }
        } else {
            labelView.setText(R.string.address_unlabeled);
            labelView.setTextColor(colorInsignificant);
        }

        final TextView messageView = row.findViewById(R.id.address_book_row_message);
        messageView.setVisibility(isRotateKey ? View.VISIBLE : View.GONE);

        return row;
    }

    private View rowSeparator(View row) {
        if (row == null)
            row = inflater.inflate(R.layout.row_separator, null);

        final TextView textView = row.findViewById(android.R.id.text1);
        textView.setText(R.string.address_book_list_receiving_random);

        return row;
    }
}
