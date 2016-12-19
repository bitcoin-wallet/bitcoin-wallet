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

import com.google.common.base.Strings;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.data.ExchangeRate;
import de.schildbach.wallet.data.ExchangeRatesProvider;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainStateLoader;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.ViewAnimator;

/**
 * @author Andreas Schildbach
 */
public final class ExchangeRatesFragment extends Fragment implements OnSharedPreferenceChangeListener {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private Uri contentUri;
    private LoaderManager loaderManager;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private ExchangeRatesAdapter adapter;

    private String query = null;
    @Nullable
    private BlockchainState blockchainState = null;

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

        adapter = new ExchangeRatesAdapter();
        adapter.setRateBase(config.getBtcBase());
        adapter.setDefaultCurrency(config.getExchangeCurrencyCode());

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
        config.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.exchange_rates_fragment, container, false);
        viewGroup = (ViewAnimator) view.findViewById(R.id.exchange_rates_list_group);
        recyclerView = (RecyclerView) view.findViewById(R.id.exchange_rates_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
        loaderManager.initLoader(ID_BLOCKCHAIN_STATE_LOADER, null, blockchainStateLoaderCallbacks);
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
                query = Strings.emptyToNull(newText.trim());
                getLoaderManager().restartLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

                return true;
            }

            @Override
            public boolean onQueryTextSubmit(final String query) {
                searchView.clearFocus();

                return true;
            }
        });

        // Workaround for not being able to style the SearchView
        final int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        final View searchInput = searchView.findViewById(id);
        if (searchInput instanceof EditText)
            ((EditText) searchInput).setTextColor(Color.WHITE);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
            adapter.setDefaultCurrency(config.getExchangeCurrencyCode());
        else if (Configuration.PREFS_KEY_BTC_PRECISION.equals(key))
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
            adapter.setCursor(data);
            if (adapter.getItemCount() == 0 && query == null) {
                viewGroup.setDisplayedChild(1);
            } else if (adapter.getItemCount() == 0 && query != null) {
                viewGroup.setDisplayedChild(2);
            } else {
                viewGroup.setDisplayedChild(3);
                final int positionToScrollTo = adapter.getDefaultCurrencyPosition();
                if (positionToScrollTo != RecyclerView.NO_POSITION)
                    recyclerView.scrollToPosition(positionToScrollTo);
                if (activity instanceof ExchangeRatesActivity) {
                    data.moveToPosition(0);
                    final String source = ExchangeRatesProvider.getExchangeRate(data).source;
                    activity.getActionBar().setSubtitle(
                            source != null ? getString(R.string.exchange_rates_fragment_source, source) : null);
                }
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    private final LoaderCallbacks<Coin> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<Coin>() {
        @Override
        public Loader<Coin> onCreateLoader(final int id, final Bundle args) {
            return new WalletBalanceLoader(activity, wallet);
        }

        @Override
        public void onLoadFinished(final Loader<Coin> loader, final Coin balance) {
            adapter.setBalance(balance);
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
            adapter.setBlockchainState(blockchainState);
        }

        @Override
        public void onLoaderReset(final Loader<BlockchainState> loader) {
        }
    };

    private final class ExchangeRatesAdapter extends RecyclerView.Adapter<ExchangeRateViewHolder> {
        private final LayoutInflater inflater = LayoutInflater.from(activity);

        private Cursor cursor = null;
        private Coin rateBase = Coin.COIN;
        @Nullable
        private String defaultCurrency = null;
        @Nullable
        private Coin balance = null;
        @Nullable
        private BlockchainState blockchainState = null;

        private ExchangeRatesAdapter() {
            setHasStableIds(true);
        }

        public void setCursor(final Cursor cursor) {
            this.cursor = cursor;
            notifyDataSetChanged();
        }

        public void setDefaultCurrency(final String defaultCurrency) {
            this.defaultCurrency = defaultCurrency;
            notifyDataSetChanged();
        }

        public void setRateBase(final Coin rateBase) {
            this.rateBase = rateBase;
            notifyDataSetChanged();
        }

        public void setBalance(final Coin balance) {
            this.balance = balance;
            notifyDataSetChanged();
        }

        public void setBlockchainState(final BlockchainState blockchainState) {
            this.blockchainState = blockchainState;
            notifyDataSetChanged();
        }

        public int getDefaultCurrencyPosition() {
            if (cursor == null || defaultCurrency == null)
                return RecyclerView.NO_POSITION;

            cursor.moveToPosition(-1);
            while (cursor.moveToNext())
                if (cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE))
                        .equals(defaultCurrency))
                    return cursor.getPosition();
            return RecyclerView.NO_POSITION;
        }

        @Override
        public int getItemCount() {
            return cursor != null ? cursor.getCount() : 0;
        }

        @Override
        public long getItemId(final int position) {
            cursor.moveToPosition(position);
            return cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
        }

        @Override
        public ExchangeRateViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            return new ExchangeRateViewHolder(inflater.inflate(R.layout.exchange_rate_row, parent, false));
        }

        @Override
        public void onBindViewHolder(final ExchangeRateViewHolder holder, final int position) {
            cursor.moveToPosition(position);
            final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);
            final boolean isDefaultCurrency = exchangeRate.getCurrencyCode().equals(defaultCurrency);

            holder.itemView.setBackgroundResource(isDefaultCurrency ? R.color.bg_list_selected : R.color.bg_list);

            holder.defaultView.setVisibility(isDefaultCurrency ? View.VISIBLE : View.INVISIBLE);

            holder.currencyCodeView.setText(exchangeRate.getCurrencyCode());

            holder.rateView.setFormat(!rateBase.isLessThan(Coin.COIN) ? Constants.LOCAL_FORMAT.minDecimals(2)
                    : Constants.LOCAL_FORMAT.minDecimals(4));
            holder.rateView.setAmount(exchangeRate.rate.coinToFiat(rateBase));

            holder.walletView.setFormat(Constants.LOCAL_FORMAT);
            if (balance != null && (blockchainState == null || !blockchainState.replaying)) {
                holder.walletView.setAmount(exchangeRate.rate.coinToFiat(balance));
                holder.walletView.setStrikeThru(Constants.TEST);
            } else {
                holder.walletView.setText("n/a");
                holder.walletView.setStrikeThru(false);
            }

            holder.menuView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final PopupMenu popupMenu = new PopupMenu(activity, v);
                    popupMenu.inflate(R.menu.exchange_rates_context);
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(final MenuItem item) {
                            if (item.getItemId() == R.id.exchange_rates_context_set_as_default) {
                                setDefaultCurrency(exchangeRate.getCurrencyCode());
                                config.setExchangeCurrencyCode(exchangeRate.getCurrencyCode());
                                WalletBalanceWidgetProvider.updateWidgets(activity, wallet);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
                    popupMenu.show();
                }
            });
        }
    }

    private final class ExchangeRateViewHolder extends RecyclerView.ViewHolder {
        private final View defaultView;
        private final TextView currencyCodeView;
        private final CurrencyTextView rateView;
        private final CurrencyTextView walletView;
        private final ImageButton menuView;

        public ExchangeRateViewHolder(final View itemView) {
            super(itemView);
            defaultView = itemView.findViewById(R.id.exchange_rate_row_default);
            currencyCodeView = (TextView) itemView.findViewById(R.id.exchange_rate_row_currency_code);
            rateView = (CurrencyTextView) itemView.findViewById(R.id.exchange_rate_row_rate);
            walletView = (CurrencyTextView) itemView.findViewById(R.id.exchange_rate_row_balance);
            menuView = (ImageButton) itemView.findViewById(R.id.exchange_rate_row_menu);
        }
    }
}
