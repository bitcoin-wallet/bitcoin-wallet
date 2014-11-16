/*
 * Copyright 2011-2014 the original author or authors.
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

package biz.wiz.android.wallet.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DefaultCoinSelector;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import biz.wiz.android.wallet.AddressBookProvider;
import biz.wiz.android.wallet.Constants;
import biz.wiz.android.wallet.util.CircularProgressView;
import biz.wiz.android.wallet.util.WalletUtils;
import biz.wiz.android.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class TransactionsListAdapter extends BaseAdapter
{
	private final Context context;
	private final LayoutInflater inflater;
	private final Wallet wallet;
	private final int maxConnectedPeers;

	private final List<Transaction> transactions = new ArrayList<Transaction>();
	private MonetaryFormat format;
	private boolean showEmptyText = false;
	private boolean showBackupWarning = false;

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

	public TransactionsListAdapter(final Context context, @Nonnull final Wallet wallet, final int maxConnectedPeers, final boolean showBackupWarning)
	{
		this.context = context;
		inflater = LayoutInflater.from(context);

		this.wallet = wallet;
		this.maxConnectedPeers = maxConnectedPeers;
		this.showBackupWarning = showBackupWarning;

		final Resources resources = context.getResources();
		colorSignificant = resources.getColor(R.color.fg_significant);
		colorInsignificant = resources.getColor(R.color.fg_insignificant);
		colorError = resources.getColor(R.color.fg_error);
		textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
		textInternal = context.getString(R.string.wallet_transactions_fragment_internal);
	}

	public void setFormat(final MonetaryFormat format)
	{
		this.format = format.noCode();

		notifyDataSetChanged();
	}

	public void clear()
	{
		transactions.clear();

		notifyDataSetChanged();
	}

	public void replace(@Nonnull final Transaction tx)
	{
		transactions.clear();
		transactions.add(tx);

		notifyDataSetChanged();
	}

	public void replace(@Nonnull final Collection<Transaction> transactions)
	{
		this.transactions.clear();
		this.transactions.addAll(transactions);

		showEmptyText = true;

		notifyDataSetChanged();
	}

	@Override
	public boolean isEmpty()
	{
		return showEmptyText && super.isEmpty();
	}

	@Override
	public int getCount()
	{
		int count = transactions.size();

		if (count == 1 && showBackupWarning)
			count++;

		return count;
	}

	@Override
	public Transaction getItem(final int position)
	{
		if (position == transactions.size() && showBackupWarning)
			return null;

		return transactions.get(position);
	}

	@Override
	public long getItemId(final int position)
	{
		if (position == transactions.size() && showBackupWarning)
			return 0;

		return WalletUtils.longHash(transactions.get(position).getHash());
	}

	@Override
	public int getViewTypeCount()
	{
		return 2;
	}

	@Override
	public int getItemViewType(final int position)
	{
		if (position == transactions.size() && showBackupWarning)
			return VIEW_TYPE_WARNING;
		else
			return VIEW_TYPE_TRANSACTION;
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	@Override
	public View getView(final int position, View row, final ViewGroup parent)
	{
		final int type = getItemViewType(position);

		if (type == VIEW_TYPE_TRANSACTION)
		{
			if (row == null)
				row = inflater.inflate(R.layout.transaction_row_extended, null);

			final Transaction tx = getItem(position);
			bindView(row, tx);
		}
		else if (type == VIEW_TYPE_WARNING)
		{
			if (row == null)
				row = inflater.inflate(R.layout.transaction_row_warning, null);

			final TextView messageView = (TextView) row.findViewById(R.id.transaction_row_warning_message);
			messageView.setText(Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_backup)));
		}
		else
		{
			throw new IllegalStateException("unknown type: " + type);
		}

		return row;
	}

	private class TransactionCacheEntry
	{
		public TransactionCacheEntry(final Coin value, final boolean sent, final Address address)
		{
			this.value = value;
			this.sent = sent;
			this.address = address;
		}

		public final Coin value;
		public final boolean sent;
		public final Address address;
	}

	private Map<Sha256Hash, TransactionCacheEntry> transactionCache = new HashMap<Sha256Hash, TransactionCacheEntry>();

	public void bindView(@Nonnull final View row, @Nonnull final Transaction tx)
	{
		final TransactionConfidence confidence = tx.getConfidence();
		final ConfidenceType confidenceType = confidence.getConfidenceType();
		final boolean isOwn = confidence.getSource().equals(TransactionConfidence.Source.SELF);
		final boolean isCoinBase = tx.isCoinBase();
		final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;
		final Coin fee = tx.getFee();
		final boolean hasFee = fee != null && !fee.isZero();

		TransactionCacheEntry txCache = transactionCache.get(tx.getHash());
		if (txCache == null)
		{
			final Coin value = tx.getValue(wallet);
			final boolean sent = value.signum() < 0;
			final Address address = sent ? WalletUtils.getWalletAddressOfReceived(tx, wallet) : WalletUtils.getFirstFromAddress(tx);
			txCache = new TransactionCacheEntry(value, sent, address);

			transactionCache.put(tx.getHash(), txCache);
		}

		final CircularProgressView rowConfidenceCircular = (CircularProgressView) row.findViewById(R.id.transaction_row_confidence_circular);
		final TextView rowConfidenceTextual = (TextView) row.findViewById(R.id.transaction_row_confidence_textual);

		// confidence
		if (confidenceType == ConfidenceType.PENDING)
		{
			rowConfidenceCircular.setVisibility(View.VISIBLE);
			rowConfidenceTextual.setVisibility(View.GONE);

			rowConfidenceCircular.setProgress(1);
			rowConfidenceCircular.setMaxProgress(1);
			rowConfidenceCircular.setSize(confidence.numBroadcastPeers());
			rowConfidenceCircular.setMaxSize(maxConnectedPeers / 2); // magic value
			rowConfidenceCircular.setColors(colorInsignificant, colorInsignificant);
		}
		else if (confidenceType == ConfidenceType.BUILDING)
		{
			rowConfidenceCircular.setVisibility(View.VISIBLE);
			rowConfidenceTextual.setVisibility(View.GONE);

			rowConfidenceCircular.setProgress(confidence.getDepthInBlocks());
			rowConfidenceCircular.setMaxProgress(isCoinBase ? Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth()
					: Constants.MAX_NUM_CONFIRMATIONS);
			rowConfidenceCircular.setSize(1);
			rowConfidenceCircular.setMaxSize(1);
			rowConfidenceCircular.setColors(colorCircularBuilding, Color.DKGRAY);
		}
		else if (confidenceType == ConfidenceType.DEAD)
		{
			rowConfidenceCircular.setVisibility(View.GONE);
			rowConfidenceTextual.setVisibility(View.VISIBLE);

			rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_DEAD);
			rowConfidenceTextual.setTextColor(Color.RED);
		}
		else
		{
			rowConfidenceCircular.setVisibility(View.GONE);
			rowConfidenceTextual.setVisibility(View.VISIBLE);

			rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_UNKNOWN);
			rowConfidenceTextual.setTextColor(colorInsignificant);
		}

		// spendability
		final int textColor;
		if (confidenceType == ConfidenceType.DEAD)
			textColor = Color.RED;
		else
			textColor = DefaultCoinSelector.isSelectable(tx) ? colorSignificant : colorInsignificant;

		// time
		final TextView rowTime = (TextView) row.findViewById(R.id.transaction_row_time);
		if (rowTime != null)
		{
			final Date time = tx.getUpdateTime();
			rowTime.setText(time != null ? (DateUtils.getRelativeTimeSpanString(context, time.getTime())) : null);
			rowTime.setTextColor(textColor);
		}

		// receiving or sending
		final TextView rowFromTo = (TextView) row.findViewById(R.id.transaction_row_fromto);
		if (isInternal)
			rowFromTo.setText(R.string.symbol_internal);
		else if (txCache.sent)
			rowFromTo.setText(R.string.symbol_to);
		else
			rowFromTo.setText(R.string.symbol_from);
		rowFromTo.setTextColor(textColor);

		// coinbase
		final View rowCoinbase = row.findViewById(R.id.transaction_row_coinbase);
		rowCoinbase.setVisibility(isCoinBase ? View.VISIBLE : View.GONE);

		// address
		final TextView rowAddress = (TextView) row.findViewById(R.id.transaction_row_address);
		final String label;
		if (isCoinBase)
			label = textCoinBase;
		else if (isInternal)
			label = textInternal;
		else if (txCache.address != null)
			label = resolveLabel(txCache.address.toString());
		else
			label = "?";
		rowAddress.setTextColor(textColor);
		rowAddress.setText(label != null ? label : txCache.address.toString());
		rowAddress.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

		// fee
		final View rowExtendFee = row.findViewById(R.id.transaction_row_extend_fee);
		if (rowExtendFee != null)
		{
			final CurrencyTextView rowFee = (CurrencyTextView) row.findViewById(R.id.transaction_row_fee);
			rowExtendFee.setVisibility(hasFee ? View.VISIBLE : View.GONE);
			rowFee.setAlwaysSigned(true);
			rowFee.setFormat(format);
			if (hasFee)
				rowFee.setAmount(fee.negate());
		}

		// value
		final CurrencyTextView rowValue = (CurrencyTextView) row.findViewById(R.id.transaction_row_value);
		rowValue.setTextColor(textColor);
		rowValue.setAlwaysSigned(true);
		rowValue.setFormat(format);
		rowValue.setAmount(hasFee && rowExtendFee != null ? txCache.value.add(fee) : txCache.value);

		// message
		final View rowExtendMessage = row.findViewById(R.id.transaction_row_extend_message);
		if (rowExtendMessage != null)
		{
			final TextView rowMessage = (TextView) row.findViewById(R.id.transaction_row_message);
			final boolean isTimeLocked = tx.isTimeLocked();
			rowExtendMessage.setVisibility(View.GONE);

			if (isInternal)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(Html.fromHtml(context.getString(R.string.transaction_row_message_purpose_key_rotation)));
				rowMessage.setTextColor(colorSignificant);
			}
			else if (isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_own_unbroadcasted);
				rowMessage.setTextColor(colorInsignificant);
			}
			else if (!isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() == 0)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_received_direct);
				rowMessage.setTextColor(colorInsignificant);
			}
			else if (!txCache.sent && txCache.value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_received_dust);
				rowMessage.setTextColor(colorInsignificant);
			}
			else if (!txCache.sent && confidenceType == ConfidenceType.PENDING && isTimeLocked)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_received_unconfirmed_locked);
				rowMessage.setTextColor(colorError);
			}
			else if (!txCache.sent && confidenceType == ConfidenceType.PENDING && !isTimeLocked)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_received_unconfirmed_unlocked);
				rowMessage.setTextColor(colorInsignificant);
			}
			else if (!txCache.sent && confidenceType == ConfidenceType.DEAD)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_received_dead);
				rowMessage.setTextColor(colorError);
			}
			else if (!txCache.sent && tx.getOutputs().size() > 20)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_received_pay_to_many);
				rowMessage.setTextColor(colorInsignificant);
			}
			else if (tx.getMemo() != null)
			{
				rowExtendMessage.setVisibility(View.VISIBLE);
				rowMessage.setText(tx.getMemo());
				rowMessage.setTextColor(colorInsignificant);
			}
		}
	}

	private String resolveLabel(@Nonnull final String address)
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
