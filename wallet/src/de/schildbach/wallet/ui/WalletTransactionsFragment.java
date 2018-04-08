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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.ui.TransactionsAdapter.ListItem;
import de.schildbach.wallet.ui.send.RaiseFeeDialogFragment;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.WalletUtils;

import android.app.admin.DevicePolicyManager;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.ViewAnimator;

/**
 * @author Andreas Schildbach
 */
public class WalletTransactionsFragment extends Fragment implements TransactionsAdapter.OnClickListener {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private DevicePolicyManager devicePolicyManager;

    private ViewAnimator viewGroup;
    private TextView emptyView;
    private RecyclerView recyclerView;
    private TransactionsAdapter adapter;
    private MenuItem filterMenuItem;

    private WalletTransactionsViewModel viewModel;

    private static final Uri KEY_ROTATION_URI = Uri.parse("https://bitcoin.org/en/alert/2013-08-11-android");
    private static final int SHOW_QR_THRESHOLD_BYTES = 2500;

    private static final Logger log = LoggerFactory.getLogger(WalletTransactionsFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
        this.application = activity.getWalletApplication();
        this.config = application.getConfiguration();
        this.devicePolicyManager = (DevicePolicyManager) application.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        viewModel = ViewModelProviders.of(this).get(WalletTransactionsViewModel.class);
        viewModel.transactions.observe(this, new Observer<Set<Transaction>>() {
            @Override
            public void onChanged(final Set<Transaction> transactions) {
                if (transactions.isEmpty()) {
                    viewGroup.setDisplayedChild(1);

                    final WalletTransactionsViewModel.Direction direction = viewModel.direction.getValue();
                    final SpannableStringBuilder emptyText = new SpannableStringBuilder(
                            getString(direction == WalletTransactionsViewModel.Direction.SENT
                                    ? R.string.wallet_transactions_fragment_empty_text_sent
                                    : R.string.wallet_transactions_fragment_empty_text_received));
                    emptyText.setSpan(new StyleSpan(Typeface.BOLD), 0, emptyText.length(),
                            SpannableStringBuilder.SPAN_POINT_MARK);
                    if (direction != WalletTransactionsViewModel.Direction.SENT)
                        emptyText.append("\n\n")
                                .append(getString(R.string.wallet_transactions_fragment_empty_text_howto));
                    emptyView.setText(emptyText);
                } else {
                    viewGroup.setDisplayedChild(2);
                }
            }
        });
        viewModel.list.observe(this, new Observer<List<ListItem>>() {
            @Override
            public void onChanged(final List<ListItem> listItems) {
                adapter.submitList(listItems);
                ViewModelProviders.of(activity).get(WalletActivity.ViewModel.class).transactionsLoadingFinished();
            }
        });

        adapter = new TransactionsAdapter(activity, true, application.maxConnectedPeers(), this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.wallet_transactions_fragment, container, false);

        viewGroup = (ViewAnimator) view.findViewById(R.id.wallet_transactions_group);
        viewGroup.setDisplayedChild(2); // don't show progress

        emptyView = (TextView) view.findViewById(R.id.wallet_transactions_empty);

        recyclerView = (RecyclerView) view.findViewById(R.id.wallet_transactions_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new StickToTopLinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            private final int PADDING = 2
                    * activity.getResources().getDimensionPixelOffset(R.dimen.card_padding_vertical);

            @Override
            public void getItemOffsets(final Rect outRect, final View view, final RecyclerView parent,
                    final RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);

                final int position = parent.getChildAdapterPosition(view);
                if (position == 0)
                    outRect.top += PADDING;
                else if (position == parent.getAdapter().getItemCount() - 1)
                    outRect.bottom += PADDING;
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.setWarning(warning());
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.wallet_transactions_fragment_options, menu);
        filterMenuItem = menu.findItem(R.id.wallet_transactions_options_filter);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        final WalletTransactionsViewModel.Direction direction = viewModel.direction.getValue();
        if (direction == null) {
            menu.findItem(R.id.wallet_transactions_options_filter_all).setChecked(true);
            maybeSetFilterMenuItemIcon(R.drawable.ic_filter_list_white_24dp);
        } else if (direction == WalletTransactionsViewModel.Direction.RECEIVED) {
            menu.findItem(R.id.wallet_transactions_options_filter_received).setChecked(true);
            maybeSetFilterMenuItemIcon(R.drawable.transactions_list_filter_received);
        } else if (direction == WalletTransactionsViewModel.Direction.SENT) {
            menu.findItem(R.id.wallet_transactions_options_filter_sent).setChecked(true);
            maybeSetFilterMenuItemIcon(R.drawable.transactions_list_filter_sent);
        }
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        final WalletTransactionsViewModel.Direction direction;
        if (itemId == R.id.wallet_transactions_options_filter_all) {
            direction = null;
            maybeSetFilterMenuItemIcon(R.drawable.ic_filter_list_white_24dp);
        } else if (itemId == R.id.wallet_transactions_options_filter_received) {
            direction = WalletTransactionsViewModel.Direction.RECEIVED;
            maybeSetFilterMenuItemIcon(R.drawable.transactions_list_filter_received);
        } else if (itemId == R.id.wallet_transactions_options_filter_sent) {
            direction = WalletTransactionsViewModel.Direction.SENT;
            maybeSetFilterMenuItemIcon(R.drawable.transactions_list_filter_sent);
        } else {
            return false;
        }
        item.setChecked(true);

        viewModel.setDirection(direction);
        return true;
    }

