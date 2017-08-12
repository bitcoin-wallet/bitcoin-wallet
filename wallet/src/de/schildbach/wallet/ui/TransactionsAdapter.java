/*
 * Copyright 2011-2015 the original author or authors.
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.util.CircularProgressView;
import de.schildbach.wallet.util.Formats;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public class TransactionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public enum Warning {
        BACKUP, STORAGE_ENCRYPTION, CHAIN_FORKING
    }

    private final Context context;
    private final LayoutInflater inflater;

    private final boolean useCards;
    private final Wallet wallet;
    private final int maxConnectedPeers;
    @Nullable
    private final OnClickListener onClickListener;

    private final List<Transaction> transactions = new ArrayList<Transaction>();
    private MonetaryFormat format;
    private Warning warning = null;

    private long selectedItemId = RecyclerView.NO_ID;

    private final int colorBackground, colorBackgroundSelected;
    private final int colorSignificant, colorLessSignificant, colorInsignificant;
    private final int colorValuePositve, colorValueNegative;
    private final int colorError;
    private final String textCoinBase;
    private final String textInternal;
    private final float textSizeNormal;

    private static final String CONFIDENCE_SYMBOL_IN_CONFLICT = "\u26A0"; // warning sign
    private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
    private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

    private static final int VIEW_TYPE_TRANSACTION = 0;
    private static final int VIEW_TYPE_WARNING = 1;

    private Map<Sha256Hash, TransactionCacheEntry> transactionCache = new HashMap<Sha256Hash, TransactionCacheEntry>();

    private static class TransactionCacheEntry {
        private final Coin value;
        private final boolean sent;
        private final boolean self;
        private final boolean showFee;
        @Nullable
        private final Address address;
        @Nullable
        private final String addressLabel;

        private TransactionCacheEntry(final Coin value, final boolean sent, final boolean self, final boolean showFee,
                final @Nullable Address address, final @Nullable String addressLabel) {
            this.value = value;
            this.sent = sent;
            this.self = self;
            this.showFee = showFee;
            this.address = address;
            this.addressLabel = addressLabel;
        }
    }

    public TransactionsAdapter(final Context context, final Wallet wallet, final boolean useCards,
            final int maxConnectedPeers, final @Nullable OnClickListener onClickListener) {
        this.context = context;
        inflater = LayoutInflater.from(context);

        this.useCards = useCards;
        this.wallet = wallet;
        this.maxConnectedPeers = maxConnectedPeers;
        this.onClickListener = onClickListener;

        final Resources res = context.getResources();
        colorBackground = res.getColor(R.color.bg_bright);
        colorBackgroundSelected = res.getColor(R.color.bg_panel);
        colorSignificant = res.getColor(R.color.fg_significant);
        colorLessSignificant = res.getColor(R.color.fg_less_significant);
        colorInsignificant = res.getColor(R.color.fg_insignificant);
        colorValuePositve = res.getColor(R.color.fg_value_positive);
        colorValueNegative = res.getColor(R.color.fg_value_negative);
        colorError = res.getColor(R.color.fg_error);
        textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
        textInternal = context.getString(R.string.symbol_internal) + " "
                + context.getString(R.string.wallet_transactions_fragment_internal);
        textSizeNormal = res.getDimension(R.dimen.font_size_normal);

        setHasStableIds(true);
    }

    public void setFormat(final MonetaryFormat format) {
        this.format = format.noCode();

        notifyDataSetChanged();
    }

    public void setWarning(final Warning warning) {
        this.warning = warning;

        notifyDataSetChanged();
    }

    public void clear() {
        transactions.clear();

        notifyDataSetChanged();
    }

    public void replace(final Transaction tx) {
        transactions.clear();
        transactions.add(tx);

        notifyDataSetChanged();
    }

    public void replace(final Collection<Transaction> transactions) {
        this.transactions.clear();
        this.transactions.addAll(transactions);

        notifyDataSetChanged();
    }

    public void setSelectedItemId(final long itemId) {
        selectedItemId = itemId;

        notifyDataSetChanged();
    }

    public void clearCacheAndNotifyDataSetChanged() {
        transactionCache.clear();

        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        int count = transactions.size();

        if (warning != null)
            count++;

        return count;
    }

    @Override
    public long getItemId(int position) {
        if (position == RecyclerView.NO_POSITION)
            return RecyclerView.NO_ID;

        if (warning != null) {
            if (position == 0)
                return 0;
            else
                position--;
        }

        return WalletUtils.longHash(transactions.get(position).getHash());
    }

    @Override
    public int getItemViewType(final int position) {
        if (position == 0 && warning != null)
            return VIEW_TYPE_WARNING;
        else
            return VIEW_TYPE_TRANSACTION;
    }

    public RecyclerView.ViewHolder createTransactionViewHolder(final ViewGroup parent) {
        return createViewHolder(parent, VIEW_TYPE_TRANSACTION);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        if (viewType == VIEW_TYPE_TRANSACTION) {
            if (useCards) {
                final CardView cardView = (CardView) inflater.inflate(R.layout.transaction_row_card, parent, false);
                cardView.setPreventCornerOverlap(false);
                cardView.setUseCompatPadding(true);
                return new TransactionViewHolder(cardView);
            } else {
                return new TransactionViewHolder(inflater.inflate(R.layout.transaction_row, parent, false));
            }
        } else if (viewType == VIEW_TYPE_WARNING) {
            return new WarningViewHolder(inflater.inflate(R.layout.transaction_row_warning, parent, false));
        } else {
            throw new IllegalStateException("unknown type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof TransactionViewHolder) {
            final TransactionViewHolder transactionHolder = (TransactionViewHolder) holder;

            final long itemId = getItemId(position);
            transactionHolder.itemView.setActivated(itemId == selectedItemId);

            final Transaction tx = transactions.get(position - (warning != null ? 1 : 0));
            transactionHolder.bind(tx);

            transactionHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    setSelectedItemId(getItemId(transactionHolder.getAdapterPosition()));
                }
            });

            if (onClickListener != null) {
                transactionHolder.menuView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        onClickListener.onTransactionMenuClick(v, tx);
                    }
                });
            }
        } else if (holder instanceof WarningViewHolder) {
            final WarningViewHolder warningHolder = (WarningViewHolder) holder;

            if (warning == Warning.BACKUP) {
                if (transactions.size() == 1) {
                    warningHolder.messageView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    warningHolder.messageView
                            .setText(Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_backup)));
                } else {
                    warningHolder.messageView
                            .setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_grey600_24dp, 0, 0, 0);
                    warningHolder.messageView.setText(
                            Html.fromHtml(context.getString(R.string.wallet_disclaimer_fragment_remind_backup)));
                }
            } else if (warning == Warning.STORAGE_ENCRYPTION) {
                warningHolder.messageView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                warningHolder.messageView.setText(
                        Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_storage_encryption)));
            } else if (warning == Warning.CHAIN_FORKING) {
                warningHolder.messageView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_grey600_24dp, 0,
                        0, 0);
                warningHolder.messageView.setText(
                        Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_chain_forking)));
            }
        }
    }

    public interface OnClickListener {
        void onTransactionMenuClick(View view, Transaction tx);

        void onWarningClick();
    }

    private class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final View extendTimeView;
        private final TextView fullTimeView;
        private final View extendAddressView;
        private final CircularProgressView confidenceCircularNormalView, confidenceCircularSelectedView;
        private final TextView confidenceTextualNormalView, confidenceTextualSelectedView;
        private final TextView timeView;
        private final TextView addressView;
        private final CurrencyTextView valueView;
        private final View extendFeeView;
        private final CurrencyTextView feeView;
        private final View extendFiatView;
        private final CurrencyTextView fiatView;
        private final View extendMessageView;
        private final TextView messageView;
        private final ImageButton menuView;

        private TransactionViewHolder(final View itemView) {
            super(itemView);

            extendTimeView = itemView.findViewById(R.id.transaction_row_extend_time);
            fullTimeView = (TextView) itemView.findViewById(R.id.transaction_row_full_time);
            extendAddressView = itemView.findViewById(R.id.transaction_row_extend_address);
            confidenceCircularNormalView = (CircularProgressView) itemView
                    .findViewById(R.id.transaction_row_confidence_circular);
            confidenceCircularSelectedView = (CircularProgressView) itemView
                    .findViewById(R.id.transaction_row_confidence_circular_selected);
            confidenceTextualNormalView = (TextView) itemView.findViewById(R.id.transaction_row_confidence_textual);
            confidenceTextualSelectedView = (TextView) itemView
                    .findViewById(R.id.transaction_row_confidence_textual_selected);
            timeView = (TextView) itemView.findViewById(R.id.transaction_row_time);
            addressView = (TextView) itemView.findViewById(R.id.transaction_row_address);
            valueView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_value);
            extendFeeView = itemView.findViewById(R.id.transaction_row_extend_fee);
            feeView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_fee);
            extendFiatView = itemView.findViewById(R.id.transaction_row_extend_fiat);
            fiatView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_fiat);
            extendMessageView = itemView.findViewById(R.id.transaction_row_extend_message);
            messageView = (TextView) itemView.findViewById(R.id.transaction_row_message);
            menuView = (ImageButton) itemView.findViewById(R.id.transaction_row_menu);
        }

        private void bind(final Transaction tx) {
            if (itemView instanceof CardView)
                ((CardView) itemView)
                        .setCardBackgroundColor(itemView.isActivated() ? colorBackgroundSelected : colorBackground);

            final TransactionConfidence confidence = tx.getConfidence();
            final ConfidenceType confidenceType = confidence.getConfidenceType();
            final boolean isOwn = confidence.getSource().equals(TransactionConfidence.Source.SELF);
            final boolean isCoinBase = tx.isCoinBase();
            final Transaction.Purpose purpose = tx.getPurpose();
            final Coin fee = tx.getFee();
            final String[] memo = Formats.sanitizeMemo(tx.getMemo());

            TransactionCacheEntry txCache = transactionCache.get(tx.getHash());
            if (txCache == null) {
                final Coin value = tx.getValue(wallet);
                final boolean sent = value.signum() < 0;
                final boolean self = WalletUtils.isEntirelySelf(tx, wallet);
                final boolean showFee = sent && fee != null && !fee.isZero();
                final Address address;
                if (sent)
                    address = WalletUtils.getToAddressOfSent(tx, wallet);
                else
                    address = WalletUtils.getWalletAddressOfReceived(tx, wallet);
                final String addressLabel = address != null
                        ? AddressBookProvider.resolveLabel(context, address.toBase58()) : null;

                txCache = new TransactionCacheEntry(value, sent, self, showFee, address, addressLabel);
                transactionCache.put(tx.getHash(), txCache);
            }

            final int textColor, lessSignificantColor, valueColor;
            if (confidenceType == ConfidenceType.DEAD) {
                textColor = colorError;
                lessSignificantColor = colorError;
                valueColor = colorError;
            } else if (DefaultCoinSelector.isSelectable(tx)) {
                textColor = colorSignificant;
                lessSignificantColor = colorLessSignificant;
                valueColor = txCache.sent ? colorValueNegative : colorValuePositve;
            } else {
                textColor = colorInsignificant;
                lessSignificantColor = colorInsignificant;
                valueColor = colorInsignificant;
            }

            // confidence
            final CircularProgressView confidenceCircularView = itemView.isActivated() ? confidenceCircularSelectedView
                    : confidenceCircularNormalView;
            final TextView confidenceTextualView = itemView.isActivated() ? confidenceTextualSelectedView
                    : confidenceTextualNormalView;
            (itemView.isActivated() ? confidenceCircularNormalView : confidenceCircularSelectedView)
                    .setVisibility(View.INVISIBLE);
            (itemView.isActivated() ? confidenceTextualNormalView : confidenceTextualSelectedView)
                    .setVisibility(View.GONE);
            if (confidenceType == ConfidenceType.PENDING) {
                confidenceCircularView.setVisibility(View.VISIBLE);
                confidenceTextualView.setVisibility(View.GONE);

                confidenceCircularView.setProgress(1);
                confidenceCircularView.setMaxProgress(1);
                confidenceCircularView.setSize(confidence.numBroadcastPeers());
                confidenceCircularView.setMaxSize(maxConnectedPeers / 2); // magic value
                confidenceCircularView.setColors(colorInsignificant, Color.TRANSPARENT);
            } else if (confidenceType == ConfidenceType.IN_CONFLICT) {
                confidenceCircularView.setVisibility(View.GONE);
                confidenceTextualView.setVisibility(View.VISIBLE);

                confidenceTextualView.setText(CONFIDENCE_SYMBOL_IN_CONFLICT);
                confidenceTextualView.setTextColor(colorError);
                confidenceTextualView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeNormal * 0.85f);
            } else if (confidenceType == ConfidenceType.BUILDING) {
                confidenceCircularView.setVisibility(View.VISIBLE);
                confidenceTextualView.setVisibility(View.GONE);

                confidenceCircularView.setProgress(confidence.getDepthInBlocks());
                confidenceCircularView.setMaxProgress(isCoinBase
                        ? Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth() : Constants.MAX_NUM_CONFIRMATIONS);
                confidenceCircularView.setSize(1);
                confidenceCircularView.setMaxSize(1);
                confidenceCircularView.setColors(valueColor, Color.TRANSPARENT);
            } else if (confidenceType == ConfidenceType.DEAD) {
                confidenceCircularView.setVisibility(View.GONE);
                confidenceTextualView.setVisibility(View.VISIBLE);

                confidenceTextualView.setText(CONFIDENCE_SYMBOL_DEAD);
                confidenceTextualView.setTextColor(colorError);
                confidenceTextualView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeNormal);
            } else {
                confidenceCircularView.setVisibility(View.GONE);
                confidenceTextualView.setVisibility(View.VISIBLE);

                confidenceTextualView.setText(CONFIDENCE_SYMBOL_UNKNOWN);
                confidenceTextualView.setTextColor(colorInsignificant);
                confidenceTextualView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeNormal);
            }

            // time
            final Date time = tx.getUpdateTime();
            if (!itemView.isActivated()) {
                extendTimeView.setVisibility(View.GONE);

                timeView.setVisibility(View.VISIBLE);
                timeView.setText(DateUtils.getRelativeTimeSpanString(context, time.getTime()));
                timeView.setTextColor(textColor);
            } else {
                extendTimeView.setVisibility(View.VISIBLE);
                fullTimeView.setText(DateUtils.formatDateTime(context, time.getTime(),
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
                fullTimeView.setTextColor(textColor);

                timeView.setVisibility(View.GONE);
            }

            // address
            if (isCoinBase) {
                addressView.setTextColor(textColor);
                addressView.setTypeface(Typeface.DEFAULT_BOLD);
                addressView.setText(textCoinBase);
            } else if (purpose == Purpose.KEY_ROTATION || txCache.self) {
                addressView.setTextColor(lessSignificantColor);
                addressView.setTypeface(Typeface.DEFAULT_BOLD);
                addressView.setText(textInternal);
            } else if (purpose == Purpose.RAISE_FEE) {
                addressView.setText(null);
            } else if (txCache.addressLabel != null) {
                addressView.setTextColor(textColor);
                addressView.setTypeface(Typeface.DEFAULT_BOLD);
                addressView.setText(txCache.addressLabel);
            } else if (memo != null && memo.length >= 2) {
                addressView.setTextColor(textColor);
                addressView.setTypeface(Typeface.DEFAULT_BOLD);
                addressView.setText(memo[1]);
            } else if (txCache.address != null) {
                addressView.setTextColor(lessSignificantColor);
                addressView.setTypeface(Typeface.DEFAULT);
                addressView.setText(WalletUtils.formatAddress(txCache.address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
                        Constants.ADDRESS_FORMAT_LINE_SIZE));
            } else {
                addressView.setTextColor(lessSignificantColor);
                addressView.setTypeface(Typeface.DEFAULT);
                addressView.setText("?");
            }
            addressView.setSingleLine(!itemView.isActivated());
            extendAddressView
                    .setVisibility(!itemView.isActivated() || purpose != Purpose.RAISE_FEE ? View.VISIBLE : View.GONE);

            // fee
            if (txCache.showFee) {
                extendFeeView.setVisibility(itemView.isActivated()
                        || (confidenceType == ConfidenceType.PENDING && purpose != Purpose.RAISE_FEE) ? View.VISIBLE
                                : View.GONE);
                feeView.setAlwaysSigned(true);
                feeView.setFormat(format);
                feeView.setAmount(fee.negate());
            } else {
                extendFeeView.setVisibility(View.GONE);
            }

            // value
            valueView.setAlwaysSigned(true);
            valueView.setFormat(format);
            final Coin value;
            if (purpose == Purpose.RAISE_FEE) {
                valueView.setTextColor(colorInsignificant);
                value = fee.negate();
            } else {
                valueView.setTextColor(valueColor);
                value = txCache.showFee ? txCache.value.add(fee) : txCache.value;
            }
            valueView.setAmount(value);
            valueView.setVisibility(!value.isZero() ? View.VISIBLE : View.GONE);

            // fiat value
            final ExchangeRate exchangeRate = tx.getExchangeRate();
            if (exchangeRate != null) {
                extendFiatView.setVisibility(View.VISIBLE);
                fiatView.setAlwaysSigned(true);
                fiatView.setPrefixColor(colorInsignificant);
                fiatView.setFormat(Constants.LOCAL_FORMAT.code(0,
                        Constants.PREFIX_ALMOST_EQUAL_TO + exchangeRate.fiat.getCurrencyCode()));
                fiatView.setAmount(exchangeRate.coinToFiat(txCache.value));
            } else {
                extendFiatView.setVisibility(View.GONE);
            }

            // message
            extendMessageView.setVisibility(View.GONE);
            messageView.setSingleLine(false);

            if (purpose == Purpose.KEY_ROTATION) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(
                        Html.fromHtml(context.getString(R.string.transaction_row_message_purpose_key_rotation)));
                messageView.setTextColor(colorSignificant);
            } else if (purpose == Purpose.RAISE_FEE) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_purpose_raise_fee);
                messageView.setTextColor(colorInsignificant);
            } else if (isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_own_unbroadcasted);
                messageView.setTextColor(colorInsignificant);
            } else if (!isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_direct);
                messageView.setTextColor(colorInsignificant);
            } else if (!txCache.sent && txCache.value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_dust);
                messageView.setTextColor(colorInsignificant);
            } else if (!txCache.sent && confidenceType == ConfidenceType.PENDING
                    && (tx.getUpdateTime() == null || wallet.getLastBlockSeenTimeSecs() * 1000
                            - tx.getUpdateTime().getTime() > Constants.DELAYED_TRANSACTION_THRESHOLD_MS)) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_unconfirmed_delayed);
                messageView.setTextColor(colorInsignificant);
            } else if (!txCache.sent && confidenceType == ConfidenceType.PENDING) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_unconfirmed_unlocked);
                messageView.setTextColor(colorInsignificant);
            } else if (!txCache.sent && confidenceType == ConfidenceType.IN_CONFLICT) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_in_conflict);
                messageView.setTextColor(colorInsignificant);
            } else if (!txCache.sent && confidenceType == ConfidenceType.DEAD) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_dead);
                messageView.setTextColor(colorError);
            } else if (!txCache.sent && WalletUtils.isPayToManyTransaction(tx)) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_pay_to_many);
                messageView.setTextColor(colorInsignificant);
            } else if (!txCache.sent && tx.isOptInFullRBF()) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(R.string.transaction_row_message_received_rbf);
                messageView.setTextColor(colorInsignificant);
            } else if (memo != null) {
                extendMessageView.setVisibility(View.VISIBLE);
                messageView.setText(memo[0]);
                messageView.setTextColor(colorInsignificant);
                messageView.setSingleLine(!itemView.isActivated());
            }

            // menu
            menuView.setVisibility(itemView.isActivated() ? View.VISIBLE : View.GONE);
        }
    }

    private class WarningViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageView;

        private WarningViewHolder(final View itemView) {
            super(itemView);

            messageView = (TextView) itemView.findViewById(R.id.transaction_row_warning_message);

            if (onClickListener != null) {
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        onClickListener.onWarningClick();
                    }
                });
            }
        }
    }
}
