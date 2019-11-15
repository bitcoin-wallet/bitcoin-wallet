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

package de.schildbach.wallet.ui.monitor;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.AddressBookEntry;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet.util.WalletUtils;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateUtils;
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
public class BlockListAdapter extends ListAdapter<BlockListAdapter.ListItem, BlockListAdapter.ViewHolder> {
    public static List<ListItem> buildListItems(final Context context, final List<StoredBlock> blocks, final Date time,
            final MonetaryFormat format, final @Nullable Set<Transaction> transactions, final @Nullable Wallet wallet,
            final @Nullable Map<String, AddressBookEntry> addressBook) {
        final List<ListItem> items = new ArrayList<>(blocks.size());
        for (final StoredBlock block : blocks)
            items.add(new ListItem(context, block, time, format, transactions, wallet, addressBook));
        return items;
    }

    public static class ListItem {
        public final Sha256Hash blockHash;
        public final int height;
        public final String time;
        public final boolean isMiningRewardHalvingPoint;
        public final boolean isDifficultyTransitionPoint;
        public final MonetaryFormat format;
        public final List<ListTransaction> transactions;

        public ListItem(final Context context, final StoredBlock block, final Date time, final MonetaryFormat format,
                final @Nullable Set<Transaction> transactions, final @Nullable Wallet wallet,
                final @Nullable Map<String, AddressBookEntry> addressBook) {
            this.blockHash = block.getHeader().getHash();
            this.height = block.getHeight();
            final long timeMs = block.getHeader().getTimeSeconds() * DateUtils.SECOND_IN_MILLIS;
            if (timeMs < time.getTime() - DateUtils.MINUTE_IN_MILLIS)
                this.time = DateUtils.getRelativeDateTimeString(context, timeMs, DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.WEEK_IN_MILLIS, 0).toString();
            else
                this.time = context.getString(R.string.block_row_now);
            this.isMiningRewardHalvingPoint = isMiningRewardHalvingPoint(block);
            this.isDifficultyTransitionPoint = isDifficultyTransitionPoint(block);
            this.format = format;
            this.transactions = new LinkedList<>();
            if (transactions != null && wallet != null) {
                for (final Transaction tx : transactions) {
                    final Map<Sha256Hash, Integer> appearsInHashes = tx.getAppearsInHashes();
                    if (appearsInHashes != null && appearsInHashes.containsKey(blockHash))
                        this.transactions.add(new ListTransaction(context, tx, wallet, addressBook));
                }
            }
        }

        private final boolean isMiningRewardHalvingPoint(final StoredBlock storedPrev) {
            return ((storedPrev.getHeight() + 1) % 210000) == 0;
        }

        private final boolean isDifficultyTransitionPoint(final StoredBlock storedPrev) {
            return ((storedPrev.getHeight() + 1) % Constants.NETWORK_PARAMETERS.getInterval()) == 0;
        }

        public static class ListTransaction {
            public final String fromTo;
            public final Address address;
            public final String label;
            public final Coin value;

            public ListTransaction(final Context context, final Transaction tx, final Wallet wallet,
                    final @Nullable Map<String, AddressBookEntry> addressBook) {
                final boolean isCoinBase = tx.isCoinBase();
                final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;

                this.value = tx.getValue(wallet);
                final boolean sent = value.signum() < 0;
                final boolean self = WalletUtils.isEntirelySelf(tx, wallet);
                if (sent)
                    this.address = WalletUtils.getToAddressOfSent(tx, wallet);
                else
                    this.address = WalletUtils.getWalletAddressOfReceived(tx, wallet);

                if (isInternal || self)
                    this.fromTo = context.getString(R.string.symbol_internal);
                else if (sent)
                    this.fromTo = context.getString(R.string.symbol_to);
                else
                    this.fromTo = context.getString(R.string.symbol_from);

                if (isCoinBase) {
                    this.label = context.getString(R.string.wallet_transactions_fragment_coinbase);
                } else if (isInternal || self) {
                    this.label = context.getString(R.string.wallet_transactions_fragment_internal);
                } else if (address != null && addressBook != null) {
                    final AddressBookEntry entry = addressBook.get(address.toString());
                    if (entry != null)
                        this.label = entry.getLabel();
                    else
                        this.label = "?";
                } else {
                    this.label = "?";
                }
            }
        }
    }

    private static final int ROW_BASE_CHILD_COUNT = 2;
    private static final int ROW_INSERT_INDEX = 1;