    private void maybeSetFilterMenuItemIcon(final int iconResId) {
        // Older Android versions can't deal with width and height in XML layer-list items.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            filterMenuItem.setIcon(iconResId);
    }

    @Override
    public void onTransactionMenuClick(final View view, final Sha256Hash transactionHash) {
        final Wallet wallet = viewModel.wallet.getValue();
        final Transaction tx = wallet.getTransaction(transactionHash);
        final boolean txSent = tx.getValue(wallet).signum() < 0;
        final Address txAddress = txSent ? WalletUtils.getToAddressOfSent(tx, wallet)
                : WalletUtils.getWalletAddressOfReceived(tx, wallet);
        final byte[] txSerialized = tx.unsafeBitcoinSerialize();
        final boolean txRotation = tx.getPurpose() == Purpose.KEY_ROTATION;

        final PopupMenu popupMenu = new PopupMenu(activity, view);
        popupMenu.inflate(R.menu.wallet_transactions_context);
        final MenuItem editAddressMenuItem = popupMenu.getMenu()
                .findItem(R.id.wallet_transactions_context_edit_address);
        if (!txRotation && txAddress != null) {
            editAddressMenuItem.setVisible(true);
            final boolean isAdd = AddressBookProvider.resolveLabel(activity, txAddress.toBase58()) == null;
            final boolean isOwn = wallet.isPubKeyHashMine(txAddress.getHash160());

            if (isOwn)
                editAddressMenuItem.setTitle(isAdd ? R.string.edit_address_book_entry_dialog_title_add_receive
                        : R.string.edit_address_book_entry_dialog_title_edit_receive);
            else
                editAddressMenuItem.setTitle(isAdd ? R.string.edit_address_book_entry_dialog_title_add
                        : R.string.edit_address_book_entry_dialog_title_edit);
        } else {
            editAddressMenuItem.setVisible(false);
        }

        popupMenu.getMenu().findItem(R.id.wallet_transactions_context_show_qr)
                .setVisible(!txRotation && txSerialized.length < SHOW_QR_THRESHOLD_BYTES);
        popupMenu.getMenu().findItem(R.id.wallet_transactions_context_raise_fee)
                .setVisible(RaiseFeeDialogFragment.feeCanLikelyBeRaised(wallet, tx));
        popupMenu.getMenu().findItem(R.id.wallet_transactions_context_browse).setVisible(Constants.ENABLE_BROWSE);
        popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                switch (item.getItemId()) {
                case R.id.wallet_transactions_context_edit_address:
                    handleEditAddress(tx);
                    return true;

                case R.id.wallet_transactions_context_show_qr:
                    handleShowQr();
                    return true;

                case R.id.wallet_transactions_context_raise_fee:
                    RaiseFeeDialogFragment.show(getFragmentManager(), tx);
                    return true;

                case R.id.wallet_transactions_context_report_issue:
                    handleReportIssue(tx);
                    return true;

                case R.id.wallet_transactions_context_browse:
                    if (!txRotation) {
                        final String txHash = tx.getHashAsString();
                        final Uri blockExplorerUri = config.getBlockExplorer();
                        log.info("Viewing transaction {} on {}", txHash, blockExplorerUri);
                        startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(blockExplorerUri, "tx/" + txHash)));
                    } else {
                        startActivity(new Intent(Intent.ACTION_VIEW, KEY_ROTATION_URI));
                    }
                    return true;
                }

                return false;
            }

            private void handleEditAddress(final Transaction tx) {
                EditAddressBookEntryFragment.edit(getFragmentManager(), txAddress);
            }

            private void handleShowQr() {
                final Bitmap qrCodeBitmap = Qr.bitmap(Qr.encodeCompressBinary(txSerialized));
                BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
            }

            private void handleReportIssue(final Transaction tx) {
                final StringBuilder contextualData = new StringBuilder();
                try {
                    contextualData.append(tx.getValue(wallet).toFriendlyString()).append(" total value");
                } catch (final ScriptException x) {
                    contextualData.append(x.getMessage());
                }
                contextualData.append('\n');
                if (tx.hasConfidence())
                    contextualData.append("  confidence: ").append(tx.getConfidence()).append('\n');
                contextualData.append(tx.toString());

                ReportIssueDialogFragment.show(getFragmentManager(), R.string.report_issue_dialog_title_transaction,
                        R.string.report_issue_dialog_message_issue, Constants.REPORT_SUBJECT_ISSUE,
                        contextualData.toString());
            }
        });
        popupMenu.show();
    }

    @Override
    public void onTransactionClick(final View view, final Sha256Hash transactionHash) {
        viewModel.setSelectedTransaction(transactionHash);
    }

    @Override
    public void onWarningClick(final View view) {
        switch (warning()) {
        case BACKUP:
            ((WalletActivity) activity).handleBackupWallet();
            break;

        case STORAGE_ENCRYPTION:
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            break;
        }
    }

    private TransactionsAdapter.WarningType warning() {
        final int storageEncryptionStatus = devicePolicyManager.getStorageEncryptionStatus();
        if (config.remindBackup())
            return TransactionsAdapter.WarningType.BACKUP;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && (storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE
                        || storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY))
            return TransactionsAdapter.WarningType.STORAGE_ENCRYPTION;
        else
            return null;
    }
}
