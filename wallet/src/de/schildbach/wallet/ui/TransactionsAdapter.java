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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.ui.TransactionsAdapter.ListItem.TransactionItem;
import de.schildbach.wallet.util.Formats;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.Wallet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Andreas Schildbach
 */
public class TransactionsAdapter extends ListAdapter<TransactionsAdapter.ListItem, RecyclerView.ViewHolder> {
    public static List<ListItem> buildListItems(final Context context, final List<Transaction> transactions,
            final WarningType warning, final @Nullable Wallet wallet,
            final @Nullable Map<String, AddressBookEntry> addressBook, final MonetaryFormat format,
            final int maxConnectedPeers) {
        final MonetaryFormat noCodeFormat = format.noCode();
        final List<ListItem> items = new ArrayList<>(transactions.size() + 1);
        if (warning != null)
            items.add(new ListItem.WarningItem(warning));
        for (final Transaction tx : transactions)
            items.add(new ListItem.TransactionItem(context, tx, wallet, addressBook, noCodeFormat, maxConnectedPeers));
        return items;
    }

    public static abstract class ListItem {
        // internal item id
        public final long id;

        private ListItem(final long id) {
            this.id = id;
        }

        public static class TransactionItem extends ListItem {
            public final Sha256Hash transactionId;
            public final int confidenceCircularProgress, confidenceCircularMaxProgress;
            public final int confidenceCircularSize, confidenceCircularMaxSize;
            public final int confidenceCircularFillColor, confidenceCircularStrokeColor;
            @Nullable
            public final String confidenceTextual;
            public final int confidenceTextualColor;
            @Nullable
            public final Spanned confidenceMessage;
            public final boolean confidenceMessageOnlyShownWhenSelected;
            public final CharSequence time, timeSelected;
            public final int timeColor;
            @Nullable
            public final Spanned address;
            public final int addressColor;
            public final Typeface addressTypeface;
            @Nullable
            public final Coin fee;
            public final MonetaryFormat feeFormat;
            @Nullable
            public final Coin value;
            public final MonetaryFormat valueFormat;
            public final int valueColor;
            @Nullable
            public final Fiat fiat;
            @Nullable
            public final MonetaryFormat fiatFormat;
            public final int fiatPrefixColor;
            @Nullable
            public final Spanned message;
            public final int messageColor;

