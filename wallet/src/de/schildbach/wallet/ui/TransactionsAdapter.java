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
import org.bitcoinj.core.Wallet;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DefaultCoinSelector;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.CircularProgressView;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class TransactionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
{
	private final Context context;
	private final LayoutInflater inflater;

	private final boolean useCards;
	private final Wallet wallet;
	private final int maxConnectedPeers;
	@Nullable
	private final OnClickListener onClickListener;

	private final List<Transaction> transactions = new ArrayList<Transaction>();
	private MonetaryFormat format;
	private boolean showBackupWarning = false;

	private long selectedItemId = RecyclerView.NO_ID;

	private final int colorBackground, colorBackgroundSelected;
	private final int colorSignificant;
	private final int colorInsignificant;
	private final int colorError;
	private final int colorCircularBuilding = Color.parseColor("#44ff44");
	private final String textCoinBase;
	private final String textInternal;

	private final Map<String, String> labelCache = new HashMap<String, String>();
	private final static String CACHE_NULL_MARKER = "";

	private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
	private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

	private static final int VIEW_TYPE_TRANSACTION = 0;
	private static final int VIEW_TYPE_WARNING = 1;

	private Map<Sha256Hash, TransactionCacheEntry> transactionCache = new HashMap<Sha256Hash, TransactionCacheEntry>();

	private static class TransactionCacheEntry
	{
		private final Coin value;
		private final boolean sent;
		private final boolean showFee;
		private final Address address;

		private TransactionCacheEntry(final Coin value, final boolean sent, final boolean showFee, final Address address)
		{
			this.value = value;
			this.sent = sent;
			this.showFee = showFee;
			this.address = address;
		}
	}

	public TransactionsAdapter(final Context context, final Wallet wallet, final boolean useCards, final int maxConnectedPeers,
			final @Nullable OnClickListener onClickListener)
	{
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
		colorInsignificant = res.getColor(R.color.fg_insignificant);
		colorError = res.getColor(R.color.fg_error);
		textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
		textInternal = context.getString(R.string.wallet_transactions_fragment_internal);

		setHasStableIds(true);
	}

	public void setFormat(final MonetaryFormat format)
	{
		this.format = format.noCode();

		notifyDataSetChanged();
	}

	public void setShowBackupWarning(final boolean showBackupWarning)
	{
		this.showBackupWarning = showBackupWarning;

		notifyDataSetChanged();
	}

	public void clear()
	{
		transactions.clear();

		notifyDataSetChanged();
	}

	public void replace(final Transaction tx)
	{
		transactions.clear();
		transactions.add(tx);

		notifyDataSetChanged();
	}

	public void replace(final Collection<Transaction> transactions)
	{
		this.transactions.clear();
		this.transactions.addAll(transactions);

		notifyDataSetChanged();
	}

	public void setSelectedItemId(final long itemId)
	{
		selectedItemId = itemId;

		notifyDataSetChanged();
	}

	@Override
	public int getItemCount()
	{
		int count = transactions.size();

		if (count == 1 && showBackupWarning)
			count++;

		return count;
	}

	@Override
	public long getItemId(final int position)
	{
		if (position == RecyclerView.NO_POSITION)
			return RecyclerView.NO_ID;

		if (position == transactions.size() && showBackupWarning)
			return 0;

		return WalletUtils.longHash(transactions.get(position).getHash());
	}

	@Override
	public int getItemViewType(final int position)
	{
		if (position == transactions.size() && showBackupWarning)
			return VIEW_TYPE_WARNING;
		else
			return VIEW_TYPE_TRANSACTION;
	}

	public RecyclerView.ViewHolder createTransactionViewHolder(final ViewGroup parent)
	{
		return createViewHolder(parent, VIEW_TYPE_TRANSACTION);
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType)
	{
		if (viewType == VIEW_TYPE_TRANSACTION)
		{
			if (useCards)
			{
				final CardView cardView = (CardView) inflater.inflate(R.layout.transaction_row_card, parent, false);
				cardView.setPreventCornerOverlap(false);
				cardView.setUseCompatPadding(true);
				return new TransactionViewHolder(cardView);
			}
			else
			{
				return new TransactionViewHolder(inflater.inflate(R.layout.transaction_row, parent, false));
			}
		}
		else if (viewType == VIEW_TYPE_WARNING)
		{
			return new WarningViewHolder(inflater.inflate(R.layout.transaction_row_warning, parent, false));
		}
		else
		{
			throw new IllegalStateException("unknown type: " + viewType);
		}
	}

	@Override
	public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position)
	{
		if (holder instanceof TransactionViewHolder)
		{
			final TransactionViewHolder transactionHolder = (TransactionViewHolder) holder;

			final long itemId = getItemId(position);
			transactionHolder.itemView.setActivated(itemId == selectedItemId);

			final Transaction tx = transactions.get(position);
			transactionHolder.bind(tx);

			if (onClickListener != null)
			{
				transactionHolder.itemView.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(final View v)
					{
						setSelectedItemId(getItemId(transactionHolder.getAdapterPosition()));
						onClickListener.onTransactionClick(tx);
					}
				});
			}
		}
	}

	public interface OnClickListener
	{
		void onTransactionClick(Transaction tx);

		void onWarningClick();
	}

	private class TransactionViewHolder extends RecyclerView.ViewHolder
	{
		private final CircularProgressView confidenceCircularView;
		private final TextView confidenceTextualView;
		private final TextView timeView;
		private final TextView fromToView;
		private final View coinbaseView;
		private final TextView addressView;
		private final View extendFeeView;
		private final CurrencyTextView feeView;
		private final CurrencyTextView valueView;
		private final View extendFiatView;
		private final CurrencyTextView fiatView;
		private final View extendMessageView;
		private final TextView messageView;

		private TransactionViewHolder(final View itemView)
		{
			super(itemView);

			confidenceCircularView = (CircularProgressView) itemView.findViewById(R.id.transaction_row_confidence_circular);
			confidenceTextualView = (TextView) itemView.findViewById(R.id.transaction_row_confidence_textual);
			timeView = (TextView) itemView.findViewById(R.id.transaction_row_time);
			fromToView = (TextView) itemView.findViewById(R.id.transaction_row_fromto);
			coinbaseView = itemView.findViewById(R.id.transaction_row_coinbase);
			addressView = (TextView) itemView.findViewById(R.id.transaction_row_address);
			extendFeeView = itemView.findViewById(R.id.transaction_row_extend_fee);
			feeView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_fee);
			valueView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_value);
			extendFiatView = itemView.findViewById(R.id.transaction_row_extend_fiat);
			fiatView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_fiat);
			extendMessageView = itemView.findViewById(R.id.transaction_row_extend_message);
			messageView = (TextView) itemView.findViewById(R.id.transaction_row_message);
		}

		private void bind(final Transaction tx)
		{
			if (itemView instanceof CardView)
				((CardView) itemView).setCardBackgroundColor(itemView.isActivated() ? colorBackgroundSelected : colorBackground);

			final TransactionConfidence confidence = tx.getConfidence();
			final ConfidenceType confidenceType = confidence.getConfidenceType();
			final boolean isOwn = confidence.getSource().equals(TransactionConfidence.Source.SELF);
			final boolean isCoinBase = tx.isCoinBase();
			final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;
			final Coin fee = tx.getFee();

			TransactionCacheEntry txCache = transactionCache.get(tx.getHash());
			if (txCache == null)
			{
				final Coin value = tx.getValue(wallet);
				final boolean sent = value.signum() < 0;
				final boolean showFee = sent && fee != null && !fee.isZero();
				final Address address;
				if (sent)
					address = WalletUtils.getToAddressOfSent(tx, wallet);
				else
					address = WalletUtils.getWalletAddressOfReceived(tx, wallet);
				txCache = new TransactionCacheEntry(value, sent, showFee, address);

				transactionCache.put(tx.getHash(), txCache);
			}

			// confidence
			if (confidenceType == ConfidenceType.PENDING)
			{
				confidenceCircularView.setVisibility(View.VISIBLE);
				confidenceTextualView.setVisibility(View.GONE);

				confidenceCircularView.setProgress(1);
				confidenceCircularView.setMaxProgress(1);
				confidenceCircularView.setSize(confidence.numBroadcastPeers());
				confidenceCircularView.setMaxSize(maxConnectedPeers / 2); // magic value
				confidenceCircularView.setColors(colorInsignificant, colorInsignificant);
			}
			else if (confidenceType == ConfidenceType.BUILDING)
			{
				confidenceCircularView.setVisibility(View.VISIBLE);
				confidenceTextualView.setVisibility(View.GONE);

				confidenceCircularView.setProgress(confidence.getDepthInBlocks());
				confidenceCircularView.setMaxProgress(isCoinBase ? Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth()
						: Constants.MAX_NUM_CONFIRMATIONS);
				confidenceCircularView.setSize(1);
				confidenceCircularView.setMaxSize(1);
				confidenceCircularView.setColors(colorCircularBuilding, Color.DKGRAY);
			}
			else if (confidenceType == ConfidenceType.DEAD)
			{
				confidenceCircularView.setVisibility(View.GONE);
				confidenceTextualView.setVisibility(View.VISIBLE);

				confidenceTextualView.setText(CONFIDENCE_SYMBOL_DEAD);
				confidenceTextualView.setTextColor(Color.RED);
			}
			else
			{
				confidenceCircularView.setVisibility(View.GONE);
				confidenceTextualView.setVisibility(View.VISIBLE);

				confidenceTextualView.setText(CONFIDENCE_SYMBOL_UNKNOWN);
				confidenceTextualView.setTextColor(colorInsignificant);
			}

			// spendability
			final int textColor;
			if (confidenceType == ConfidenceType.DEAD)
				textColor = Color.RED;
			else
				textColor = DefaultCoinSelector.isSelectable(tx) ? colorSignificant : colorInsignificant;

			// time
			final Date time = tx.getUpdateTime();
			timeView.setText(time != null ? (DateUtils.getRelativeTimeSpanString(context, time.getTime())) : null);
			timeView.setTextColor(textColor);

			// receiving or sending
			if (isInternal)
				fromToView.setText(R.string.symbol_internal);
			else if (txCache.sent)
				fromToView.setText(R.string.symbol_to);
			else
				fromToView.setText(R.string.symbol_from);
			fromToView.setTextColor(textColor);

			// coinbase
			coinbaseView.setVisibility(isCoinBase ? View.VISIBLE : View.GONE);

			// address
			final String label;
			if (isCoinBase)
				label = textCoinBase;
			else if (isInternal)
				label = textInternal;
			else if (txCache.address != null)
				label = resolveLabel(txCache.address.toString());
			else
				label = "?";
			addressView.setTextColor(textColor);
			addressView.setText(label != null ? label : txCache.address.toString());
			addressView.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

			// fee
			extendFeeView.setVisibility(itemView.isActivated() && txCache.showFee ? View.VISIBLE : View.GONE);
			feeView.setAlwaysSigned(true);
			feeView.setFormat(format);
			if (txCache.showFee)
				feeView.setAmount(fee.negate());

			// value
			valueView.setTextColor(textColor);
			valueView.setAlwaysSigned(true);
			valueView.setFormat(format);
			final Coin value = txCache.showFee ? txCache.value.add(fee) : txCache.value;
			valueView.setAmount(value);
			valueView.setVisibility(!value.isZero() ? View.VISIBLE : View.GONE);

			// fiat value
			final ExchangeRate exchangeRate = tx.getExchangeRate();
			if (exchangeRate != null)
			{
				extendFiatView.setVisibility(View.VISIBLE);
				fiatView.setAlwaysSigned(true);
				fiatView.setPrefixColor(colorInsignificant);
				fiatView.setFormat(Constants.LOCAL_FORMAT.code(0, Constants.PREFIX_ALMOST_EQUAL_TO + exchangeRate.fiat.getCurrencyCode()));
				fiatView.setAmount(exchangeRate.coinToFiat(txCache.value));
			}
			else
			{
				extendFiatView.setVisibility(View.GONE);
			}

			// message
			extendMessageView.setVisibility(View.GONE);

			if (isInternal)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(Html.fromHtml(context.getString(R.string.transaction_row_message_purpose_key_rotation)));
				messageView.setTextColor(colorSignificant);
			}
			else if (isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(R.string.transaction_row_message_own_unbroadcasted);
				messageView.setTextColor(colorInsignificant);
			}
			else if (!isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(R.string.transaction_row_message_received_direct);
				messageView.setTextColor(colorInsignificant);
			}
			else if (!txCache.sent && txCache.value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(R.string.transaction_row_message_received_dust);
				messageView.setTextColor(colorInsignificant);
			}
			else if (!txCache.sent && confidenceType == ConfidenceType.PENDING)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(R.string.transaction_row_message_received_unconfirmed_unlocked);
				messageView.setTextColor(colorInsignificant);
			}
			else if (!txCache.sent && confidenceType == ConfidenceType.DEAD)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(R.string.transaction_row_message_received_dead);
				messageView.setTextColor(colorError);
			}
			else if (!txCache.sent && tx.getOutputs().size() > 20)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(R.string.transaction_row_message_received_pay_to_many);
				messageView.setTextColor(colorInsignificant);
			}
			else if (tx.getMemo() != null)
			{
				extendMessageView.setVisibility(View.VISIBLE);
				messageView.setText(tx.getMemo());
				messageView.setTextColor(colorInsignificant);
			}
		}
	}

	private class WarningViewHolder extends RecyclerView.ViewHolder
	{
		private final TextView messageView;

		private WarningViewHolder(final View itemView)
		{
			super(itemView);

			messageView = (TextView) itemView.findViewById(R.id.transaction_row_warning_message);
			messageView.setText(Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_backup)));

			if (onClickListener != null)
			{
				itemView.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(final View v)
					{
						onClickListener.onWarningClick();
					}
				});
			}
		}
	}

	private String resolveLabel(final String address)
	{
		final String cachedLabel = labelCache.get(address);
		if (cachedLabel == null)
		{
			final String label = AddressBookProvider.resolveLabel(context, address);
			if (label != null)
				labelCache.put(address, label);
			else
				labelCache.put(address, CACHE_NULL_MARKER);
			return label;
		}
		else
		{
			return cachedLabel != CACHE_NULL_MARKER ? cachedLabel : null;
		}
	}

	public void clearLabelCache()
	{
		labelCache.clear();

		notifyDataSetChanged();
	}
}
