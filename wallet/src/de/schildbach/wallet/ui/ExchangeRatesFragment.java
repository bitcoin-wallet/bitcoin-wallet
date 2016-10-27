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

import javax.annotation.Nullable;

import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.data.ExchangeRate;
import de.schildbach.wallet.data.ExchangeRatesProvider;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainStateLoader;
import de.schildbach.wallet.util.WholeStringBuilder;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public final class ExchangeRatesFragment extends FancyListFragment implements OnSharedPreferenceChangeListener {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private Uri contentUri;
    private LoaderManager loaderManager;

    private ExchangeRatesAdapter adapter;
    private String query = null;

    private Coin balance = null;
    @Nullable
    private BlockchainState blockchainState = null;
    @Nullable
    private String defaultCurrency = null;

    private static final int ID_BALANCE_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;
    private static final int ID_BLOCKCHAIN_STATE_LOADER = 2;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractWalletActivity) activity;
        this.application = (WalletApplication) activity.getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        this.contentUri = ExchangeRatesProvider.contentUri(activity.getPackageName(), false);
        this.loaderManager = getLoaderManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        adapter = new ExchangeRatesAdapter(activity);
        setListAdapter(adapter);

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

        defaultCurrency = config.getExchangeCurrencyCode();
        config.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getListView().setFastScrollEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
        loaderManager.initLoader(ID_BLOCKCHAIN_STATE_LOADER, null, blockchainStateLoaderCallbacks);

        updateView();
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_BALANCE_LOADER);
        loaderManager.destroyLoader(ID_BLOCKCHAIN_STATE_LOADER);

        super.onPause();
    }

    @Override
    public void onDestroy() {
        config.unregisterOnSharedPreferenceChangeListener(this);

        loaderManager.destroyLoader(ID_RATE_LOADER);

        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.exchange_rates_fragment_options, menu);

        final SearchView searchView = (SearchView) menu.findItem(R.id.exchange_rates_options_search).getActionView();
        searchView.setOnQueryTextListener(new OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(final String newText) {
                query = newText.trim();
                if (query.isEmpty())
                    query = null;

                getLoaderManager().restartLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

                return true;
            }

            @Override
            public boolean onQueryTextSubmit(final String query) {
                searchView.clearFocus();

                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onListItemClick(final ListView l, final View v, final int position, final long id) {
        final Cursor cursor = (Cursor) adapter.getItem(position);
        final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);

        activity.startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.exchange_rates_context, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                mode.setTitle(exchangeRate.getCurrencyCode());
                mode.setSubtitle(getString(R.string.exchange_rates_fragment_source, exchangeRate.source));

                return true;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                switch (item.getItemId()) {
                case R.id.exchange_rates_context_set_as_default:
                    handleSetAsDefault(exchangeRate.getCurrencyCode());

                    mode.finish();
                    return true;
                }

                return false;
            }

            @Override
            public void onDestroyActionMode(final ActionMode mode) {
            }

            private void handleSetAsDefault(final String currencyCode) {
                config.setExchangeCurrencyCode(currencyCode);

                WalletBalanceWidgetProvider.updateWidgets(activity, wallet);
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key)
                || Configuration.PREFS_KEY_BTC_PRECISION.equals(key)) {
            defaultCurrency = config.getExchangeCurrencyCode();

            updateView();
        }
    }

    private void updateView() {
        balance = application.getWallet().getBalance(BalanceType.ESTIMATED);

        if (adapter != null)
            adapter.setRateBase(config.getBtcBase());
    }

    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            if (query == null)
                return new CursorLoader(activity, contentUri, null, null, null, null);
            else
                return new CursorLoader(activity, contentUri, null, ExchangeRatesProvider.QUERY_PARAM_Q,
                        new String[] { query }, null);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            final Cursor oldCursor = adapter.swapCursor(data);

            if (data != null && oldCursor == null && defaultCurrency != null) {
                final int defaultCurrencyPosition = findCurrencyCode(data, defaultCurrency);
                if (defaultCurrencyPosition >= 0)
                    getListView().setSelection(defaultCurrencyPosition); // scroll to selection
            }

            setEmptyText(WholeStringBuilder.bold(getString(query != null ? R.string.exchange_rates_fragment_empty_search
                    : R.string.exchange_rates_fragment_empty_text)));
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }

        private int findCurrencyCode(final Cursor cursor, final String currencyCode) {
            final int currencyCodeColumn = cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE);

            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                if (cursor.getString(currencyCodeColumn).equals(currencyCode))
                    return cursor.getPosition();
            }

            return -1;
        }
    };

    private final LoaderCallbacks<Coin> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<Coin>() {
        @Override
        public Loader<Coin> onCreateLoader(final int id, final Bundle args) {
            return new WalletBalanceLoader(activity, wallet);
        }

        @Override
        public void onLoadFinished(final Loader<Coin> loader, final Coin balance) {
            ExchangeRatesFragment.this.balance = balance;

            updateView();
        }

        @Override
        public void onLoaderReset(final Loader<Coin> loader) {
        }
    };

    private final LoaderCallbacks<BlockchainState> blockchainStateLoaderCallbacks = new LoaderManager.LoaderCallbacks<BlockchainState>() {
        @Override
        public Loader<BlockchainState> onCreateLoader(final int id, final Bundle args) {
            return new BlockchainStateLoader(activity);
        }

        @Override
        public void onLoadFinished(final Loader<BlockchainState> loader, final BlockchainState blockchainState) {
            ExchangeRatesFragment.this.blockchainState = blockchainState;

            updateView();
        }

        @Override
        public void onLoaderReset(final Loader<BlockchainState> loader) {
        }
    };

    private final class ExchangeRatesAdapter extends ResourceCursorAdapter {
        private Coin rateBase = Coin.COIN;

        private ExchangeRatesAdapter(final Context context) {
            super(context, R.layout.exchange_rate_row, null, true);
        }

        public void setRateBase(final Coin rateBase) {
            this.rateBase = rateBase;

            notifyDataSetChanged();
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {
            final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);
            final boolean isDefaultCurrency = exchangeRate.getCurrencyCode().equals(defaultCurrency);

            view.setBackgroundResource(isDefaultCurrency ? R.color.bg_list_selected : R.color.bg_list);

            final View defaultView = view.findViewById(R.id.exchange_rate_row_default);
            defaultView.setVisibility(isDefaultCurrency ? View.VISIBLE : View.INVISIBLE);

            final TextView currencyCodeView = (TextView) view.findViewById(R.id.exchange_rate_row_currency_code);
            currencyCodeView.setText(exchangeRate.getCurrencyCode());

            final CurrencyTextView rateView = (CurrencyTextView) view.findViewById(R.id.exchange_rate_row_rate);
            rateView.setFormat(!rateBase.isLessThan(Coin.COIN) ? Constants.LOCAL_FORMAT.minDecimals(2)
                    : Constants.LOCAL_FORMAT.minDecimals(4));
            rateView.setAmount(exchangeRate.rate.coinToFiat(rateBase));

            final CurrencyTextView walletView = (CurrencyTextView) view.findViewById(R.id.exchange_rate_row_balance);
            walletView.setFormat(Constants.LOCAL_FORMAT);
            if (blockchainState == null || !blockchainState.replaying) {
                walletView.setAmount(exchangeRate.rate.coinToFiat(balance));
                walletView.setStrikeThru(Constants.TEST);
            } else {
                walletView.setText("n/a");
                walletView.setStrikeThru(false);
            }
            walletView.setTextColor(getResources().getColor(R.color.fg_less_significant));
        }
    }
}
