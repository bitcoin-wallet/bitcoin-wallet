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

package de.schildbach.wallet.ui;

import java.math.BigInteger;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Transaction.Purpose;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet;

import com.google.bitcoin.script.Script;
import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public class TransactionsListFragment extends SherlockListFragment implements LoaderCallbacks<List<Transaction>>, OnSharedPreferenceChangeListener
{
	public enum Direction
	{
		RECEIVED, SENT
	}

	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Wallet wallet;
	private SharedPreferences prefs;
	private NfcManager nfcManager;
	private ContentResolver resolver;
	private LoaderManager loaderManager;

	private TransactionsListAdapter adapter;

	@CheckForNull
	private Direction direction;

	private final Handler handler = new Handler();

	private static final String KEY_DIRECTION = "direction";
	private static final long THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;
	private static final Uri KEY_ROTATION_URI = Uri.parse("http://bitcoin.org/en/alert/2013-08-11-android");

	public static TransactionsListFragment instance(@Nullable final Direction direction)
	{
		final TransactionsListFragment fragment = new TransactionsListFragment();

		final Bundle args = new Bundle();
		args.putSerializable(KEY_DIRECTION, direction);
		fragment.setArguments(args);

		return fragment;
	}

	private final ContentObserver addressBookObserver = new ContentObserver(handler)
	{
		@Override
		public void onChange(final boolean selfChange)
		{
			adapter.clearLabelCache();
		}
	};

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);
		this.resolver = activity.getContentResolver();
		this.loaderManager = getLoaderManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setRetainInstance(true);

		this.direction = (Direction) getArguments().getSerializable(KEY_DIRECTION);

		final boolean showBackupWarning = direction == null || direction == Direction.RECEIVED;

		adapter = new TransactionsListAdapter(activity, wallet, application.maxConnectedPeers(), showBackupWarning);
		setListAdapter(adapter);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		resolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true, addressBookObserver);

		prefs.registerOnSharedPreferenceChangeListener(this);

		loaderManager.initLoader(0, null, this);

		wallet.addEventListener(transactionChangeListener);

		updateView();
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		final SpannableStringBuilder emptyText = new SpannableStringBuilder(
				getString(direction == Direction.SENT ? R.string.wallet_transactions_fragment_empty_text_sent
						: R.string.wallet_transactions_fragment_empty_text_received));
		emptyText.setSpan(new StyleSpan(Typeface.BOLD), 0, emptyText.length(), SpannableStringBuilder.SPAN_POINT_MARK);
		if (direction != Direction.SENT)
			emptyText.append("\n\n").append(getString(R.string.wallet_transactions_fragment_empty_text_howto));

		setEmptyText(emptyText);
	}

	@Override
	public void onPause()
	{
		wallet.removeEventListener(transactionChangeListener);
		transactionChangeListener.removeCallbacks();

		loaderManager.destroyLoader(0);

		prefs.unregisterOnSharedPreferenceChangeListener(this);

		resolver.unregisterContentObserver(addressBookObserver);

		super.onPause();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final Transaction tx = (Transaction) adapter.getItem(position);

		if (tx == null)
			handleBackupWarningClick();
		else if (tx.getPurpose() == Purpose.KEY_ROTATION)
			handleKeyRotationClick();
		else
			handleTransactionClick(tx);
	}

	private void handleTransactionClick(@Nonnull final Transaction tx)
	{
		activity.startActionMode(new ActionMode.Callback()
		{
			private Address address;
			private byte[] serializedTx;

			private static final int SHOW_QR_THRESHOLD_BYTES = 2500;

			@Override
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.wallet_transactions_context, menu);

				return true;
			}

			@Override
			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				try
				{
					final Date time = tx.getUpdateTime();
					final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);
					final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);

					mode.setTitle(time != null ? (DateUtils.isToday(time.getTime()) ? getString(R.string.time_today) : dateFormat.format(time))
							+ ", " + timeFormat.format(time) : null);

					final BigInteger value = tx.getValue(wallet);
					final boolean sent = value.signum() < 0;

					address = sent ? WalletUtils.getFirstToAddress(tx) : WalletUtils.getFirstFromAddress(tx);

					final String label;
					if (tx.isCoinBase())
						label = getString(R.string.wallet_transactions_fragment_coinbase);
					else if (address != null)
						label = AddressBookProvider.resolveLabel(activity, address.toString());
					else
						label = "?";

					final String prefix = getString(sent ? R.string.symbol_to : R.string.symbol_from) + " ";

					if (tx.getPurpose() != Purpose.KEY_ROTATION)
						mode.setSubtitle(label != null ? prefix + label : WalletUtils.formatAddress(prefix, address,
								Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
					else
						mode.setSubtitle(null);

					menu.findItem(R.id.wallet_transactions_context_edit_address).setVisible(address != null);

					serializedTx = tx.unsafeBitcoinSerialize();

					menu.findItem(R.id.wallet_transactions_context_show_qr).setVisible(serializedTx.length < SHOW_QR_THRESHOLD_BYTES);

					Nfc.publishMimeObject(nfcManager, activity, Constants.MIMETYPE_TRANSACTION, serializedTx, false);

					return true;
				}
				catch (final ScriptException x)
				{
					return false;
				}
			}

			@Override
			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.wallet_transactions_context_edit_address:
						handleEditAddress(tx);

						mode.finish();
						return true;

					case R.id.wallet_transactions_context_show_qr:
						handleShowQr();

						mode.finish();
						return true;

                    case R.id.wallet_transactions_context_edit_note:
                        handleEditNote(tx);

                        mode.finish();
                        return true;

					case R.id.wallet_transactions_context_browse:
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.EXPLORE_BASE_URL + "tx/" + tx.getHashAsString())));

						mode.finish();
						return true;

                    case R.id.wallet_transactions_context_browse2:
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.EXPLORE2_BASE_URL + "tx/" + tx.getHashAsString())));

                        mode.finish();
                        return true;
                    case R.id.wallet_transactions_context_show_transaction:
                        TransactionActivity.show(activity, tx);
				}
				return false;
			}

			@Override
			public void onDestroyActionMode(final ActionMode mode)
			{
				Nfc.unpublish(nfcManager, activity);
			}

			private void handleEditAddress(@Nonnull final Transaction tx)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}

            private void handleEditNote(@Nonnull final Transaction tx)
            {
                EditTransactionNoteFragment.edit(getFragmentManager(), tx.getHashAsString(), adapter);
            }

			private void handleShowQr()
			{
				final int size = (int) (384 * getResources().getDisplayMetrics().density);
				final Bitmap qrCodeBitmap = Qr.bitmap(Qr.encodeBinary(serializedTx), size);
				BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
			}
		});
	}

	private void handleKeyRotationClick()
	{
		startActivity(new Intent(Intent.ACTION_VIEW, KEY_ROTATION_URI));
	}

	private void handleBackupWarningClick()
	{
		((WalletActivity) activity).handleExportKeys();
	}

	@Override
	public Loader<List<Transaction>> onCreateLoader(final int id, final Bundle args)
	{
		return new TransactionsLoader(activity, wallet, direction);
	}

	@Override
	public void onLoadFinished(final Loader<List<Transaction>> loader, final List<Transaction> transactions)
	{
		adapter.replace(transactions);
	}

	@Override
	public void onLoaderReset(final Loader<List<Transaction>> loader)
	{
		// don't clear the adapter, because it will confuse users
	}

	private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener(THROTTLE_MS)
	{
		@Override
		public void onThrottledWalletChanged()
		{
			adapter.notifyDataSetChanged();
		}

        @Override
        public void onScriptsAdded(Wallet wallet, List<Script> scripts) { }
    };

	private static class TransactionsLoader extends AsyncTaskLoader<List<Transaction>>
	{
		private final Wallet wallet;
		@CheckForNull
		private final Direction direction;

		private TransactionsLoader(final Context context, @Nonnull final Wallet wallet, @Nullable final Direction direction)
		{
			super(context);

			this.wallet = wallet;
			this.direction = direction;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			wallet.addEventListener(transactionAddRemoveListener);
			transactionAddRemoveListener.onReorganize(null); // trigger at least one reload

			forceLoad();
		}

		@Override
		protected void onStopLoading()
		{
			wallet.removeEventListener(transactionAddRemoveListener);
			transactionAddRemoveListener.removeCallbacks();

			super.onStopLoading();
		}

		@Override
		public List<Transaction> loadInBackground()
		{
			final Set<Transaction> transactions = wallet.getTransactions(true);
			final List<Transaction> filteredTransactions = new ArrayList<Transaction>(transactions.size());

			try
			{
				for (final Transaction tx : transactions)
				{
					final boolean sent = tx.getValue(wallet).signum() < 0;
					if ((direction == Direction.RECEIVED && !sent) || direction == null || (direction == Direction.SENT && sent))
						filteredTransactions.add(tx);
				}
			}
			catch (final ScriptException x)
			{
				throw new RuntimeException(x);
			}

			Collections.sort(filteredTransactions, TRANSACTION_COMPARATOR);

			return filteredTransactions;
		}

		private final ThrottlingWalletChangeListener transactionAddRemoveListener = new ThrottlingWalletChangeListener(THROTTLE_MS, true, true,
				false)
		{
			@Override
			public void onThrottledWalletChanged()
			{
				forceLoad();
			}

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts) { }
        };

		private static final Comparator<Transaction> TRANSACTION_COMPARATOR = new Comparator<Transaction>()
		{
			@Override
			public int compare(final Transaction tx1, final Transaction tx2)
			{
				final boolean pending1 = tx1.getConfidence().getConfidenceType() == ConfidenceType.PENDING;
				final boolean pending2 = tx2.getConfidence().getConfidenceType() == ConfidenceType.PENDING;

				if (pending1 != pending2)
					return pending1 ? -1 : 1;

				final Date updateTime1 = tx1.getUpdateTime();
				final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
				final Date updateTime2 = tx2.getUpdateTime();
				final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;

				if (time1 > time2)
					return -1;
				else if (time1 < time2)
					return 1;
				else
					return 0;
			}
		};
	}

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	{
		if (Constants.PREFS_KEY_BTC_PRECISION.equals(key))
			updateView();
	}

	private void updateView()
	{
		final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
		final int btcPrecision = precision.charAt(0) - '0';
		final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

		adapter.setPrecision(btcPrecision, btcShift);
		adapter.clearLabelCache();
	}
}
