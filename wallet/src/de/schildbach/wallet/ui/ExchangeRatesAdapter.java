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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.exchangerate.ExchangeRateEntry;
import de.schildbach.wallet.service.BlockchainState;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesAdapter extends ListAdapter<ExchangeRatesAdapter.ListItem, ExchangeRatesAdapter.ViewHolder> {
    public static List<ListItem> buildListItems(final List<ExchangeRateEntry> exchangeRates, final Coin balance,
                                                final BlockchainState blockchainState, final String defaultCurrency,
                                                final Coin rateBase) {
        final List<ListItem> items = new ArrayList<>(exchangeRates.size());
        for (final ExchangeRateEntry exchangeRate : exchangeRates) {
            final ExchangeRate rate = exchangeRate.exchangeRate();
            final String source = exchangeRate.getSource();
            final String currencyCode = rate.fiat.currencyCode;
            final Fiat baseRateAsFiat = rate.coinToFiat(rateBase);
            final int baseRateMinDecimals = !rateBase.isLessThan(Coin.COIN) ? 2 : 4;
            final Fiat balanceAsFiat = balance != null && (blockchainState == null || !blockchainState.replaying)
                    ? rate.coinToFiat(balance) : null;
            final boolean isDefaultCurrency = currencyCode.equals(defaultCurrency);
            items.add(new ListItem(source, currencyCode, baseRateAsFiat, baseRateMinDecimals,
                    balanceAsFiat, isDefaultCurrency));
        }
        return items;
    }

    public static class ListItem {
        // internal item id
        public final long id;

        public final String currencyCode;
        public final Fiat baseRateAsFiat;
        public final int baseRateMinDecimals;
        public final Fiat balanceAsFiat;
        public final boolean isSelected;

        public ListItem(final String source, final String currencyCode, final Fiat baseRateAsFiat,
                        final int baseRateMinDecimals, final Fiat balanceAsFiat, final boolean isSelected) {
            this.id = id(source, currencyCode);
            this.currencyCode = currencyCode;
            this.baseRateAsFiat = baseRateAsFiat;
            this.baseRateMinDecimals = baseRateMinDecimals;
            this.balanceAsFiat = balanceAsFiat;
            this.isSelected = isSelected;
        }

        private static long id(final String source, final String currencyCode) {
            return ID_HASH.newHasher().putUnencodedChars(source).putUnencodedChars(currencyCode).hash().asLong();
        }

        private static final HashFunction ID_HASH = Hashing.farmHashFingerprint64();
    }

    public interface OnClickListener {
        void onExchangeRateMenuClick(View view, String currencyCode);
    }

    private final LayoutInflater inflater;
    @Nullable
    private final OnClickListener onClickListener;

    private enum ChangeType {
        RATE, DEFAULT
    }

    public ExchangeRatesAdapter(final Context context, final @Nullable OnClickListener onClickListener) {
        super(new DiffUtil.ItemCallback<ListItem>() {
            @Override
            public boolean areItemsTheSame(final ListItem oldItem, final ListItem newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(final ListItem oldItem, final ListItem newItem) {
                if (!Objects.equals(oldItem.baseRateAsFiat, newItem.baseRateAsFiat))
                    return false;
                if (!Objects.equals(oldItem.baseRateMinDecimals, newItem.baseRateMinDecimals))
                    return false;
                if (!Objects.equals(oldItem.balanceAsFiat, newItem.balanceAsFiat))
                    return false;
                if (!Objects.equals(oldItem.isSelected, newItem.isSelected))
                    return false;
                return true;
            }

            @Nullable
            @Override
            public Object getChangePayload(final ListItem oldItem, final ListItem newItem) {
                final EnumSet<ChangeType> changes = EnumSet.noneOf(ChangeType.class);
                if (!(Objects.equals(oldItem.baseRateAsFiat, newItem.baseRateAsFiat)
                        && Objects.equals(oldItem.baseRateMinDecimals, newItem.baseRateMinDecimals)
                        && Objects.equals(oldItem.balanceAsFiat, newItem.balanceAsFiat)))
                    changes.add(ChangeType.RATE);
                if (!Objects.equals(oldItem.isSelected, newItem.isSelected))
                    changes.add(ChangeType.DEFAULT);
                return changes;
            }
        });

        this.inflater = LayoutInflater.from(context);
        this.onClickListener = onClickListener;

        setHasStableIds(true);
    }

    @Override
    public long getItemId(final int position) {
        final ListItem listItem = getItem(position);
        return listItem.id;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.exchange_rate_row, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position, final List<Object> payloads) {
        final boolean fullBind = payloads.isEmpty();
        final EnumSet<ChangeType> changes = EnumSet.noneOf(ChangeType.class);
        for (final Object payload : payloads)
            changes.addAll((EnumSet<ChangeType>) payload);

        final ListItem listItem = getItem(position);
        if (fullBind || changes.contains(ChangeType.DEFAULT)) {
            holder.itemView.setBackgroundResource(listItem.isSelected ? R.color.bg_level3 : R.color.bg_level2);
            holder.defaultView.setVisibility(listItem.isSelected ? View.VISIBLE : View.INVISIBLE);
        }
        if (fullBind || changes.contains(ChangeType.RATE)) {
            holder.rateView.setFormat(Constants.LOCAL_FORMAT.minDecimals(listItem.baseRateMinDecimals));
            holder.rateView.setAmount(listItem.baseRateAsFiat);
            holder.walletView.setFormat(Constants.LOCAL_FORMAT);
            if (listItem.balanceAsFiat != null) {
                holder.walletView.setAmount(listItem.balanceAsFiat);
                holder.walletView.setStrikeThru(!Constants.NETWORK_PARAMETERS.getId().equals(NetworkParameters.ID_MAINNET));
            } else {
                holder.walletView.setText("n/a");
                holder.walletView.setStrikeThru(false);
            }
        }
        if (fullBind) {
            holder.currencyCodeView.setText(listItem.currencyCode);
            final OnClickListener onClickListener = this.onClickListener;
            if (onClickListener != null) {
                holder.menuView.setOnClickListener(v -> onClickListener.onExchangeRateMenuClick(v, listItem.currencyCode));
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final View defaultView;
        private final TextView currencyCodeView;
        private final CurrencyTextView rateView;
        private final CurrencyTextView walletView;
        private final ImageButton menuView;

        public ViewHolder(final View itemView) {
            super(itemView);
            defaultView = itemView.findViewById(R.id.exchange_rate_row_default);
            currencyCodeView = itemView.findViewById(R.id.exchange_rate_row_currency_code);
            rateView = itemView.findViewById(R.id.exchange_rate_row_rate);
            walletView = itemView.findViewById(R.id.exchange_rate_row_balance);
            menuView = itemView.findViewById(R.id.exchange_rate_row_menu);
        }
    }
}