    private final LayoutInflater inflater;
    @Nullable
    private final OnClickListener onClickListener;

    public BlockListAdapter(final Context context, final @Nullable OnClickListener onClickListener) {
        super(new DiffUtil.ItemCallback<ListItem>() {
            @Override
            public boolean areItemsTheSame(final ListItem oldItem, final ListItem newItem) {
                return oldItem.blockHash.equals(newItem.blockHash);
            }

            @Override
            public boolean areContentsTheSame(final ListItem oldItem, final ListItem newItem) {
                return Objects.equals(oldItem.time, newItem.time);
            }
        });

        inflater = LayoutInflater.from(context);
        this.onClickListener = onClickListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.block_row, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final ListItem listItem = getItem(position);

        holder.heightView.setText(Integer.toString(listItem.height));
        holder.timeView.setText(listItem.time);
        holder.miningRewardAdjustmentView.setVisibility(listItem.isMiningRewardHalvingPoint ? View.VISIBLE : View.GONE);
        holder.miningDifficultyAdjustmentView
                .setVisibility(listItem.isDifficultyTransitionPoint ? View.VISIBLE : View.GONE);
        holder.hashView.setText(WalletUtils.formatHash(null, listItem.blockHash.toString(), 8, 0, ' '));

        final int transactionChildCount = holder.transactionsViewGroup.getChildCount() - ROW_BASE_CHILD_COUNT;
        int iTransactionView = 0;
        for (final BlockListAdapter.ListItem.ListTransaction tx : listItem.transactions) {
            final View view;
            if (iTransactionView < transactionChildCount) {
                view = holder.transactionsViewGroup.getChildAt(ROW_INSERT_INDEX + iTransactionView);
            } else {
                view = inflater.inflate(R.layout.block_row_transaction, null);
                holder.transactionsViewGroup.addView(view, ROW_INSERT_INDEX + iTransactionView);
            }
            bindTransactionView(view, listItem.format, tx);
            iTransactionView++;
        }
        final int leftoverTransactionViews = transactionChildCount - iTransactionView;
        if (leftoverTransactionViews > 0)
            holder.transactionsViewGroup.removeViews(ROW_INSERT_INDEX + iTransactionView, leftoverTransactionViews);

        final OnClickListener onClickListener = this.onClickListener;
        if (onClickListener != null) {
            holder.menuView.setOnClickListener(v -> onClickListener.onBlockMenuClick(v, listItem.blockHash));
        }
    }

    private void bindTransactionView(final View row, final MonetaryFormat format, final ListItem.ListTransaction tx) {
        // receiving or sending
        final TextView rowFromTo = (TextView) row.findViewById(R.id.block_row_transaction_fromto);
        rowFromTo.setText(tx.fromTo);

        // address
        final TextView rowAddress = (TextView) row.findViewById(R.id.block_row_transaction_address);
        rowAddress.setText(tx.label != null ? tx.label : tx.address.toString());
        rowAddress.setTypeface(tx.label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

        // value
        final CurrencyTextView rowValue = (CurrencyTextView) row.findViewById(R.id.block_row_transaction_value);
        rowValue.setAlwaysSigned(true);
        rowValue.setFormat(format);
        rowValue.setAmount(tx.value);
    }

    public interface OnClickListener {
        void onBlockMenuClick(View view, Sha256Hash blockHash);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewGroup transactionsViewGroup;
        private final View miningRewardAdjustmentView;
        private final View miningDifficultyAdjustmentView;
        private final TextView heightView;
        private final TextView timeView;
        private final TextView hashView;
        private final ImageButton menuView;

        private ViewHolder(final View itemView) {
            super(itemView);
            transactionsViewGroup = (ViewGroup) itemView.findViewById(R.id.block_list_row_transactions_group);
            miningRewardAdjustmentView = itemView.findViewById(R.id.block_list_row_mining_reward_adjustment);
            miningDifficultyAdjustmentView = itemView.findViewById(R.id.block_list_row_mining_difficulty_adjustment);
            heightView = (TextView) itemView.findViewById(R.id.block_list_row_height);
            timeView = (TextView) itemView.findViewById(R.id.block_list_row_time);
            hashView = (TextView) itemView.findViewById(R.id.block_list_row_hash);
            menuView = (ImageButton) itemView.findViewById(R.id.block_list_row_menu);
        }
    }
}
