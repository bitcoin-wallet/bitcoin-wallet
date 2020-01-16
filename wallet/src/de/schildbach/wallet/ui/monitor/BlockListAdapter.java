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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet.ui.SeparatorViewHolder;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public class BlockListAdapter extends ListAdapter<BlockListAdapter.ListItem, RecyclerView.ViewHolder> {
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
            final List<ListItem.TxItem> transactionItems = buildTransactionItems(context, blockHash, transactions,
                    wallet, addressBook);
            if (((AbstractBitcoinNetParams) Constants.NETWORK_PARAMETERS).isRewardHalvingPoint(height))
                items.add(new ListItem.SeparatorItem(context.getString(R.string.block_row_mining_reward_adjustment)));
            if (((AbstractBitcoinNetParams) Constants.NETWORK_PARAMETERS).isDifficultyTransitionPoint(height))
                items.add(new ListItem.SeparatorItem(context.getString(R.string.block_row_mining_difficulty_adjustment)));
            items.add(new ListItem.BlockItem(blockHash, height, time, format, transactionItems));
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

    public static abstract class ListItem {
        // internal item id
        public final long id;

        private ListItem(final long id) {
            this.id = id;
        }

        public static class BlockItem extends ListItem {
            public final Sha256Hash blockHash;
            public final int height;
            public final String time;
            public final List<TxItem> transactions;
            public final MonetaryFormat format;

            public BlockItem(final Sha256Hash blockHash, final int height, final String time,
                             final MonetaryFormat format, final @Nullable List<TxItem> transactions) {
                super(id(blockHash));
                this.blockHash = blockHash;
                this.height = height;
                this.time = time;
                this.transactions = transactions;
                this.format = format;
            }

            private static long id(final Sha256Hash blockHash) {
                final byte[] bytes = blockHash.getBytes();
                return ByteBuffer.wrap(bytes).getLong(bytes.length - Long.BYTES);
            }
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

        public static class SeparatorItem extends ListItem {
            public final CharSequence label;

            public SeparatorItem(final CharSequence label) {
                super(id(label));
                this.label = label;
            }

            private static long id(final CharSequence label) {
                return ID_HASH.newHasher().putString(label, StandardCharsets.UTF_8).hash().asLong();
            }
        }

        private static final HashFunction ID_HASH = Hashing.farmHashFingerprint64();
    }

    public interface OnClickListener {
        void onBlockClick(View view, Sha256Hash blockHash);
    }

    public interface ContextMenuCallback {
        void onInflateBlockContextMenu(MenuInflater inflater, Menu menu);

        boolean onClickBlockContextMenuItem(MenuItem item, Sha256Hash blockHash);
    }

    private final LayoutInflater inflater;
    private final MenuInflater menuInflater;
    @Dimension
    private final int cardElevationSelected;

    @Nullable
    private final OnClickListener onClickListener;
    @Nullable
    private final ContextMenuCallback contextMenuCallback;
    @Nullable
    private Sha256Hash selectedBlockHash;

    private static final int VIEW_TYPE_BLOCK = 0;
    private static final int VIEW_TYPE_SEPARATOR = 1;

    private static final int ROW_BASE_CHILD_COUNT = 3;
    private static final int ROW_INSERT_INDEX = 1;

    private enum ChangeType {
        TIME, TRANSACTIONS, SELECTION
    }

    public BlockListAdapter(final Context context, @Nullable final OnClickListener onClickListener,
                            @Nullable final ContextMenuCallback contextMenuCallback) {
        super(new DiffUtil.ItemCallback<ListItem>() {
            @Override
            public boolean areItemsTheSame(final ListItem oldItem, final ListItem newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(final ListItem oldItem, final ListItem newItem) {
                if (oldItem instanceof ListItem.BlockItem)
                    return Objects.equals(((ListItem.BlockItem) oldItem).time, ((ListItem.BlockItem) newItem).time);
                else if (oldItem instanceof ListItem.SeparatorItem)
                    return Objects.equals(((ListItem.SeparatorItem) oldItem).label,
                            ((ListItem.SeparatorItem) newItem).label);
                else
                    throw new IllegalArgumentException();
            }

            @Nullable
            @Override
            public Object getChangePayload(final ListItem oldItem, final ListItem newItem) {
                final EnumSet<ChangeType> changes = EnumSet.noneOf(ChangeType.class);
                if (oldItem instanceof ListItem.BlockItem) {
                    final ListItem.BlockItem oldBlockItem = (ListItem.BlockItem) oldItem;
                    final ListItem.BlockItem newBlockItem = (ListItem.BlockItem) newItem;
                    if (!Objects.equals(oldBlockItem.time, newBlockItem.time))
                        changes.add(ChangeType.TIME);
                    if (!Objects.equals(oldBlockItem.transactions, newBlockItem.transactions))
                        changes.add(ChangeType.TRANSACTIONS);
                }
                return changes;
            }
        });

        this.inflater = LayoutInflater.from(context);
        this.menuInflater = new MenuInflater(context);
        this.contextMenuCallback = contextMenuCallback;
        this.onClickListener = onClickListener;
        this.cardElevationSelected = context.getResources().getDimensionPixelOffset(R.dimen.card_elevation_selected);

        setHasStableIds(true);
    }

    @MainThread
    public void setSelectedBlock(final Sha256Hash newSelectedBlockHash) {
        if (Objects.equals(newSelectedBlockHash, selectedBlockHash))
            return;
        if (selectedBlockHash != null)
            notifyItemChanged(positionOf(selectedBlockHash), EnumSet.of(ChangeType.SELECTION));
        if (newSelectedBlockHash != null)
            notifyItemChanged(positionOf(newSelectedBlockHash), EnumSet.of(ChangeType.SELECTION));
        this.selectedBlockHash = newSelectedBlockHash;
    }

    @MainThread
    public int positionOf(final Sha256Hash blockHash) {
        if (blockHash != null) {
            final List<ListItem> list = getCurrentList();
            for (int i = 0; i < list.size(); i++) {
                final ListItem item = list.get(i);
                if (item instanceof ListItem.BlockItem && ((ListItem.BlockItem) item).blockHash.equals(blockHash))
                    return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemViewType(final int position) {
        final ListItem listItem = getItem(position);
        if (listItem instanceof ListItem.BlockItem)
            return VIEW_TYPE_BLOCK;
        else if (listItem instanceof ListItem.SeparatorItem)
            return VIEW_TYPE_SEPARATOR;
        else
            throw new IllegalStateException();
    }

    @Override
    public long getItemId(final int position) {
        final ListItem listItem = getItem(position);
        return listItem.id;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        if (viewType == VIEW_TYPE_BLOCK)
            return new BlockViewHolder(inflater.inflate(R.layout.block_row, parent, false));
        else if (viewType == VIEW_TYPE_SEPARATOR)
            return new SeparatorViewHolder(inflater.inflate(R.layout.row_separator, parent, false));
        else
            throw new IllegalStateException();
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position,
                                 final List<Object> payloads) {
        final boolean fullBind = payloads.isEmpty();
        final EnumSet<ChangeType> changes = EnumSet.noneOf(ChangeType.class);
        for (final Object payload : payloads)
            changes.addAll((EnumSet<ChangeType>) payload);

        final ListItem listItem = getItem(position);
        if (holder instanceof BlockViewHolder) {
            final BlockViewHolder blockHolder = (BlockViewHolder) holder;
            final ListItem.BlockItem blockItem = (ListItem.BlockItem) listItem;
            if (fullBind || changes.contains(ChangeType.SELECTION)) {
                final boolean isSelected = blockItem.blockHash.equals(selectedBlockHash);
                holder.itemView.setSelected(isSelected);
                ((CardView) holder.itemView).setCardElevation(isSelected ? cardElevationSelected : 0);
                blockHolder.contextBar.setVisibility(View.GONE);
                if (contextMenuCallback != null && isSelected) {
                    final Menu menu = blockHolder.contextBar.getMenu();
                    menu.clear();
                    contextMenuCallback.onInflateBlockContextMenu(menuInflater, menu);
                    if (menu.hasVisibleItems()) {
                        blockHolder.contextBar.setVisibility(View.VISIBLE);
                        blockHolder.contextBar.setOnMenuItemClickListener(item ->
                                contextMenuCallback.onClickBlockContextMenuItem(item, blockItem.blockHash));
                    }
                }
            }
            if (fullBind || changes.contains(ChangeType.TIME)) {
                blockHolder.timeView.setText(blockItem.time);
            }
            if (fullBind || changes.contains(ChangeType.TRANSACTIONS)) {
                final int transactionChildCount =
                        blockHolder.transactionsViewGroup.getChildCount() - ROW_BASE_CHILD_COUNT;
                int iTransactionView = 0;
                for (final ListItem.TxItem tx : blockItem.transactions) {
                    final View view;
                    if (iTransactionView < transactionChildCount) {
                        view = blockHolder.transactionsViewGroup.getChildAt(ROW_INSERT_INDEX + iTransactionView);
                    } else {
                        view = inflater.inflate(R.layout.block_row_transaction, blockHolder.transactionsViewGroup,
                                false);
                        blockHolder.transactionsViewGroup.addView(view, ROW_INSERT_INDEX + iTransactionView);
                    }
                    bindTransactionView(view, blockItem.format, tx);
                    iTransactionView++;
                }
                final int leftoverTransactionViews = transactionChildCount - iTransactionView;
                if (leftoverTransactionViews > 0)
                    blockHolder.transactionsViewGroup.removeViews(ROW_INSERT_INDEX + iTransactionView,
                            leftoverTransactionViews);
            }
            if (fullBind) {
                blockHolder.heightView.setText(Integer.toString(blockItem.height));
                blockHolder.hashView.setText(WalletUtils.formatHash(null, blockItem.blockHash.toString(), 8, 0, ' '));

                final OnClickListener onClickListener = this.onClickListener;
                if (onClickListener != null)
                    holder.itemView.setOnClickListener(v -> onClickListener.onBlockClick(v, blockItem.blockHash));
            }
        } else if (holder instanceof SeparatorViewHolder) {
            final SeparatorViewHolder separatorHolder = (SeparatorViewHolder) holder;
            final ListItem.SeparatorItem separatorItem = (ListItem.SeparatorItem) listItem;
            separatorHolder.label.setText(separatorItem.label);
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

    public static class BlockViewHolder extends RecyclerView.ViewHolder {
        private final ViewGroup transactionsViewGroup;
        private final TextView heightView;
        private final TextView timeView;
        private final TextView hashView;
        private final Toolbar contextBar;

        private BlockViewHolder(final View itemView) {
            super(itemView);
            transactionsViewGroup = itemView.findViewById(R.id.block_list_row_transactions_group);
            heightView = itemView.findViewById(R.id.block_list_row_height);
            timeView = itemView.findViewById(R.id.block_list_row_time);
            hashView = itemView.findViewById(R.id.block_list_row_hash);
            contextBar = itemView.findViewById(R.id.block_list_row_context_bar);
        }
    }
}