            public TransactionItem(final Context context, final Transaction tx, final @Nullable Wallet wallet,
                    final @Nullable Map<String, AddressBookEntry> addressBook, final MonetaryFormat format,
                    final int maxConnectedPeers) {
                super(id(tx.getTxId()));
                this.transactionId = tx.getTxId();

                final int colorSignificant = context.getColor(R.color.fg_significant);
                final int colorLessSignificant = context.getColor(R.color.fg_less_significant);
                final int colorInsignificant = context.getColor(R.color.fg_insignificant);
                final int colorValuePositive = context.getColor(R.color.fg_value_positive);
                final int colorValueNegative = context.getColor(R.color.fg_value_negative);
                final int colorError = context.getColor(R.color.fg_error);

                final Coin value = tx.getValue(wallet);
                final boolean sent = value.signum() < 0;
                final boolean self = WalletUtils.isEntirelySelf(tx, wallet);
                final TransactionConfidence confidence = tx.getConfidence();
                final ConfidenceType confidenceType = confidence.getConfidenceType();
                final boolean isOwn = confidence.getSource().equals(TransactionConfidence.Source.SELF);
                final Transaction.Purpose purpose = tx.getPurpose();
                final String[] memo = Formats.sanitizeMemo(tx.getMemo());

                final int textColor, lessSignificantColor, valueColor;
                if (confidenceType == ConfidenceType.DEAD) {
                    textColor = colorError;
                    lessSignificantColor = colorError;
                    valueColor = colorError;
                } else if (DefaultCoinSelector.isSelectable(tx)) {
                    textColor = colorSignificant;
                    lessSignificantColor = colorLessSignificant;
                    valueColor = sent ? colorValueNegative : colorValuePositive;
                } else {
                    textColor = colorInsignificant;
                    lessSignificantColor = colorInsignificant;
                    valueColor = sent ? colorValueNegative : colorValuePositive;
                }

                // confidence
                if (confidenceType == ConfidenceType.PENDING) {
                    this.confidenceCircularMaxProgress = 1;
                    this.confidenceCircularProgress = 1;
                    this.confidenceCircularMaxSize = maxConnectedPeers / 2; // magic value
                    this.confidenceCircularSize = confidence.numBroadcastPeers();
                    this.confidenceCircularFillColor = colorInsignificant;
                    this.confidenceCircularStrokeColor = Color.TRANSPARENT;
                    this.confidenceTextual = null;
                    this.confidenceTextualColor = 0;
                    this.confidenceMessage = sent && confidence.numBroadcastPeers() == 0
                            ? SpannedString.valueOf(
                                    context.getString(R.string.transaction_row_confidence_message_sent_unbroadcasted))
                            : null;
                    this.confidenceMessageOnlyShownWhenSelected = false;
                } else if (confidenceType == ConfidenceType.IN_CONFLICT) {
                    this.confidenceTextual = CONFIDENCE_SYMBOL_IN_CONFLICT;
                    this.confidenceTextualColor = colorError;
                    this.confidenceCircularMaxProgress = 0;
                    this.confidenceCircularProgress = 0;
                    this.confidenceCircularMaxSize = 0;
                    this.confidenceCircularSize = 0;
                    this.confidenceCircularFillColor = 0;
                    this.confidenceCircularStrokeColor = 0;
                    this.confidenceMessage = null;
                    this.confidenceMessageOnlyShownWhenSelected = false;
                } else if (confidenceType == ConfidenceType.BUILDING) {
                    this.confidenceCircularMaxProgress = tx.isCoinBase()
                            ? Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth()
                            : Constants.MAX_NUM_CONFIRMATIONS;
                    this.confidenceCircularProgress = Math.min(confidence.getDepthInBlocks(),
                            this.confidenceCircularMaxProgress);
                    this.confidenceCircularMaxSize = 1;
                    this.confidenceCircularSize = 1;
                    this.confidenceCircularFillColor = ColorUtils.blendARGB(colorValueNegative, colorValuePositive,
                            (float) (this.confidenceCircularProgress - 1) / (this.confidenceCircularMaxProgress - 1));
                    this.confidenceCircularStrokeColor = Color.TRANSPARENT;
                    this.confidenceTextual = null;
                    this.confidenceTextualColor = 0;
                    this.confidenceMessage = SpannedString.valueOf(
                            context.getString(sent ? R.string.transaction_row_confidence_message_sent_successful
                                    : R.string.transaction_row_confidence_message_received_successful));
                    this.confidenceMessageOnlyShownWhenSelected = true;
                } else if (confidenceType == ConfidenceType.DEAD) {
                    this.confidenceTextual = CONFIDENCE_SYMBOL_DEAD;
                    this.confidenceTextualColor = colorError;
                    this.confidenceCircularMaxProgress = 0;
                    this.confidenceCircularProgress = 0;
                    this.confidenceCircularMaxSize = 0;
                    this.confidenceCircularSize = 0;
                    this.confidenceCircularFillColor = 0;
                    this.confidenceCircularStrokeColor = 0;
                    this.confidenceMessage = SpannedString
                            .valueOf(context.getString(sent ? R.string.transaction_row_confidence_message_sent_failed
                                    : R.string.transaction_row_confidence_message_received_failed));
                    this.confidenceMessageOnlyShownWhenSelected = false;
                } else {
                    this.confidenceTextual = CONFIDENCE_SYMBOL_UNKNOWN;
                    this.confidenceTextualColor = colorInsignificant;
                    this.confidenceCircularMaxProgress = 0;
                    this.confidenceCircularProgress = 0;
                    this.confidenceCircularMaxSize = 0;
                    this.confidenceCircularSize = 0;
                    this.confidenceCircularFillColor = 0;
                    this.confidenceCircularStrokeColor = 0;
                    this.confidenceMessage = null;
                    this.confidenceMessageOnlyShownWhenSelected = false;
                }

                // time
                final Date time = tx.getUpdateTime();
                this.time = DateUtils.getRelativeTimeSpanString(context, time.getTime());
                this.timeSelected = DateUtils.formatDateTime(context, time.getTime(),
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
                this.timeColor = textColor;

                // address
                final Address address = sent ? WalletUtils.getToAddressOfSent(tx, wallet)
                        : WalletUtils.getWalletAddressOfReceived(tx, wallet);
                final String addressLabel;
                if (addressBook == null || address == null) {
                    addressLabel = null;
                } else {
                    final AddressBookEntry entry = addressBook.get(address.toString());
                    if (entry != null)
                        addressLabel = entry.getLabel();
                    else
                        addressLabel = null;
                }
                if (tx.isCoinBase()) {
                    this.address = SpannedString
                            .valueOf(context.getString(R.string.wallet_transactions_fragment_coinbase));
                    this.addressColor = textColor;
                    this.addressTypeface = Typeface.DEFAULT_BOLD;
                } else if (purpose == Purpose.RAISE_FEE) {
                    this.address = null;
                    this.addressColor = 0;
                    this.addressTypeface = Typeface.DEFAULT;
                } else if (purpose == Purpose.KEY_ROTATION || self) {
                    this.address = SpannedString.valueOf(context.getString(R.string.symbol_internal) + " "
                            + context.getString(R.string.wallet_transactions_fragment_internal));
                    this.addressColor = lessSignificantColor;
                    this.addressTypeface = Typeface.DEFAULT_BOLD;
                } else if (addressLabel != null) {
                    this.address = SpannedString.valueOf(addressLabel);
                    this.addressColor = textColor;
                    this.addressTypeface = Typeface.DEFAULT_BOLD;
                } else if (memo != null && memo.length >= 2) {
                    this.address = SpannedString.valueOf(memo[1]);
                    this.addressColor = textColor;
                    this.addressTypeface = Typeface.DEFAULT_BOLD;
                } else if (address != null) {
                    this.address = WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
                            Constants.ADDRESS_FORMAT_LINE_SIZE);
                    this.addressColor = lessSignificantColor;
                    this.addressTypeface = Typeface.DEFAULT;
                } else {
                    this.address = SpannedString.valueOf("?");
                    this.addressColor = lessSignificantColor;
                    this.addressTypeface = Typeface.DEFAULT;
                }

                // fee
                final Coin fee = tx.getFee();
                final boolean showFee = sent && fee != null && !fee.isZero();
                this.feeFormat = format;
                this.fee = showFee ? fee.negate() : null;

                // value
                this.valueFormat = format;
                if (purpose == Purpose.RAISE_FEE) {
                    this.valueColor = valueColor;
                    this.value = fee.negate();
                } else if (value.isZero()) {
                    this.valueColor = 0;
                    this.value = null;
                } else {
                    this.valueColor = valueColor;
                    this.value = showFee ? value.add(fee) : value;
                }

                // fiat value
                final ExchangeRate exchangeRate = tx.getExchangeRate();
                if (exchangeRate != null && !value.isZero()) {
                    this.fiat = exchangeRate.coinToFiat(value);
                    this.fiatFormat = Constants.LOCAL_FORMAT.code(0,
                            Constants.PREFIX_ALMOST_EQUAL_TO + exchangeRate.fiat.getCurrencyCode());
                    this.fiatPrefixColor = colorInsignificant;
                } else {
                    this.fiat = null;
                    this.fiatFormat = null;
                    this.fiatPrefixColor = 0;
                }

                // message
                if (purpose == Purpose.KEY_ROTATION) {
                    this.message = Html
                            .fromHtml(context.getString(R.string.transaction_row_message_purpose_key_rotation));
                    this.messageColor = colorSignificant;
                } else if (purpose == Purpose.RAISE_FEE) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_purpose_raise_fee));
                    this.messageColor = colorInsignificant;
                } else if (!isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_direct));
                    this.messageColor = colorInsignificant;
                } else if (!sent && value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_dust));
                    this.messageColor = colorInsignificant;
                } else if (!sent && confidenceType == ConfidenceType.PENDING
                        && (tx.getUpdateTime() == null || wallet.getLastBlockSeenTimeSecs() * 1000
                                - tx.getUpdateTime().getTime() > Constants.DELAYED_TRANSACTION_THRESHOLD_MS)) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_unconfirmed_delayed));
                    this.messageColor = colorInsignificant;
                } else if (!sent && confidenceType == ConfidenceType.PENDING) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_unconfirmed_unlocked));
                    this.messageColor = colorInsignificant;
                } else if (!sent && confidenceType == ConfidenceType.IN_CONFLICT) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_in_conflict));
                    this.messageColor = colorInsignificant;
                } else if (!sent && confidenceType == ConfidenceType.DEAD) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_dead));
                    this.messageColor = colorError;
                } else if (!sent && WalletUtils.isPayToManyTransaction(tx)) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_pay_to_many));
                    this.messageColor = colorInsignificant;
                } else if (!sent && tx.isOptInFullRBF()) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_rbf));
                    this.messageColor = colorInsignificant;
                } else if (memo != null) {
                    this.message = SpannedString.valueOf(memo[0]);
                    this.messageColor = colorInsignificant;
                } else {
                    this.message = null;
                    this.messageColor = 0;
                }
            }

            private static long id(final Sha256Hash txId) {
                return ByteBuffer.wrap(txId.getBytes()).getLong();
            }
        }

        public static class WarningItem extends ListItem {
            public final WarningType type;

            public WarningItem(final WarningType type) {
                super(id(type));
                this.type = type;
            }

            private static long id(final WarningType type) {
                return type.ordinal();
            }
        }
    }

    public enum WarningType {
        BACKUP, STORAGE_ENCRYPTION, CHAIN_FORKING
    }

    public interface OnClickListener {
        void onTransactionClick(View view, Sha256Hash transactionId);

        void onWarningClick(View view, WarningType warning);
    }

    public interface ContextMenuCallback {
        void onInflateTransactionContextMenu(MenuInflater inflater, Menu menu, Sha256Hash transactionId);

        boolean onClickTransactionContextMenuItem(MenuItem item, Sha256Hash transactionId);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final MenuInflater menuInflater;

    @Nullable
    private final OnClickListener onClickListener;
    @Nullable
    private final ContextMenuCallback contextMenuCallback;
    @Nullable
    private Sha256Hash selectedTransactionId;

    private static final String CONFIDENCE_SYMBOL_IN_CONFLICT = "\u26A0"; // warning sign
    private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
    private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

    private static final int VIEW_TYPE_TRANSACTION = 0;
    private static final int VIEW_TYPE_WARNING = 1;

    private enum ChangeType {
        CONFIDENCE, TIME, ADDRESS, FEE, VALUE, FIAT, MESSAGE, SELECTION
    }

    public TransactionsAdapter(final Context context, @Nullable final OnClickListener onClickListener,
                               @Nullable final ContextMenuCallback contextMenuCallback) {
        super(new DiffUtil.ItemCallback<ListItem>() {
            @Override
            public boolean areItemsTheSame(final ListItem oldItem, final ListItem newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(final ListItem oldItem, final ListItem newItem) {
                if (oldItem instanceof TransactionItem) {
                    final TransactionItem oldTransactionItem = (TransactionItem) oldItem;
                    final TransactionItem newTransactionItem = (TransactionItem) newItem;
                    if (!Objects.equals(oldTransactionItem.confidenceCircularProgress,
                            newTransactionItem.confidenceCircularProgress))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceCircularMaxProgress,
                            newTransactionItem.confidenceCircularMaxProgress))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceCircularSize,
                            newTransactionItem.confidenceCircularSize))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceCircularMaxSize,
                            newTransactionItem.confidenceCircularMaxSize))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceCircularFillColor,
                            newTransactionItem.confidenceCircularFillColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceCircularStrokeColor,
                            newTransactionItem.confidenceCircularStrokeColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceTextual, newTransactionItem.confidenceTextual))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceTextualColor,
                            newTransactionItem.confidenceTextualColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceMessage, newTransactionItem.confidenceMessage))
                        return false;
                    if (!Objects.equals(oldTransactionItem.confidenceMessageOnlyShownWhenSelected,
                            newTransactionItem.confidenceMessageOnlyShownWhenSelected))
                        return false;
                    if (!Objects.equals(oldTransactionItem.time, newTransactionItem.time))
                        return false;
                    if (!Objects.equals(oldTransactionItem.timeSelected, newTransactionItem.timeSelected))
                        return false;
                    if (!Objects.equals(oldTransactionItem.timeColor, newTransactionItem.timeColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.address, newTransactionItem.address))
                        return false;
                    if (!Objects.equals(oldTransactionItem.addressColor, newTransactionItem.addressColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.addressTypeface, newTransactionItem.addressTypeface))
                        return false;
                    if (!Objects.equals(oldTransactionItem.fee, newTransactionItem.fee))
                        return false;
                    if (!Objects.equals(oldTransactionItem.feeFormat.format(Coin.COIN).toString(),
                            newTransactionItem.feeFormat.format(Coin.COIN).toString()))
                        return false;
                    if (!Objects.equals(oldTransactionItem.value, newTransactionItem.value))
                        return false;
                    if (!Objects.equals(oldTransactionItem.valueFormat.format(Coin.COIN).toString(),
                            newTransactionItem.valueFormat.format(Coin.COIN).toString()))
                        return false;
                    if (!Objects.equals(oldTransactionItem.valueColor, newTransactionItem.valueColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.fiat, newTransactionItem.fiat))
                        return false;
                    if (!Objects.equals(
                            oldTransactionItem.fiatFormat != null
                                    ? oldTransactionItem.fiatFormat.format(Coin.COIN).toString() : null,
                            newTransactionItem.fiatFormat != null
                                    ? newTransactionItem.fiatFormat.format(Coin.COIN).toString() : null))
                        return false;
                    if (!Objects.equals(oldTransactionItem.fiatPrefixColor, newTransactionItem.fiatPrefixColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.message, newTransactionItem.message))
                        return false;
                    if (!Objects.equals(oldTransactionItem.messageColor, newTransactionItem.messageColor))
                        return false;
                    return true;
                } else {
                    return true;
                }
            }

            @Override
            public Object getChangePayload(final ListItem oldItem, final ListItem newItem) {
                final EnumSet<ChangeType> changes = EnumSet.noneOf(ChangeType.class);
                if (oldItem instanceof TransactionItem) {
                    final TransactionItem oldTransactionItem = (TransactionItem) oldItem;
                    final TransactionItem newTransactionItem = (TransactionItem) newItem;
                    if (!(Objects.equals(oldTransactionItem.confidenceCircularProgress,
                            newTransactionItem.confidenceCircularProgress)
                            && Objects.equals(oldTransactionItem.confidenceCircularMaxProgress,
                                    newTransactionItem.confidenceCircularMaxProgress)
                            && Objects.equals(oldTransactionItem.confidenceCircularSize,
                                    newTransactionItem.confidenceCircularSize)
                            && Objects.equals(oldTransactionItem.confidenceCircularMaxSize,
                                    newTransactionItem.confidenceCircularMaxSize)
                            && Objects.equals(oldTransactionItem.confidenceCircularFillColor,
                                    newTransactionItem.confidenceCircularFillColor)
                            && Objects.equals(oldTransactionItem.confidenceCircularStrokeColor,
                                    newTransactionItem.confidenceCircularStrokeColor)
                            && Objects.equals(oldTransactionItem.confidenceTextual,
                                    newTransactionItem.confidenceTextual)
                            && Objects.equals(oldTransactionItem.confidenceTextualColor,
                                    newTransactionItem.confidenceTextualColor)
                            && Objects.equals(oldTransactionItem.confidenceMessage,
                                    newTransactionItem.confidenceMessage)
                            && Objects.equals(oldTransactionItem.confidenceMessageOnlyShownWhenSelected,
                                    newTransactionItem.confidenceMessageOnlyShownWhenSelected)))
                        changes.add(ChangeType.CONFIDENCE);
                    if (!(Objects.equals(oldTransactionItem.time, newTransactionItem.time)
                            && Objects.equals(oldTransactionItem.timeSelected, newTransactionItem.timeSelected)
                            && Objects.equals(oldTransactionItem.timeColor, newTransactionItem.timeColor)))
                        changes.add(ChangeType.TIME);
                    if (!(Objects.equals(oldTransactionItem.address, newTransactionItem.address)
                            && Objects.equals(oldTransactionItem.addressColor, newTransactionItem.addressColor)
                            && Objects.equals(oldTransactionItem.addressTypeface, newTransactionItem.addressTypeface)))
                        changes.add(ChangeType.ADDRESS);
                    if (!(Objects.equals(oldTransactionItem.fee, newTransactionItem.fee)
                            && Objects.equals(oldTransactionItem.feeFormat.format(Coin.COIN).toString(),
                                    newTransactionItem.feeFormat.format(Coin.COIN).toString())))
                        changes.add(ChangeType.FEE);
                    if (!(Objects.equals(oldTransactionItem.value, newTransactionItem.value)
                            && Objects.equals(oldTransactionItem.valueFormat.format(Coin.COIN).toString(),
                                    newTransactionItem.valueFormat.format(Coin.COIN).toString())
                            && Objects.equals(oldTransactionItem.valueColor, newTransactionItem.valueColor)))
                        changes.add(ChangeType.VALUE);
                    if (!(Objects.equals(oldTransactionItem.fiat, newTransactionItem.fiat)
                            && Objects.equals(
                                    oldTransactionItem.fiatFormat != null
                                            ? oldTransactionItem.fiatFormat.format(Coin.COIN).toString() : null,
                                    newTransactionItem.fiatFormat != null
                                            ? newTransactionItem.fiatFormat.format(Coin.COIN).toString() : null)
                            && Objects.equals(oldTransactionItem.fiatPrefixColor, newTransactionItem.fiatPrefixColor)))
                        changes.add(ChangeType.FIAT);
                    if (!(Objects.equals(oldTransactionItem.message, newTransactionItem.message)
                            && Objects.equals(oldTransactionItem.messageColor, newTransactionItem.messageColor)))
                        changes.add(ChangeType.MESSAGE);
                }
                return changes;
            }
        });
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.menuInflater = new MenuInflater(context);
        this.onClickListener = onClickListener;
        this.contextMenuCallback = contextMenuCallback;

        setHasStableIds(true);
    }

    @MainThread
    public void setSelectedTransaction(final Sha256Hash newSelectedTransactionId) {
        if (Objects.equals(newSelectedTransactionId, selectedTransactionId))
            return;
        if (selectedTransactionId != null)
            notifyItemChanged(positionOf(selectedTransactionId), EnumSet.of(ChangeType.SELECTION));
        if (newSelectedTransactionId != null)
            notifyItemChanged(positionOf(newSelectedTransactionId), EnumSet.of(ChangeType.SELECTION));
        this.selectedTransactionId = newSelectedTransactionId;
    }

    @MainThread
    public int positionOf(final Sha256Hash transactionId) {
        if (transactionId != null) {
            final List<ListItem> list = getCurrentList();
            for (int i = 0; i < list.size(); i++) {
                final ListItem item = list.get(i);
                if (item instanceof ListItem.TransactionItem && ((TransactionItem) item).transactionId.equals(transactionId))
                    return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemViewType(final int position) {
        final ListItem listItem = getItem(position);
        if (listItem instanceof ListItem.WarningItem)
            return VIEW_TYPE_WARNING;
        else if (listItem instanceof ListItem.TransactionItem)
            return VIEW_TYPE_TRANSACTION;
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
        if (viewType == VIEW_TYPE_TRANSACTION)
            return new TransactionViewHolder(inflater.inflate(R.layout.transaction_row_card, parent, false));
        else if (viewType == VIEW_TYPE_WARNING)
            return new WarningViewHolder(inflater.inflate(R.layout.transaction_row_warning, parent, false));
        else
            throw new IllegalStateException("unknown type: " + viewType);
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
        if (holder instanceof TransactionViewHolder) {
            final TransactionViewHolder transactionHolder = (TransactionViewHolder) holder;
            final ListItem.TransactionItem transactionItem = (ListItem.TransactionItem) listItem;
            final boolean isSelected = transactionItem.transactionId.equals(selectedTransactionId);
            if (fullBind) {
                final OnClickListener onClickListener = this.onClickListener;
                if (onClickListener != null)
                    transactionHolder.itemView.setOnClickListener(v -> onClickListener.onTransactionClick(v,
                            transactionItem.transactionId));
            }
            if (fullBind || changes.contains(ChangeType.SELECTION)) {
                transactionHolder.itemView.setSelected(isSelected);
                transactionHolder.contextBar.setVisibility(View.GONE);
                if (contextMenuCallback != null && isSelected) {
                    final Menu menu = transactionHolder.contextBar.getMenu();
                    menu.clear();
                    contextMenuCallback.onInflateTransactionContextMenu(menuInflater, menu,
                            transactionItem.transactionId);
                    if (menu.hasVisibleItems()) {
                        transactionHolder.contextBar.setVisibility(View.VISIBLE);
                        transactionHolder.contextBar.setOnMenuItemClickListener(item ->
                                contextMenuCallback.onClickTransactionContextMenuItem(item, transactionItem.transactionId));
                    }
                }
            }
            if (fullBind || changes.contains(ChangeType.CONFIDENCE) || changes.contains(ChangeType.SELECTION))
                transactionHolder.bindConfidence(transactionItem, isSelected);
            if (fullBind || changes.contains(ChangeType.TIME) || changes.contains(ChangeType.SELECTION))
                transactionHolder.bindTime(transactionItem, isSelected);
            if (fullBind || changes.contains(ChangeType.ADDRESS) || changes.contains(ChangeType.SELECTION))
                transactionHolder.bindAddress(transactionItem, isSelected);
            if (fullBind || changes.contains(ChangeType.FEE) || changes.contains(ChangeType.SELECTION))
                transactionHolder.bindFee(transactionItem, isSelected);
            if (fullBind || changes.contains(ChangeType.VALUE))
                transactionHolder.bindValue(transactionItem);
            if (fullBind || changes.contains(ChangeType.FIAT))
                transactionHolder.bindFiat(transactionItem);
            if (fullBind || changes.contains(ChangeType.MESSAGE) || changes.contains(ChangeType.SELECTION))
                transactionHolder.bindMessage(transactionItem, isSelected);
        } else if (holder instanceof WarningViewHolder) {
            final WarningViewHolder warningHolder = (WarningViewHolder) holder;
            final ListItem.WarningItem warningItem = (ListItem.WarningItem) listItem;
            if (warningItem.type == WarningType.BACKUP) {
                if (getItemCount() == 2 /* 1 transaction, 1 warning */) {
                    warningHolder.message.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    warningHolder.message
                            .setText(Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_backup)));
                } else {
                    warningHolder.message
                            .setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_grey600_24dp, 0, 0, 0);
                    warningHolder.message.setText(
                            Html.fromHtml(context.getString(R.string.wallet_disclaimer_fragment_remind_backup)));
                }
            } else if (warningItem.type == WarningType.STORAGE_ENCRYPTION) {
                warningHolder.message.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                warningHolder.message.setText(
                        Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_storage_encryption)));
            } else if (warningItem.type == WarningType.CHAIN_FORKING) {
                warningHolder.message.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_grey600_24dp, 0,
                        0, 0);
                warningHolder.message.setText(
                        Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_chain_forking)));
            }

            if (onClickListener != null)
                warningHolder.itemView.setOnClickListener(v -> onClickListener.onWarningClick(v, warningItem.type));
        }
    }

    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final View extendTime;
        private final TextView fullTime;
        private final View extendAddress;
        private final CircularProgressView confidenceCircularNormal, confidenceCircularSelected;
        private final TextView confidenceTextualNormal, confidenceTextualSelected;
        private final View extendConfidenceMessageNormal, extendConfidenceMessageSelected;
        private final TextView confidenceMessageNormal, confidenceMessageSelected;
        private final TextView time;
        private final TextView address;
        private final CurrencyTextView value;
        private final CurrencyTextView fiat;
        private final View extendFee;
        private final CurrencyTextView fee;
        private final View extendMessage;
        private final TextView message;
        private final Toolbar contextBar;

        public TransactionViewHolder(final View itemView) {
            super(itemView);
            this.extendTime = itemView.findViewById(R.id.transaction_row_extend_time);
            this.fullTime = itemView.findViewById(R.id.transaction_row_full_time);
            this.extendAddress = itemView.findViewById(R.id.transaction_row_extend_address);
            this.confidenceCircularNormal = itemView.findViewById(R.id.transaction_row_confidence_circular);
            this.confidenceCircularSelected = itemView.findViewById(R.id.transaction_row_confidence_circular_selected);
            this.confidenceTextualNormal = itemView.findViewById(R.id.transaction_row_confidence_textual);
            this.confidenceTextualSelected = itemView.findViewById(R.id.transaction_row_confidence_textual_selected);
            this.extendConfidenceMessageNormal = itemView.findViewById(R.id.transaction_row_extend_confidence_message);
            this.extendConfidenceMessageSelected = itemView.findViewById(R.id.transaction_row_extend_confidence_message_selected);
            this.confidenceMessageNormal = itemView.findViewById(R.id.transaction_row_confidence_message);
            this.confidenceMessageSelected = itemView.findViewById(R.id.transaction_row_confidence_message_selected);
            this.time = itemView.findViewById(R.id.transaction_row_time);
            this.address = itemView.findViewById(R.id.transaction_row_address);
            this.value = itemView.findViewById(R.id.transaction_row_value);
            this.fiat = itemView.findViewById(R.id.transaction_row_fiat);
            this.extendFee = itemView.findViewById(R.id.transaction_row_extend_fee);
            this.fee = itemView.findViewById(R.id.transaction_row_fee);
            this.extendMessage = itemView.findViewById(R.id.transaction_row_extend_message);
            this.message = itemView.findViewById(R.id.transaction_row_message);
            this.contextBar = itemView.findViewById(R.id.transaction_row_context_bar);
        }

        public void fullBind(final TransactionItem item) {
            bindConfidence(item, true);
            bindTime(item, true);
            bindAddress(item, true);
            bindFee(item, true);
            bindValue(item);
            bindFiat(item);
            bindMessage(item, true);
        }

        private void bindConfidence(final TransactionItem item, final boolean isSelected) {
            if (isSelected) {
                confidenceCircularNormal.setVisibility(View.INVISIBLE);
                confidenceTextualNormal.setVisibility(View.GONE);
                confidenceCircularSelected.setVisibility(
                        item.confidenceCircularMaxProgress > 0 || item.confidenceCircularMaxSize > 0 ? View.VISIBLE :
                                View.GONE);
                confidenceCircularSelected.setMaxProgress(item.confidenceCircularMaxProgress);
                confidenceCircularSelected.setProgress(item.confidenceCircularProgress);
                confidenceCircularSelected.setMaxSize(item.confidenceCircularMaxSize);
                confidenceCircularSelected.setSize(item.confidenceCircularSize);
                confidenceCircularSelected.setColors(item.confidenceCircularFillColor,
                        item.confidenceCircularStrokeColor);
                confidenceTextualSelected.setVisibility(item.confidenceTextual != null ? View.VISIBLE : View.GONE);
                confidenceTextualSelected.setText(item.confidenceTextual);
                confidenceTextualSelected.setTextColor(item.confidenceTextualColor);
                extendConfidenceMessageSelected.setVisibility(item.confidenceMessage != null ? View.VISIBLE :
                        View.GONE);
                extendConfidenceMessageNormal.setVisibility(View.GONE);
                confidenceMessageSelected.setText(item.confidenceMessage);
            } else {
                confidenceCircularSelected.setVisibility(View.INVISIBLE);
                confidenceTextualSelected.setVisibility(View.GONE);
                confidenceCircularNormal.setVisibility(
                        item.confidenceCircularMaxProgress > 0 || item.confidenceCircularMaxSize > 0 ? View.VISIBLE :
                                View.GONE);
                confidenceCircularNormal.setMaxProgress(item.confidenceCircularMaxProgress);
                confidenceCircularNormal.setProgress(item.confidenceCircularProgress);
                confidenceCircularNormal.setMaxSize(item.confidenceCircularMaxSize);
                confidenceCircularNormal.setSize(item.confidenceCircularSize);
                confidenceCircularNormal.setColors(item.confidenceCircularFillColor,
                        item.confidenceCircularStrokeColor);
                confidenceTextualNormal.setVisibility(item.confidenceTextual != null ? View.VISIBLE : View.GONE);
                confidenceTextualNormal.setText(item.confidenceTextual);
                confidenceTextualNormal.setTextColor(item.confidenceTextualColor);
                extendConfidenceMessageSelected.setVisibility(View.GONE);
                extendConfidenceMessageNormal.setVisibility(
                        item.confidenceMessage != null && !item.confidenceMessageOnlyShownWhenSelected ?
                                View.VISIBLE : View.GONE);
                confidenceMessageNormal.setText(item.confidenceMessage);
            }
        }

        private void bindTime(final TransactionItem item, final boolean isSelected) {
            if (isSelected) {
                extendTime.setVisibility(View.VISIBLE);
                fullTime.setText(item.timeSelected);
                fullTime.setTextColor(item.timeColor);
                time.setVisibility(View.GONE);
            } else {
                time.setVisibility(View.VISIBLE);
                time.setText(item.time);
                time.setTextColor(item.timeColor);
                extendTime.setVisibility(View.GONE);
            }
        }

        private void bindAddress(final TransactionItem item, final boolean isSelected) {
            extendAddress.setVisibility(item.address != null || !isSelected ? View.VISIBLE : View.GONE);
            address.setText(item.address);
            address.setTextColor(item.addressColor);
            address.setTypeface(item.addressTypeface);
            address.setSingleLine(!isSelected);
        }

        private void bindFee(final TransactionItem item, final boolean isSelected) {
            extendFee.setVisibility(item.fee != null && isSelected ? View.VISIBLE : View.GONE);
            fee.setAlwaysSigned(true);
            fee.setFormat(item.feeFormat);
            fee.setAmount(item.fee);
        }

        private void bindValue(final TransactionItem item) {
            value.setVisibility(item.value != null ? View.VISIBLE : View.GONE);
            value.setAlwaysSigned(true);
            value.setAmount(item.value);
            value.setFormat(item.valueFormat);
            value.setTextColor(item.valueColor);
        }

        private void bindFiat(final TransactionItem item) {
            fiat.setVisibility(item.fiat != null ? View.VISIBLE : View.GONE);
            fiat.setAlwaysSigned(true);
            fiat.setAmount(item.fiat);
            fiat.setFormat(item.fiatFormat);
            fiat.setPrefixColor(item.fiatPrefixColor);
        }

        private void bindMessage(final TransactionItem item, final boolean isSelected) {
            extendMessage.setVisibility(item.message != null ? View.VISIBLE : View.GONE);
            message.setText(item.message);
            message.setTextColor(item.messageColor);
            message.setSingleLine(!isSelected);
        }
    }

    public static class WarningViewHolder extends RecyclerView.ViewHolder {
        private final TextView message;

        private WarningViewHolder(final View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.transaction_row_warning_message);
        }
    }
}
