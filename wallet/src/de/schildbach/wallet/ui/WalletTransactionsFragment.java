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

import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookDao;
import de.schildbach.wallet.data.AppDatabase;
import de.schildbach.wallet.ui.TransactionsAdapter.ListItem;
import de.schildbach.wallet.ui.TransactionsAdapter.WarningType;
import de.schildbach.wallet.ui.send.RaiseFeeDialogFragment;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.WalletUtils;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

/**
 * @author Andreas Schildbach
 */
public class WalletTransactionsFragment extends Fragment implements TransactionsAdapter.OnClickListener {
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private AddressBookDao addressBookDao;
    private DevicePolicyManager devicePolicyManager;

    private ViewAnimator viewGroup;
    private TextView emptyView;
    private RecyclerView recyclerView;
    private TransactionsAdapter adapter;
    private MenuItem filterMenuItem;

    private WalletActivityViewModel activityViewModel;
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
        this.addressBookDao = AppDatabase.getDatabase(context).addressBookDao();
        this.devicePolicyManager = (DevicePolicyManager) application.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        activityViewModel = ViewModelProviders.of(activity).get(WalletActivityViewModel.class);
        viewModel = ViewModelProviders.of(this).get(WalletTransactionsViewModel.class);

        viewModel.direction.observe(this, new Observer<WalletTransactionsViewModel.Direction>() {
            @Override
            public void onChanged(final WalletTransactionsViewModel.Direction direction) {
                activity.invalidateOptionsMenu();
            }
        });
        viewModel.transactions.observe(this, new Observer<Set<Transaction>>() {
            @Override
            public void onChanged(final Set<Transaction> transactions) {
                if (transactions.isEmpty()) {
                    viewGroup.setDisplayedChild(0);

                    final WalletTransactionsViewModel.Direction direction = viewModel.direction.getValue();
                    final WarningType warning = viewModel.warning.getValue();
                    final SpannableStringBuilder emptyText = new SpannableStringBuilder(
                            getString(direction == WalletTransactionsViewModel.Direction.SENT
                                    ? R.string.wallet_transactions_fragment_empty_text_sent
                                    : R.string.wallet_transactions_fragment_empty_text_received));
                    emptyText.setSpan(new StyleSpan(Typeface.BOLD), 0, emptyText.length(),
                            SpannableStringBuilder.SPAN_POINT_MARK);
                    if (direction != WalletTransactionsViewModel.Direction.SENT)
                        emptyText.append("\n\n")
                                .append(getString(R.string.wallet_transactions_fragment_empty_text_howto));
                    if (warning == WarningType.BACKUP) {
                        final int start = emptyText.length();
                        emptyText.append("\n\n")
                                .append(getString(R.string.wallet_transactions_fragment_empty_remind_backup));
                        emptyText.setSpan(new StyleSpan(Typeface.BOLD), start, emptyText.length(),
                                SpannableStringBuilder.SPAN_POINT_MARK);
                    }
                    emptyView.setText(emptyText);
                } else {
                    viewGroup.setDisplayedChild(1);
                }
            }
        });
        viewModel.list.observe(this, new Observer<List<ListItem>>() {
            @Override
            public void onChanged(final List<ListItem> listItems) {
                adapter.submitList(listItems);
                activityViewModel.transactionsLoadingFinished();
            }
        });
        viewModel.showBitmapDialog.observe(this, new Event.Observer<Bitmap>() {
            @Override
            public void onEvent(final Bitmap bitmap) {
                BitmapFragment.show(getFragmentManager(), bitmap);
            }
        });
        viewModel.showEditAddressBookEntryDialog.observe(this, new Event.Observer<Address>() {
            @Override
            public void onEvent(final Address address) {
                EditAddressBookEntryFragment.edit(getFragmentManager(), address);
            }
        });
        viewModel.showReportIssueDialog.observe(this, new Event.Observer<String>() {
            @Override
            public void onEvent(final String contextualData) {
                ReportIssueDialogFragment.show(getFragmentManager(), R.string.report_issue_dialog_title_transaction,
                        R.string.report_issue_dialog_message_issue, Constants.REPORT_SUBJECT_ISSUE, contextualData);
            }
        });

        adapter = new TransactionsAdapter(activity, application.maxConnectedPeers(), this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.wallet_transactions_fragment, container, false);

        viewGroup = (ViewAnimator) view.findViewById(R.id.wallet_transactions_group);

        emptyView = (TextView) view.findViewById(R.id.wallet_transactions_empty);

        recyclerView = (RecyclerView) view.findViewById(R.id.wallet_transactions_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new StickToTopLinearLayoutManager(activity));
        recyclerView.setItemAnimator(new TransactionsAdapter.ItemAnimator());
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
            final boolean isAdd = addressBookDao.resolveLabel(txAddress.toString()) == null;
            final boolean isOwn = wallet.isAddressMine(txAddress);

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
                int itemId = item.getItemId();
                if (itemId == R.id.wallet_transactions_context_edit_address) {
                    viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(txAddress));
                    return true;
                } else if (itemId == R.id.wallet_transactions_context_show_qr) {
                    final Bitmap qrCodeBitmap = Qr.bitmap(Qr.encodeCompressBinary(txSerialized));
                    viewModel.showBitmapDialog.setValue(new Event<>(qrCodeBitmap));
                    return true;
                } else if (itemId == R.id.wallet_transactions_context_raise_fee) {
                    RaiseFeeDialogFragment.show(getFragmentManager(), tx);
                    return true;
                } else if (itemId == R.id.wallet_transactions_context_report_issue) {
                    handleReportIssue(tx);
                    return true;
                } else if (itemId == R.id.wallet_transactions_context_browse) {
                    if (!txRotation) {
                        final Uri blockExplorerUri = config.getBlockExplorer();
                        log.info("Viewing transaction {} on {}", tx.getTxId(), blockExplorerUri);
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.withAppendedPath(blockExplorerUri, "tx/" + tx.getTxId().toString())));
                    } else {
                        startActivity(new Intent(Intent.ACTION_VIEW, KEY_ROTATION_URI));
                    }
                    return true;
                }
                return false;
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
                final String[] blockExplorers = activity.getResources()
                        .getStringArray(R.array.preferences_block_explorer_values);
                for (final String blockExplorer : blockExplorers)
                    contextualData
                            .append(Uri.withAppendedPath(Uri.parse(blockExplorer), "tx/" + tx.getTxId().toString()))
                            .append('\n');
                contextualData.append(tx.toString());
                viewModel.showReportIssueDialog.setValue(new Event<>(contextualData.toString()));
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
        final WarningType warning = warning();
        if (warning == TransactionsAdapter.WarningType.BACKUP)
            activityViewModel.showBackupWalletDialog.setValue(Event.simple());
        else if (warning == TransactionsAdapter.WarningType.STORAGE_ENCRYPTION)
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
    }

    private TransactionsAdapter.WarningType warning() {
        if (config.remindBackup())
            return TransactionsAdapter.WarningType.BACKUP;

        final int storageEncryptionStatus = devicePolicyManager.getStorageEncryptionStatus();
        if (storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE
                || storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY)
            return TransactionsAdapter.WarningType.STORAGE_ENCRYPTION;

        return null;
    }
}
