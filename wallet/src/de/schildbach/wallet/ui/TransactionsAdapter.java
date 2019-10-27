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
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.AddressBookEntry;
import de.schildbach.wallet.ui.TransactionsAdapter.ListItem.TransactionItem;
import de.schildbach.wallet.ui.TransactionsAdapter.ListItem.WarningItem;
import de.schildbach.wallet.util.Formats;
import de.schildbach.wallet.util.WalletUtils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/**
 * @author Andreas Schildbach
 */
public class TransactionsAdapter extends ListAdapter<TransactionsAdapter.ListItem, RecyclerView.ViewHolder> {
    public static List<ListItem> buildListItems(final Context context, final List<Transaction> transactions,
            final WarningType warning, final @Nullable Wallet wallet,
            final @Nullable Map<String, AddressBookEntry> addressBook, final MonetaryFormat format,
            final int maxConnectedPeers, final @Nullable Sha256Hash selectedTransaction) {
        final MonetaryFormat noCodeFormat = format.noCode();
        final List<ListItem> items = new ArrayList<>(transactions.size() + 1);
        if (warning != null)
            items.add(new ListItem.WarningItem(warning));
        for (final Transaction tx : transactions)
            items.add(new ListItem.TransactionItem(context, tx, wallet, addressBook, noCodeFormat, maxConnectedPeers,
                    tx.getTxId().equals(selectedTransaction)));
        return items;
    }

    public static class ListItem {
        public static class TransactionItem extends ListItem {
            public final Sha256Hash transactionHash;
            public final int confidenceCircularProgress, confidenceCircularMaxProgress;
            public final int confidenceCircularSize, confidenceCircularMaxSize;
            public final int confidenceCircularFillColor, confidenceCircularStrokeColor;
            @Nullable
            public final String confidenceTextual;
            public final int confidenceTextualColor;
            @Nullable
            public final Spanned confidenceMessage;
            public final CharSequence time;
            public final int timeColor;
            @Nullable
            public final Spanned address;
            public final int addressColor;
            public final Typeface addressTypeface;
            public final boolean addressSingleLine;
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
            public final boolean messageSingleLine;
            public final boolean isSelected;

            public TransactionItem(final Context context, final Transaction tx, final @Nullable Wallet wallet,
                    final @Nullable Map<String, AddressBookEntry> addressBook, final MonetaryFormat format,
                    final int maxConnectedPeers, final boolean isSelected) {
                this.transactionHash = tx.getTxId();
                this.isSelected = isSelected;

                final int colorSignificant = ContextCompat.getColor(context, R.color.fg_significant);
                final int colorLessSignificant = ContextCompat.getColor(context, R.color.fg_less_significant);
                final int colorInsignificant = ContextCompat.getColor(context, R.color.fg_insignificant);
                final int colorValuePositve = ContextCompat.getColor(context, R.color.fg_value_positive);
                final int colorValueNegative = ContextCompat.getColor(context, R.color.fg_value_negative);
                final int colorError = ContextCompat.getColor(context, R.color.fg_error);

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
                    valueColor = sent ? colorValueNegative : colorValuePositve;
                } else {
                    textColor = colorInsignificant;
                    lessSignificantColor = colorInsignificant;
                    valueColor = colorInsignificant;
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
                } else if (confidenceType == ConfidenceType.BUILDING) {
                    this.confidenceCircularMaxProgress = tx.isCoinBase()
                            ? Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth()
                            : Constants.MAX_NUM_CONFIRMATIONS;
                    this.confidenceCircularProgress = Math.min(confidence.getDepthInBlocks(),
                            this.confidenceCircularMaxProgress);
                    this.confidenceCircularMaxSize = 1;
                    this.confidenceCircularSize = 1;
                    this.confidenceCircularFillColor = valueColor;
                    this.confidenceCircularStrokeColor = Color.TRANSPARENT;
                    this.confidenceTextual = null;
                    this.confidenceTextualColor = 0;
                    this.confidenceMessage = isSelected ? SpannedString.valueOf(
                            context.getString(sent ? R.string.transaction_row_confidence_message_sent_successful
                                    : R.string.transaction_row_confidence_message_received_successful))
                            : null;
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
                }

                // time
                final Date time = tx.getUpdateTime();
                this.time = isSelected
                        ? DateUtils.formatDateTime(context, time.getTime(),
                                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME)
                        : DateUtils.getRelativeTimeSpanString(context, time.getTime());
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
                this.addressSingleLine = !isSelected;

