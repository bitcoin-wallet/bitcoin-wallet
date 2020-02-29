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
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public class BlockListAdapter extends ListAdapter<BlockListAdapter.ListItem, BlockListAdapter.ViewHolder> {
    public static List<ListItem> buildListItems(final Context context, final List<StoredBlock> blocks, final Date currentTime,
            final MonetaryFormat format, final @Nullable Set<Transaction> transactions, final @Nullable Wallet wallet,
            final @Nullable Map<String, AddressBookEntry> addressBook) {
        final List<ListItem> items = new ArrayList<>(blocks.size());
        for (final StoredBlock block : blocks) {
            final Sha256Hash blockHash = block.getHeader().getHash();
            final int height = block.getHeight();
            final long timeMs = block.getHeader().getTimeSeconds() * DateUtils.SECOND_IN_MILLIS;
            final String time;
            if (timeMs < currentTime.getTime() - DateUtils.MINUTE_IN_MILLIS)
                time = DateUtils.getRelativeDateTimeString(context, timeMs, DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.WEEK_IN_MILLIS, 0).toString();
            else
                time = context.getString(R.string.block_row_now);
            final boolean isMiningRewardHalvingPoint =
                    ((AbstractBitcoinNetParams) Constants.NETWORK_PARAMETERS).isRewardHalvingPoint(height);
            final boolean isDifficultyTransitionPoint =
                    ((AbstractBitcoinNetParams) Constants.NETWORK_PARAMETERS).isDifficultyTransitionPoint(height);
            final List<ListItem.TxItem> transactionItems = buildTransactionItems(context, blockHash, transactions,
                    wallet, addressBook);
            items.add(new ListItem(blockHash, height, time, isMiningRewardHalvingPoint, isDifficultyTransitionPoint,
                    format, transactionItems));
        }
        return items;
    }

    private static List<ListItem.TxItem> buildTransactionItems(final Context context, final Sha256Hash blockHash,
                                                               final @Nullable Set<Transaction> transactions,
                                                               final @Nullable Wallet wallet,
                                                               final @Nullable Map<String, AddressBookEntry> addressBook) {
        final List<ListItem.TxItem> transactionItems = new LinkedList<>();
        if (transactions != null && wallet != null) {
            for (final Transaction tx : transactions) {
                final Map<Sha256Hash, Integer> appearsInHashes = tx.getAppearsInHashes();
                if (appearsInHashes != null && appearsInHashes.containsKey(blockHash)) {
                    final boolean isCoinBase = tx.isCoinBase();
                    final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;

                    final Coin value = tx.getValue(wallet);
                    final boolean sent = value.signum() < 0;
                    final boolean self = WalletUtils.isEntirelySelf(tx, wallet);
                    final Address address;
                    if (sent)
                        address = WalletUtils.getToAddressOfSent(tx, wallet);
                    else
                        address = WalletUtils.getWalletAddressOfReceived(tx, wallet);

                    final CharSequence fromTo;
                    if (isInternal || self)
                        fromTo = context.getString(R.string.symbol_internal);
                    else if (sent)
                        fromTo = context.getString(R.string.symbol_to);
                    else
                        fromTo = context.getString(R.string.symbol_from);

                    final CharSequence label;
                    if (isCoinBase) {
                        label = context.getString(R.string.wallet_transactions_fragment_coinbase);
                    } else if (isInternal || self) {
                        label = context.getString(R.string.wallet_transactions_fragment_internal);
                    } else if (address != null && addressBook != null) {
                        final AddressBookEntry entry = addressBook.get(address.toString());
                        if (entry != null)
                            label = entry.getLabel();
                        else
                            label = "?";
                    } else {
                        label = "?";
                    }

                    final CharSequence addressText = label != null ? label : address.toString();
                    final Typeface addressTypeface = label != null ? Typeface.DEFAULT : Typeface.MONOSPACE;

                    transactionItems.add(new ListItem.TxItem(fromTo, addressText, addressTypeface, label, value));
                }
            }
        }
        return transactionItems;
    }

    public static class ListItem {
        // internal item id
        public final long id;

        public final Sha256Hash blockHash;
        public final int height;
        public final String time;
        public final boolean isMiningRewardHalvingPoint;
        public final boolean isDifficultyTransitionPoint;
        public final List<TxItem> transactions;
        public final MonetaryFormat format;

        public ListItem(final Sha256Hash blockHash, final int height, final String time,
                        final boolean isMiningRewardHalvingPoint, final boolean isDifficultyTransitionPoint,
                        final MonetaryFormat format, final @Nullable List<TxItem> transactions) {
            this.id = id(blockHash);
            this.blockHash = blockHash;
            this.height = height;
            this.time = time;
            this.isMiningRewardHalvingPoint = isMiningRewardHalvingPoint;
            this.isDifficultyTransitionPoint = isDifficultyTransitionPoint;
            this.format = format;
            this.transactions = transactions;
        }

        private static long id(final Sha256Hash blockHash) {
            final byte[] bytes = blockHash.getBytes();
            return ByteBuffer.wrap(bytes).getLong(bytes.length - Long.BYTES);
        }

        public static class TxItem {
            public final CharSequence fromTo;
            public final CharSequence addressText;
            public final Typeface addressTypeface;
            public final CharSequence label;
            public final Coin value;

            public TxItem(final CharSequence fromTo, final CharSequence addressText, final Typeface addressTypeface,
                          final CharSequence label, final Coin value) {
                this.fromTo = fromTo;
                this.addressText = addressText;
                this.addressTypeface = addressTypeface;
                this.label = label;
                this.value = value;
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
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(final ListItem oldItem, final ListItem newItem) {
                return Objects.equals(oldItem.time, newItem.time);
            }
        });

        inflater = LayoutInflater.from(context);
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
        for (final ListItem.TxItem tx : listItem.transactions) {
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

    private void bindTransactionView(final View row, final MonetaryFormat format, final ListItem.TxItem tx) {
        final TextView rowFromTo = row.findViewById(R.id.block_row_transaction_fromto);
        rowFromTo.setText(tx.fromTo);
        final TextView rowAddress = row.findViewById(R.id.block_row_transaction_address);
        rowAddress.setText(tx.addressText);
        rowAddress.setTypeface(tx.addressTypeface);
        final CurrencyTextView rowValue = row.findViewById(R.id.block_row_transaction_value);
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
            transactionsViewGroup = itemView.findViewById(R.id.block_list_row_transactions_group);
            miningRewardAdjustmentView = itemView.findViewById(R.id.block_list_row_mining_reward_adjustment);
            miningDifficultyAdjustmentView = itemView.findViewById(R.id.block_list_row_mining_difficulty_adjustment);
            heightView = itemView.findViewById(R.id.block_list_row_height);
            timeView = itemView.findViewById(R.id.block_list_row_time);
            hashView = itemView.findViewById(R.id.block_list_row_hash);
            menuView = itemView.findViewById(R.id.block_list_row_menu);
        }
    }
}
