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

import androidx.core.content.ContextCompat;
import org.bitcoinj.core.Coin;

import com.google.common.base.Strings;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.exchangerate.ExchangeRateEntry;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.ViewAnimator;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * @author Andreas Schildbach
 */
public final class ExchangeRatesFragment extends Fragment
        implements OnSharedPreferenceChangeListener, ExchangeRatesAdapter.OnClickListener {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private ExchangeRatesAdapter adapter;

    private ExchangeRatesViewModel viewModel;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.config = application.getConfiguration();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        viewModel = new ViewModelProvider(this).get(ExchangeRatesViewModel.class);
        if (Constants.ENABLE_EXCHANGE_RATES) {
            viewModel.getExchangeRates().observe(this, exchangeRates -> {
                if (!exchangeRates.isEmpty()) {
                    viewGroup.setDisplayedChild(2);
                    maybeSubmitList();

                    final String defaultCurrency = config.getExchangeCurrencyCode();
                    if (defaultCurrency != null) {
                        int i = 0;
                        for (final ExchangeRateEntry exchangeRate : exchangeRates) {
                            if (exchangeRate.getCurrencyCode().equals(defaultCurrency)) {
                                recyclerView.scrollToPosition(i);
                                break;
                            }
                            i++;
                        }
                    }

                    if (activity instanceof ExchangeRatesActivity) {
                        final String source = exchangeRates.iterator().next().getSource();
                        activity.getActionBar().setSubtitle(getString(R.string.exchange_rates_fragment_source, source));
                    }
                } else if (exchangeRates.isEmpty() && viewModel.isConstrained()) {
                    viewGroup.setDisplayedChild(1);
                } else {
                    viewGroup.setDisplayedChild(0);
                }
            });
        }
        viewModel.getBalance().observe(this, balance -> maybeSubmitList());
        application.blockchainState.observe(this, blockchainState -> maybeSubmitList());

        adapter = new ExchangeRatesAdapter(activity, this);

        config.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.exchange_rates_fragment, container, false);
        viewGroup = view.findViewById(R.id.exchange_rates_list_group);
        recyclerView = view.findViewById(R.id.exchange_rates_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));
        return view;
    }

    @Override
    public void onDestroy() {
        config.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    private void maybeSubmitList() {
        final List<ExchangeRateEntry> exchangeRates = viewModel.getExchangeRates().getValue();
        if (exchangeRates != null)
            adapter.submitList(ExchangeRatesAdapter.buildListItems(exchangeRates, viewModel.getBalance().getValue(),
                    application.blockchainState.getValue(), config.getExchangeCurrencyCode(), config.getBtcBase()));
    }

    @Override
    public void onExchangeRateMenuClick(final View view, final String currencyCode) {
        final PopupMenu popupMenu = new PopupMenu(activity, view);
        popupMenu.inflate(R.menu.exchange_rates_context);
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.exchange_rates_context_set_as_default) {
                config.setExchangeCurrencyCode(currencyCode);
                return true;
            } else {
                return false;
            }
        });
        popupMenu.show();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.exchange_rates_fragment_options, menu);

        final MenuItem searchMenuItem = menu.findItem(R.id.exchange_rates_options_search);
        if (Constants.ENABLE_EXCHANGE_RATES) {
            final SearchView searchView = (SearchView) searchMenuItem.getActionView();
            searchView.setOnQueryTextListener(new OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(final String newText) {
                    viewModel.setConstraint(Strings.emptyToNull(newText.trim()));
                    maybeSubmitList();
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(final String query) {
                    searchView.clearFocus();
                    return true;
                }
            });

            // Workaround for not being able to style the SearchView
            final int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null,
                    null);
            final EditText searchInput = searchView.findViewById(id);
            searchInput.setTextColor(ContextCompat.getColor(activity, R.color.fg_on_dark_bg_network_significant));
            searchInput.setHintTextColor(ContextCompat.getColor(activity, R.color.fg_on_dark_bg_network_insignificant));
        } else {
            searchMenuItem.setVisible(false);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
            maybeSubmitList();
        else if (Configuration.PREFS_KEY_BTC_PRECISION.equals(key))
            maybeSubmitList();
    }
}