                // fee
                final Coin fee = tx.getFee();
                final boolean showFee = sent && fee != null && !fee.isZero();
                this.feeFormat = format;
                this.fee = isSelected && showFee ? fee.negate() : null;

                // value
                this.valueFormat = format;
                if (purpose == Purpose.RAISE_FEE) {
                    this.valueColor = colorInsignificant;
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
                    this.messageSingleLine = false;
                } else if (purpose == Purpose.RAISE_FEE) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_purpose_raise_fee));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (!isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_direct));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (!sent && value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_dust));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (!sent && confidenceType == ConfidenceType.PENDING
                        && (tx.getUpdateTime() == null || wallet.getLastBlockSeenTimeSecs() * 1000
                                - tx.getUpdateTime().getTime() > Constants.DELAYED_TRANSACTION_THRESHOLD_MS)) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_unconfirmed_delayed));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (!sent && confidenceType == ConfidenceType.PENDING) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_unconfirmed_unlocked));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (!sent && confidenceType == ConfidenceType.IN_CONFLICT) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_in_conflict));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (!sent && confidenceType == ConfidenceType.DEAD) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_dead));
                    this.messageColor = colorError;
                    this.messageSingleLine = false;
                } else if (!sent && WalletUtils.isPayToManyTransaction(tx)) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_pay_to_many));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (!sent && tx.isOptInFullRBF()) {
                    this.message = SpannedString
                            .valueOf(context.getString(R.string.transaction_row_message_received_rbf));
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = false;
                } else if (memo != null) {
                    this.message = SpannedString.valueOf(memo[0]);
                    this.messageColor = colorInsignificant;
                    this.messageSingleLine = isSelected;
                } else {
                    this.message = null;
                    this.messageColor = 0;
                    this.messageSingleLine = false;
                }
            }
        }

        public static class WarningItem extends ListItem {
            public final WarningType type;

            public WarningItem(final WarningType type) {
                this.type = type;
            }
        }
    }

    public enum WarningType {
        BACKUP, STORAGE_ENCRYPTION, CHAIN_FORKING
    }

    private final Context context;
    private final LayoutInflater inflater;

    @Nullable
    private final OnClickListener onClickListener;

    private static final String CONFIDENCE_SYMBOL_IN_CONFLICT = "\u26A0"; // warning sign
    private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
    private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

    private static final int VIEW_TYPE_TRANSACTION = 0;
    private static final int VIEW_TYPE_WARNING = 1;

    private enum ChangeType {
        CONFIDENCE, TIME, ADDRESS, FEE, VALUE, FIAT, MESSAGE, IS_SELECTED
    }

    public TransactionsAdapter(final Context context, final int maxConnectedPeers,
            final @Nullable OnClickListener onClickListener) {
        super(new DiffUtil.ItemCallback<ListItem>() {
            @Override
            public boolean areItemsTheSame(final ListItem oldItem, final ListItem newItem) {
                if (oldItem instanceof TransactionItem) {
                    if (!(newItem instanceof TransactionItem))
                        return false;
                    return Objects.equals(((TransactionItem) oldItem).transactionHash,
                            ((TransactionItem) newItem).transactionHash);
                } else {
                    if (!(newItem instanceof WarningItem))
                        return false;
                    return Objects.equals(((WarningItem) oldItem).type, ((WarningItem) newItem).type);
                }
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
                    if (!Objects.equals(oldTransactionItem.time, newTransactionItem.time))
                        return false;
                    if (!Objects.equals(oldTransactionItem.timeColor, newTransactionItem.timeColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.address, newTransactionItem.address))
                        return false;
                    if (!Objects.equals(oldTransactionItem.addressColor, newTransactionItem.addressColor))
                        return false;
                    if (!Objects.equals(oldTransactionItem.addressTypeface, newTransactionItem.addressTypeface))
                        return false;
                    if (!Objects.equals(oldTransactionItem.addressSingleLine, newTransactionItem.addressSingleLine))
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
                    if (!Objects.equals(oldTransactionItem.messageSingleLine, newTransactionItem.messageSingleLine))
                        return false;
                    if (!Objects.equals(oldTransactionItem.isSelected, newTransactionItem.isSelected))
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
                                    newTransactionItem.confidenceMessage)))
                        changes.add(ChangeType.CONFIDENCE);
                    if (!(Objects.equals(oldTransactionItem.time, newTransactionItem.time)
                            && Objects.equals(oldTransactionItem.timeColor, newTransactionItem.timeColor)))
                        changes.add(ChangeType.TIME);
                    if (!(Objects.equals(oldTransactionItem.address, newTransactionItem.address)
                            && Objects.equals(oldTransactionItem.addressColor, newTransactionItem.addressColor)
                            && Objects.equals(oldTransactionItem.addressTypeface, newTransactionItem.addressTypeface)
                            && Objects.equals(oldTransactionItem.addressSingleLine,
                                    newTransactionItem.addressSingleLine)))
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
                            && Objects.equals(oldTransactionItem.messageColor, newTransactionItem.messageColor)
                            && Objects.equals(oldTransactionItem.messageSingleLine,
                                    newTransactionItem.messageSingleLine)))
                        changes.add(ChangeType.MESSAGE);
                    if (!(Objects.equals(oldTransactionItem.isSelected, newTransactionItem.isSelected)))
                        changes.add(ChangeType.IS_SELECTED);
                }
                return changes;
            }
        });
        this.context = context;
        this.inflater = LayoutInflater.from(context);

        this.onClickListener = onClickListener;
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
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        if (viewType == VIEW_TYPE_TRANSACTION) {
            final CardView cardView = (CardView) inflater.inflate(R.layout.transaction_row_card, parent, false);
            cardView.setPreventCornerOverlap(false);
            cardView.setUseCompatPadding(false);
            cardView.setMaxCardElevation(0); // we're using Lollipop elevation
            return new TransactionViewHolder(cardView);
        } else if (viewType == VIEW_TYPE_WARNING) {
            return new WarningViewHolder(inflater.inflate(R.layout.transaction_row_warning, parent, false));
        } else {
            throw new IllegalStateException("unknown type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        final ListItem listItem = getItem(position);
        if (holder instanceof TransactionViewHolder) {
            final TransactionViewHolder transactionHolder = (TransactionViewHolder) holder;
            final ListItem.TransactionItem transactionItem = (ListItem.TransactionItem) listItem;
            transactionHolder.itemView.setActivated(transactionItem.isSelected);
            transactionHolder.bind(transactionItem);

            final OnClickListener onClickListener = this.onClickListener;
            if (onClickListener != null) {
                transactionHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        onClickListener.onTransactionClick(v, transactionItem.transactionHash);
                    }
                });
                transactionHolder.menuView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        onClickListener.onTransactionMenuClick(v, transactionItem.transactionHash);
                    }
                });
            }
        } else if (holder instanceof WarningViewHolder) {
            final WarningViewHolder warningHolder = (WarningViewHolder) holder;
            final ListItem.WarningItem warningItem = (ListItem.WarningItem) listItem;
            if (warningItem.type == WarningType.BACKUP) {
                if (getItemCount() == 2 /* 1 transaction, 1 warning */) {
                    warningHolder.messageView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    warningHolder.messageView
                            .setText(Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_backup)));
                } else {
                    warningHolder.messageView
                            .setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_grey600_24dp, 0, 0, 0);
                    warningHolder.messageView.setText(
                            Html.fromHtml(context.getString(R.string.wallet_disclaimer_fragment_remind_backup)));
                }
            } else if (warningItem.type == WarningType.STORAGE_ENCRYPTION) {
                warningHolder.messageView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                warningHolder.messageView.setText(
                        Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_storage_encryption)));
            } else if (warningItem.type == WarningType.CHAIN_FORKING) {
                warningHolder.messageView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_grey600_24dp, 0,
                        0, 0);
                warningHolder.messageView.setText(
                        Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_chain_forking)));
            }

            final OnClickListener onClickListener = this.onClickListener;
            if (onClickListener != null) {
                warningHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        onClickListener.onWarningClick(v);
                    }
                });
            }
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position,
            final List<Object> payloads) {
        if (payloads.isEmpty()) { // Full bind
            onBindViewHolder(holder, position);
        } else { // Partial bind
            final ListItem listItem = getItem(position);
            final TransactionViewHolder transactionHolder = (TransactionViewHolder) holder;
            final ListItem.TransactionItem transactionItem = (ListItem.TransactionItem) listItem;
            for (final Object payload : payloads) {
                final EnumSet<ChangeType> changes = (EnumSet<ChangeType>) payload;
                for (final ChangeType change : changes) {
                    if (change == ChangeType.CONFIDENCE)
                        transactionHolder.bindConfidence(transactionItem);
                    else if (change == ChangeType.TIME)
                        transactionHolder.bindTime(transactionItem);
                    else if (change == ChangeType.ADDRESS)
                        transactionHolder.bindAddress(transactionItem);
                    else if (change == ChangeType.FEE)
                        transactionHolder.bindFee(transactionItem);
                    else if (change == ChangeType.VALUE)
                        transactionHolder.bindValue(transactionItem);
                    else if (change == ChangeType.FIAT)
                        transactionHolder.bindFiat(transactionItem);
                    else if (change == ChangeType.MESSAGE)
                        transactionHolder.bindMessage(transactionItem);
                    else if (change == ChangeType.IS_SELECTED)
                        transactionHolder.bindIsSelected(transactionItem);
                }
            }
        }
    }

    public static class ItemAnimator extends DefaultItemAnimator {
        @Override
        public boolean canReuseUpdatedViewHolder(final ViewHolder viewHolder, final List<Object> payloads) {
            for (final Object payload : payloads) {
                final EnumSet<TransactionsAdapter.ChangeType> changes = (EnumSet<TransactionsAdapter.ChangeType>) payload;
                if (changes.contains(TransactionsAdapter.ChangeType.IS_SELECTED))
                    return false;
            }
            return super.canReuseUpdatedViewHolder(viewHolder, payloads);
        }
    }

    public interface OnClickListener {
        void onTransactionClick(View view, Sha256Hash transactionHash);

        void onTransactionMenuClick(View view, Sha256Hash transactionHash);

        void onWarningClick(View view);
    }

    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final int colorBackground;
        private final int colorBackgroundSelected;

        private final View extendTimeView;
        private final TextView fullTimeView;
        private final View extendAddressView;
        private final CircularProgressView confidenceCircularNormalView, confidenceCircularSelectedView;
        private final TextView confidenceTextualNormalView, confidenceTextualSelectedView;
        private final View extendConfidenceMessageNormalView, extendConfidenceMessageSelectedView;
        private final TextView confidenceMessageNormalView, confidenceMessageSelectedView;
        private final TextView timeView;
        private final TextView addressView;
        private final CurrencyTextView valueView;
        private final CurrencyTextView fiatView;
        private final View extendFeeView;
        private final CurrencyTextView feeView;
        private final View extendMessageView;
        private final TextView messageView;
        private final ImageButton menuView;

        public TransactionViewHolder(final View itemView) {
            super(itemView);
            final Context context = itemView.getContext();
            this.colorBackground = ContextCompat.getColor(context, R.color.bg_level2);
            this.colorBackgroundSelected = ContextCompat.getColor(context, R.color.bg_level3);

            this.extendTimeView = itemView.findViewById(R.id.transaction_row_extend_time);
            this.fullTimeView = (TextView) itemView.findViewById(R.id.transaction_row_full_time);
            this.extendAddressView = itemView.findViewById(R.id.transaction_row_extend_address);
            this.confidenceCircularNormalView = (CircularProgressView) itemView
                    .findViewById(R.id.transaction_row_confidence_circular);
            this.confidenceCircularSelectedView = (CircularProgressView) itemView
                    .findViewById(R.id.transaction_row_confidence_circular_selected);
            this.confidenceTextualNormalView = (TextView) itemView
                    .findViewById(R.id.transaction_row_confidence_textual);
            this.confidenceTextualSelectedView = (TextView) itemView
                    .findViewById(R.id.transaction_row_confidence_textual_selected);
            this.extendConfidenceMessageNormalView = itemView
                    .findViewById(R.id.transaction_row_extend_confidence_message);
            this.extendConfidenceMessageSelectedView = itemView
                    .findViewById(R.id.transaction_row_extend_confidence_message_selected);
            this.confidenceMessageNormalView = (TextView) itemView
                    .findViewById(R.id.transaction_row_confidence_message);
            this.confidenceMessageSelectedView = (TextView) itemView
                    .findViewById(R.id.transaction_row_confidence_message_selected);
            this.timeView = (TextView) itemView.findViewById(R.id.transaction_row_time);
            this.addressView = (TextView) itemView.findViewById(R.id.transaction_row_address);
            this.valueView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_value);
            this.fiatView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_fiat);
            this.extendFeeView = itemView.findViewById(R.id.transaction_row_extend_fee);
            this.feeView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_fee);
            this.extendMessageView = itemView.findViewById(R.id.transaction_row_extend_message);
            this.messageView = (TextView) itemView.findViewById(R.id.transaction_row_message);
            this.menuView = (ImageButton) itemView.findViewById(R.id.transaction_row_menu);
        }

        public void bind(final TransactionItem item) {
            bindConfidence(item);
            bindTime(item);
            bindAddress(item);
            bindFee(item);
            bindValue(item);
            bindFiat(item);
            bindMessage(item);
            bindIsSelected(item);
        }

        private void bindConfidence(final TransactionItem item) {
            (item.isSelected ? confidenceCircularNormalView : confidenceCircularSelectedView)
                    .setVisibility(View.INVISIBLE);
            (item.isSelected ? confidenceTextualNormalView : confidenceTextualSelectedView).setVisibility(View.GONE);
            final CircularProgressView confidenceCircularView = item.isSelected ? confidenceCircularSelectedView
                    : confidenceCircularNormalView;
            final TextView confidenceTextualView = item.isSelected ? confidenceTextualSelectedView
                    : confidenceTextualNormalView;
            confidenceCircularView
                    .setVisibility(item.confidenceCircularMaxProgress > 0 || item.confidenceCircularMaxSize > 0
                            ? View.VISIBLE : View.GONE);
            confidenceCircularView.setMaxProgress(item.confidenceCircularMaxProgress);
            confidenceCircularView.setProgress(item.confidenceCircularProgress);
            confidenceCircularView.setMaxSize(item.confidenceCircularMaxSize);
            confidenceCircularView.setSize(item.confidenceCircularSize);
            confidenceCircularView.setColors(item.confidenceCircularFillColor, item.confidenceCircularStrokeColor);
            confidenceTextualView.setVisibility(item.confidenceTextual != null ? View.VISIBLE : View.GONE);
            confidenceTextualView.setText(item.confidenceTextual);
            confidenceTextualView.setTextColor(item.confidenceTextualColor);
            extendConfidenceMessageSelectedView
                    .setVisibility(item.isSelected && item.confidenceMessage != null ? View.VISIBLE : View.GONE);
            extendConfidenceMessageNormalView
                    .setVisibility(!item.isSelected && item.confidenceMessage != null ? View.VISIBLE : View.GONE);
            (item.isSelected ? confidenceMessageSelectedView : confidenceMessageNormalView)
                    .setText(item.confidenceMessage);
        }

        private void bindTime(final TransactionItem item) {
            (item.isSelected ? extendTimeView : timeView).setVisibility(View.VISIBLE);
            (item.isSelected ? timeView : extendTimeView).setVisibility(View.GONE);
            final TextView timeView = item.isSelected ? this.fullTimeView : this.timeView;
            timeView.setText(item.time);
            timeView.setTextColor(item.timeColor);
        }

        private void bindAddress(final TransactionItem item) {
            extendAddressView.setVisibility(item.address != null || !item.isSelected ? View.VISIBLE : View.GONE);
            addressView.setText(item.address);
            addressView.setTextColor(item.addressColor);
            addressView.setTypeface(item.addressTypeface);
            addressView.setSingleLine(item.addressSingleLine);
        }

        private void bindFee(final TransactionItem item) {
            extendFeeView.setVisibility(item.fee != null ? View.VISIBLE : View.GONE);
            feeView.setAlwaysSigned(true);
            feeView.setFormat(item.feeFormat);
            feeView.setAmount(item.fee);
        }

        private void bindValue(final TransactionItem item) {
            valueView.setVisibility(item.value != null ? View.VISIBLE : View.GONE);
            valueView.setAlwaysSigned(true);
            valueView.setAmount(item.value);
            valueView.setFormat(item.valueFormat);
            valueView.setTextColor(item.valueColor);
        }

        private void bindFiat(final TransactionItem item) {
            fiatView.setVisibility(item.fiat != null ? View.VISIBLE : View.GONE);
            fiatView.setAlwaysSigned(true);
            fiatView.setAmount(item.fiat);
            fiatView.setFormat(item.fiatFormat);
            fiatView.setPrefixColor(item.fiatPrefixColor);
        }

        private void bindMessage(final TransactionItem item) {
            extendMessageView.setVisibility(item.message != null ? View.VISIBLE : View.GONE);
            messageView.setText(item.message);
            messageView.setTextColor(item.messageColor);
            messageView.setSingleLine(item.messageSingleLine);
        }

        private void bindIsSelected(final TransactionItem item) {
            if (itemView instanceof CardView)
                ((CardView) itemView)
                        .setCardBackgroundColor(item.isSelected ? colorBackgroundSelected : colorBackground);
            menuView.setVisibility(item.isSelected ? View.VISIBLE : View.GONE);
            bindConfidence(item);
            bindTime(item);
            bindAddress(item);
        }
    }

    public static class WarningViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageView;

        private WarningViewHolder(final View itemView) {
            super(itemView);
            messageView = (TextView) itemView.findViewById(R.id.transaction_row_warning_message);
        }
    }
}
