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

import java.util.Date;
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
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookEntry;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DividerItemDecoration;
import de.schildbach.wallet.ui.StickToTopLinearLayoutManager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ViewAnimator;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

/**
 * @author Andreas Schildbach
 */
public final class BlockListFragment extends Fragment implements BlockListAdapter.OnClickListener {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;

    private ViewAnimator viewGroup;
    private RecyclerView recyclerView;
    private BlockListAdapter adapter;

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
        viewModel = ViewModelProviders.of(this).get(BlockListViewModel.class);
        viewModel.getBlocks().observe(this, blocks -> {
            maybeSubmitList();
            viewGroup.setDisplayedChild(1);
            viewModel.getTransactions().loadTransactions();
        });
        viewModel.getTransactions().observe(this, transactions -> maybeSubmitList());
        viewModel.getWallet().observe(this, wallet -> maybeSubmitList());
        viewModel.getTime().observe(this, time -> maybeSubmitList());

        adapter = new BlockListAdapter(activity, this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.block_list_fragment, container, false);

        viewGroup = (ViewAnimator) view.findViewById(R.id.block_list_group);

        recyclerView = (RecyclerView) view.findViewById(R.id.block_list);
        recyclerView.setLayoutManager(new StickToTopLinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));

        return view;
    }

    private void maybeSubmitList() {
        final List<StoredBlock> blocks = viewModel.getBlocks().getValue();
        if (blocks != null) {
            final Map<String, AddressBookEntry> addressBook = AddressBookEntry.asMap(viewModel.addressBook.getValue());
            adapter.submitList(BlockListAdapter.buildListItems(activity, blocks, viewModel.getTime().getValue(),
                    config.getFormat(), viewModel.getTransactions().getValue(), viewModel.getWallet().getValue(),
                    addressBook));
        }
    }

    @Override
    public void onBlockMenuClick(final View view, final Sha256Hash blockHash) {
        final PopupMenu popupMenu = new PopupMenu(activity, view);
        popupMenu.inflate(R.menu.blocks_context);
        popupMenu.getMenu().findItem(R.id.blocks_context_browse).setVisible(Constants.ENABLE_BROWSE);
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.blocks_context_browse) {
                final Uri blockExplorerUri = config.getBlockExplorer();
                log.info("Viewing block {} on {}", blockHash, blockExplorerUri);
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.withAppendedPath(blockExplorerUri, "block/" + blockHash)));
                return true;
            }
            return false;
        });
        popupMenu.show();
    }
}
