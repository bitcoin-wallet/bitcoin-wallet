/*
 * Copyright 2013-2015 the original author or authors.
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
import java.util.Set;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

import android.content.Context;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public class BlockListAdapter extends RecyclerView.Adapter<BlockListAdapter.BlockViewHolder> {
    private static final int ROW_BASE_CHILD_COUNT = 2;
    private static final int ROW_INSERT_INDEX = 1;

    private final Context context;
    private final Wallet wallet;
    private final LayoutInflater inflater;
    @Nullable
    private final OnClickListener onClickListener;

    private MonetaryFormat format;

    private final List<StoredBlock> blocks = new ArrayList<StoredBlock>();
    private Set<Transaction> transactions;

    private final String textCoinBase;
    private final String textInternal;

    public BlockListAdapter(final Context context, final Wallet wallet,
            final @Nullable OnClickListener onClickListener) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.wallet = wallet;
        this.onClickListener = onClickListener;

        textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
        textInternal = context.getString(R.string.wallet_transactions_fragment_internal);

        setHasStableIds(true);
    }

    public void setFormat(final MonetaryFormat format) {
        this.format = format.noCode();

        notifyDataSetChanged();
    }

    public void clear() {
        blocks.clear();

        notifyDataSetChanged();
    }

    public void replace(final Collection<StoredBlock> blocks) {
        this.blocks.clear();
        this.blocks.addAll(blocks);

        notifyDataSetChanged();
    }

    public void clearTransactions() {
        transactions = null;

        notifyDataSetChanged();
    }

    public void replaceTransactions(final Set<Transaction> transactions) {
        this.transactions = transactions;

        notifyDataSetChanged();
    }

    public StoredBlock getItem(final int position) {
        return blocks.get(position);
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    @Override
    public long getItemId(final int position) {
        return WalletUtils.longHash(blocks.get(position).getHeader().getHash());
    }

    @Override
    public BlockViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new BlockViewHolder(inflater.inflate(R.layout.block_row, parent, false));
    }

    @Override
    public void onBindViewHolder(final BlockViewHolder holder, final int position) {
        final StoredBlock storedBlock = getItem(position);
        final Block header = storedBlock.getHeader();

        holder.miningRewardAdjustmentView
                .setVisibility(isMiningRewardHalvingPoint(storedBlock) ? View.VISIBLE : View.GONE);
        holder.miningDifficultyAdjustmentView
                .setVisibility(isDifficultyTransitionPoint(storedBlock) ? View.VISIBLE : View.GONE);

        final int height = storedBlock.getHeight();
        holder.heightView.setText(Integer.toString(height));

        final long timeMs = header.getTimeSeconds() * DateUtils.SECOND_IN_MILLIS;
        if (timeMs < System.currentTimeMillis() - DateUtils.MINUTE_IN_MILLIS)
            holder.timeView.setText(DateUtils.getRelativeDateTimeString(context, timeMs, DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS, 0));
        else
            holder.timeView.setText(R.string.block_row_now);

        holder.hashView.setText(WalletUtils.formatHash(null, header.getHashAsString(), 8, 0, ' '));

        final int transactionChildCount = holder.transactionsViewGroup.getChildCount() - ROW_BASE_CHILD_COUNT;
        int iTransactionView = 0;

        if (transactions != null) {
            for (final Transaction tx : transactions) {
                if (tx.getAppearsInHashes().containsKey(header.getHash())) {
                    final View view;
                    if (iTransactionView < transactionChildCount) {
                        view = holder.transactionsViewGroup.getChildAt(ROW_INSERT_INDEX + iTransactionView);
                    } else {
                        view = inflater.inflate(R.layout.block_row_transaction, null);
                        holder.transactionsViewGroup.addView(view, ROW_INSERT_INDEX + iTransactionView);
                    }

                    bindView(view, tx);

                    iTransactionView++;
                }
            }
        }

        final int leftoverTransactionViews = transactionChildCount - iTransactionView;
        if (leftoverTransactionViews > 0)
            holder.transactionsViewGroup.removeViews(ROW_INSERT_INDEX + iTransactionView, leftoverTransactionViews);

        if (onClickListener != null) {
            holder.menuView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    onClickListener.onBlockMenuClick(v, storedBlock);
                }
            });
        }
    }

    public void bindView(final View row, final Transaction tx) {
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

        // receiving or sending
        final TextView rowFromTo = (TextView) row.findViewById(R.id.block_row_transaction_fromto);
        if (isInternal || self)
            rowFromTo.setText(R.string.symbol_internal);
        else if (sent)
            rowFromTo.setText(R.string.symbol_to);
        else
            rowFromTo.setText(R.string.symbol_from);

        // address
        final TextView rowAddress = (TextView) row.findViewById(R.id.block_row_transaction_address);
        final String label;
        if (isCoinBase)
            label = textCoinBase;
        else if (isInternal || self)
            label = textInternal;
        else if (address != null)
            label = AddressBookProvider.resolveLabel(context, address.toBase58());
        else
            label = "?";
        rowAddress.setText(label != null ? label : address.toBase58());
        rowAddress.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

        // value
        final CurrencyTextView rowValue = (CurrencyTextView) row.findViewById(R.id.block_row_transaction_value);
        rowValue.setAlwaysSigned(true);
        rowValue.setFormat(format);
        rowValue.setAmount(value);
    }

    public interface OnClickListener {
        void onBlockMenuClick(View view, StoredBlock block);
    }

    public static class BlockViewHolder extends RecyclerView.ViewHolder {
        private final ViewGroup transactionsViewGroup;
        private final View miningRewardAdjustmentView;
        private final View miningDifficultyAdjustmentView;
        private final TextView heightView;
        private final TextView timeView;
        private final TextView hashView;
        private final ImageButton menuView;

        private BlockViewHolder(final View itemView) {
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

    public final boolean isMiningRewardHalvingPoint(final StoredBlock storedPrev) {
        return ((storedPrev.getHeight() + 1) % 210000) == 0;
    }

    public final boolean isDifficultyTransitionPoint(final StoredBlock storedPrev) {
        return ((storedPrev.getHeight() + 1) % Constants.NETWORK_PARAMETERS.getInterval()) == 0;
    }
}
