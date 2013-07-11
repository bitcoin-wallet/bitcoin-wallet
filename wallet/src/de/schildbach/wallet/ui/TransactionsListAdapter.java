/*
 * Copyright 2011-2013 the original author or authors.
 * Copyright 2013 Google Inc.
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Transaction.Purpose;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet.DefaultCoinSelector;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.CircularProgressView;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class TransactionsListAdapter extends BaseAdapter
{
	private final Context context;
	private final LayoutInflater inflater;
	private final WalletApplication walletApplication;
	private final int maxConnectedPeers;

	private final List<Transaction> transactions = new ArrayList<Transaction>();
	private int precision = Constants.BTC_MAX_PRECISION;
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

	private static final Logger log = LoggerFactory.getLogger(TransactionsListAdapter.class);

	public TransactionsListAdapter(final Context context, final WalletApplication walletApplication, final int maxConnectedPeers, final boolean showBackupWarning)
	{
		this.context = context;
		inflater = LayoutInflater.from(context);

		this.walletApplication = walletApplication;
		this.maxConnectedPeers = maxConnectedPeers;
		this.showBackupWarning = showBackupWarning;

		final Resources resources = context.getResources();
		colorSignificant = resources.getColor(R.color.fg_significant);
		colorInsignificant = resources.getColor(R.color.fg_insignificant);
		colorError = resources.getColor(R.color.fg_error);
		textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
		textInternal = context.getString(R.string.wallet_transactions_fragment_internal);
	}

	public void setPrecision(final int precision)
	{
		this.precision = precision;

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

	// Overridden during testing to set time more easily
	protected long getCurrentTimeSecs() {
		return System.currentTimeMillis()/1000;
	}

	public void bindView(final View row, final Transaction tx)
	{
		// Payment channel refund/payment transactions never make it this far, so we have to process both the contract
		// (this tx) and the payment/refund now
		// For normal transactions the row view is fairly self-explanatory - to/from address, confirmation level, value
		// sent/received, and extra warnings for unconfirmed/special transactions.
		// For payment channel contracts it gets a bit more complicated - the to address should be the app which
		// requested the channel be opened (from the ContractHashToCreatorMap in the wallet).
		// The confirmation level depends on the spend state, initially the confirmation level of the contract is shown,
		// after it is spent the confirmation level shown is either that of the payment/refund transaction if it has an
		// output which comes back to us or simply the confirmation level of the contract.
		// Some additional warning messages are present for contracts, one which informs the user that the contract may
		// be partially refunded if we have not yet seen a spend of the contract, and a special version of the
		// unconfirmed message telling the user that the output to us in the refund/spend transaction is not yet
		// confirmed.


		// paymentChannelCreatorApp == to address or null if this is not a payment channel contract
		String paymentChannelCreatorApp = walletApplication.getContractHashToCreatorMap().getCreatorApp(tx.getHash());
		Transaction paymentChannelSpend = null; // Either refund or incomplete spend (ie with a refund output)

		// First output is contract output, and if it is either connected to a non-final transaction (ie a locked refund
		// transaction) or unconnected, then we treat this contract as being unclosed
		final boolean isContractClosed = tx.getOutput(0).isAvailableForSpending() ||
				!tx.getOutput(0).getSpentBy().getParentTransaction().isFinal(0, getCurrentTimeSecs() + 5*60);

		if (paymentChannelCreatorApp != null && !isContractClosed) {
			paymentChannelSpend = tx.getOutput(0).getSpentBy().getParentTransaction();
			boolean isOutputToUs = false;
			for (TransactionOutput output : paymentChannelSpend.getOutputs())
				if (output.isMine(walletApplication.getWallet())) {
					isOutputToUs = true;
					break;
				}
			if (!isOutputToUs) {
				log.debug("binding View for payment channel contract that was spent fully");
				paymentChannelSpend = null; // If there is no refund component, just ignore it
			} else
				log.debug("binding View for payment channel contract that was spent with a refund component");
		}

		final TransactionConfidence confidence = paymentChannelSpend == null ? tx.getConfidence() : paymentChannelSpend.getConfidence();
		final ConfidenceType confidenceType = confidence.getConfidenceType();
		final boolean isOwn = confidence.getSource().equals(TransactionConfidence.Source.SELF);
		final boolean isCoinBase = tx.isCoinBase();
		final boolean isInternal = WalletUtils.isInternal(tx);

		try
		{
			BigInteger value = tx.getValue(walletApplication.getWallet());
			final boolean sent = value.signum() < 0;

			if (paymentChannelSpend != null)
				value = value.add(paymentChannelSpend.getValue(walletApplication.getWallet()));

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
				rowConfidenceCircular.setMaxSize(maxConnectedPeers - 1);
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
			else if (paymentChannelSpend == null)
				textColor = DefaultCoinSelector.isSelectable(tx) ? colorSignificant : colorInsignificant;
			else
				textColor = DefaultCoinSelector.isSelectable(paymentChannelSpend) ? colorSignificant : colorInsignificant;

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
			else if (sent)
				rowFromTo.setText(R.string.symbol_to);
			else
				rowFromTo.setText(R.string.symbol_from);
			rowFromTo.setTextColor(textColor);

			// coinbase
			final View rowCoinbase = row.findViewById(R.id.transaction_row_coinbase);
			rowCoinbase.setVisibility(isCoinBase ? View.VISIBLE : View.GONE);

			// address
			final TextView rowAddress = (TextView) row.findViewById(R.id.transaction_row_address);
			final Address address = sent ? WalletUtils.getToAddress(tx) : WalletUtils.getFromAddress(tx);
			final String label;
			if (isCoinBase)
				label = textCoinBase;
			else if (isInternal)
				label = textInternal;
			else if (address != null)
				label = resolveLabel(address.toString());
			else if (paymentChannelCreatorApp != null)
				label = paymentChannelCreatorApp;
			else
				label = "?";
			rowAddress.setTextColor(textColor);
			rowAddress.setText(label != null ? label : address.toString());
			rowAddress.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

			// value
			final CurrencyTextView rowValue = (CurrencyTextView) row.findViewById(R.id.transaction_row_value);
			rowValue.setTextColor(textColor);
			rowValue.setAlwaysSigned(true);
			rowValue.setPrecision(precision);
			rowValue.setAmount(value);

			// extended message
			final View rowExtend = row.findViewById(R.id.transaction_row_extend);
			if (rowExtend != null)
			{
				final TextView rowMessage = (TextView) row.findViewById(R.id.transaction_row_message);
				final boolean isTimeLocked = tx.isTimeLocked();
				final boolean contractSpendUnseen = !walletApplication.getContractHashToCreatorMap().isSpendSeen(tx.getHash());

				rowExtend.setVisibility(View.GONE);

				if (tx.getPurpose() == Purpose.KEY_ROTATION)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(Html.fromHtml(context.getString(R.string.transaction_row_message_purpose_key_rotation)));
					rowMessage.setTextColor(colorSignificant);
				}
				else if (paymentChannelCreatorApp != null && sent && contractSpendUnseen && isContractClosed &&
						confidence.getConfidenceType() != ConfidenceType.DEAD)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_channel_locked);
					rowMessage.setTextColor(colorInsignificant);
				}
				else if (paymentChannelSpend != null && confidence.getConfidenceType() == ConfidenceType.PENDING)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_channel_unlock_unconfirmed);
					rowMessage.setTextColor(colorInsignificant);
				}
				else if (isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() <= 1)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_own_unbroadcasted);
					rowMessage.setTextColor(colorInsignificant);
				}
				else if (!sent && value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0 && paymentChannelCreatorApp != null)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_dust);
					rowMessage.setTextColor(colorInsignificant);
				}
				else if (!sent && confidenceType == ConfidenceType.PENDING && isTimeLocked)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_unconfirmed_locked);
					rowMessage.setTextColor(colorError);
				}
				else if (!sent && confidenceType == ConfidenceType.PENDING && !isTimeLocked)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_unconfirmed_unlocked);
					rowMessage.setTextColor(colorInsignificant);
				}
				else if (!sent && confidenceType == ConfidenceType.DEAD)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_dead);
					rowMessage.setTextColor(colorError);
				}
			}
		}
		catch (final ScriptException x)
		{
			throw new RuntimeException(x);
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
