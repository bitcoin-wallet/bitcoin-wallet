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

package de.schildbach.wallet.ui.monitor;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewAnimator;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.AbstractWalletActivityViewModel;
import de.schildbach.wallet.ui.StickToTopLinearLayoutManager;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * @author Andreas Schildbach
 */
public final class BlockListFragment extends Fragment implements BlockListAdapter.OnClickListener,
        BlockListAdapter.ContextMenuCallback {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private BlockListAdapter adapter;

    private AbstractWalletActivityViewModel walletActivityViewModel;
    private NetworkMonitorViewModel activityViewModel;
    private BlockListViewModel viewModel;

    private static final Logger log = LoggerFactory.getLogger(BlockListFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = this.activity.getWalletApplication();
        this.config = application.getConfiguration();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        walletActivityViewModel = new ViewModelProvider(activity).get(AbstractWalletActivityViewModel.class);
        walletActivityViewModel.wallet.observe(this, wallet -> maybeSubmitList());
        activityViewModel = new ViewModelProvider(activity).get(NetworkMonitorViewModel.class);
        activityViewModel.selectedItem.observe(this, item -> {
            if (item instanceof Sha256Hash) {
                final Sha256Hash blockHash = (Sha256Hash) item;
                adapter.setSelectedBlock(blockHash);
                final int position = adapter.positionOf(blockHash);
                if (position != RecyclerView.NO_POSITION)
                    recyclerView.smoothScrollToPosition(position);
            } else {
                adapter.setSelectedBlock(null);
            }
        });
        viewModel = new ViewModelProvider(this).get(BlockListViewModel.class);
        viewModel.blocks.observe(this, blocks -> {
            maybeSubmitList();
            viewGroup.setDisplayedChild(1);
            viewModel.getTransactions().loadTransactions();
        });
        viewModel.getTransactions().observe(this, transactions -> maybeSubmitList());
        viewModel.getTime().observe(this, time -> maybeSubmitList());

        adapter = new BlockListAdapter(activity, this, this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.block_list_fragment, container, false);
        viewGroup = view.findViewById(R.id.block_list_group);
        recyclerView = view.findViewById(R.id.block_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new StickToTopLinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        return view;
    }

    private void maybeSubmitList() {
        final List<StoredBlock> blocks = viewModel.blocks.getValue();
        final Wallet wallet = walletActivityViewModel.wallet.getValue();
        if (blocks != null) {
            final Map<String, AddressBookEntry> addressBook = AddressBookEntry.asMap(viewModel.addressBook.getValue());
            adapter.submitList(BlockListAdapter.buildListItems(activity, blocks, viewModel.getTime().getValue(),
                    config.getFormat(), viewModel.getTransactions().getValue(), wallet, addressBook));
        }
    }

    @Override
    public void onBlockClick(final View view, final Sha256Hash blockHash) {
        activityViewModel.selectedItem.setValue(blockHash);
    }

    @Override
    public void onInflateBlockContextMenu(final MenuInflater inflater, final Menu menu) {
        inflater.inflate(R.menu.blocks_context, menu);
        menu.findItem(R.id.blocks_context_browse).setVisible(Constants.ENABLE_BROWSE);
    }

    @Override
    public boolean onClickBlockContextMenuItem(final MenuItem item, final Sha256Hash blockHash) {
        final int itemId = item.getItemId();
        if (itemId == R.id.blocks_context_browse) {
            final Uri blockExplorerUri = config.getBlockExplorer();
            log.info("Viewing block {} on {}", blockHash, blockExplorerUri);
            activity.startExternalDocument(Uri.withAppendedPath(blockExplorerUri, "block/" + blockHash));
            return true;
        } else {
            return false;
        }
    }
}
