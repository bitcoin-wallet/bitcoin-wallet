/*
 * Copyright 2013-2015 the original author or authors.
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

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.TimeLiveData;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet_test.R;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ViewAnimator;

/**
 * @author Andreas Schildbach
 */
public final class BlockListFragment extends Fragment implements BlockListAdapter.OnClickListener {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private BlockListAdapter adapter;

    private ViewModel viewModel;

    private static final int MAX_BLOCKS = 64;

    private static final Logger log = LoggerFactory.getLogger(BlockListFragment.class);

    public static class ViewModel extends AndroidViewModel {
        private final WalletApplication application;
        private BlocksLiveData blocks;
        private TransactionsLiveData transactions;
        private TimeLiveData time;

        public ViewModel(final Application application) {
            super(application);
            this.application = (WalletApplication) application;
        }

        public BlocksLiveData getBlocks() {
            if (blocks == null)
                blocks = new BlocksLiveData(application);
            return blocks;
        }

        public TransactionsLiveData getTransactions() {
            if (transactions == null)
                transactions = new TransactionsLiveData(application);
            return transactions;
        }

        public TimeLiveData getTime() {
            if (time == null)
                time = new TimeLiveData(application);
            return time;
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = this.activity.getWalletApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        viewModel.getBlocks().observe(this, new Observer<List<StoredBlock>>() {
            @Override
            public void onChanged(final List<StoredBlock> blocks) {
                adapter.replace(blocks);
                viewGroup.setDisplayedChild(1);
                viewModel.getTransactions().forceLoad();
            }
        });
        viewModel.getTransactions().observe(this, new Observer<Set<Transaction>>() {
            @Override
            public void onChanged(final Set<Transaction> transactions) {
                adapter.replaceTransactions(transactions);
            }
        });
        viewModel.getTime().observe(this, new Observer<Date>() {
            @Override
            public void onChanged(final Date time) {
                adapter.setTime(time);
            }
        });

        adapter = new BlockListAdapter(activity, wallet, this);
        adapter.setFormat(config.getFormat());
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.block_list_fragment, container, false);

        viewGroup = (ViewAnimator) view.findViewById(R.id.block_list_group);

        recyclerView = (RecyclerView) view.findViewById(R.id.block_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));

        return view;
    }

    @Override
    public void onBlockMenuClick(final View view, final StoredBlock block) {
        final PopupMenu popupMenu = new PopupMenu(activity, view);
        popupMenu.inflate(R.menu.blocks_context);
        popupMenu.getMenu().findItem(R.id.blocks_context_browse).setVisible(Constants.ENABLE_BROWSE);
        popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                switch (item.getItemId()) {
                case R.id.blocks_context_browse:
                    final String blockHash = block.getHeader().getHashAsString();
                    final Uri blockExplorerUri = config.getBlockExplorer();
                    log.info("Viewing block {} on {}", blockHash, blockExplorerUri);
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.withAppendedPath(blockExplorerUri, "block/" + blockHash)));
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    private static class BlocksLiveData extends LiveData<List<StoredBlock>> implements ServiceConnection {
        private final WalletApplication application;
        private final LocalBroadcastManager broadcastManager;
        private BlockchainService blockchainService;

        private BlocksLiveData(final WalletApplication application) {
            this.application = application;
            this.broadcastManager = LocalBroadcastManager.getInstance(application);
        }

        @Override
        protected void onActive() {
            broadcastManager.registerReceiver(broadcastReceiver,
                    new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
            application.bindService(new Intent(application, BlockchainService.class), this, Context.BIND_AUTO_CREATE);
        }

        @Override
        protected void onInactive() {
            application.unbindService(this);
            broadcastManager.unregisterReceiver(broadcastReceiver);
        }

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            blockchainService = ((BlockchainService.LocalBinder) service).getService();
            setValue(blockchainService.getRecentBlocks(MAX_BLOCKS));
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            blockchainService = null;
        }

        private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                if (blockchainService != null)
                    setValue(blockchainService.getRecentBlocks(MAX_BLOCKS));
            }
        };
    }

    private static class TransactionsLiveData extends LiveData<Set<Transaction>> {
        private final Wallet wallet;

        private TransactionsLiveData(final WalletApplication application) {
            this.wallet = application.getWallet();
        }

        @Override
        protected void onActive() {
            forceLoad();
        }

        public void forceLoad() {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

                    final Set<Transaction> transactions = wallet.getTransactions(false);
                    final Set<Transaction> filteredTransactions = new HashSet<Transaction>(transactions.size());
                    for (final Transaction tx : transactions) {
                        final Map<Sha256Hash, Integer> appearsIn = tx.getAppearsInHashes();
                        if (appearsIn != null && !appearsIn.isEmpty()) // TODO filter by updateTime
                            filteredTransactions.add(tx);
                    }
                    postValue(filteredTransactions);
                }
            });
        }
    }
}
