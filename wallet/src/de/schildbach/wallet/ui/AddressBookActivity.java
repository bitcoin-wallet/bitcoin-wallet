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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet.util.ZoomOutPageTransformer;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.wallet.Wallet;

/**
 * @author Andreas Schildbach
 */
public final class AddressBookActivity extends AbstractWalletActivity {
    public static void start(final Context context) {
        context.startActivity(new Intent(context, AddressBookActivity.class));
    }

    private AbstractWalletActivityViewModel walletActivityViewModel;
    private AddressBookViewModel viewModel;

    private static final int REQUEST_CODE_SCAN = 0;

    public static final int POSITION_WALLET_ADDRESSES = 0;
    public static final int POSITION_SENDING_ADDRESSES = 1;
    private static final int[] TAB_LABELS = { R.string.address_book_list_receiving_title,
            R.string.address_book_list_sending_title };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final FragmentManager fragmentManager = getSupportFragmentManager();

        setContentView(R.layout.address_book_content);
        final ViewPager2 pager = findViewById(R.id.address_book_pager);
        final ViewPagerTabs pagerTabs = findViewById(R.id.address_book_pager_tabs);

        pagerTabs.addTabLabels(TAB_LABELS);

        final boolean twoPanes = getResources().getBoolean(R.bool.address_book_two_panes);

        walletActivityViewModel = new ViewModelProvider(this).get(AbstractWalletActivityViewModel.class);
        walletActivityViewModel.wallet.observe(this, wallet -> invalidateOptionsMenu());
        viewModel = new ViewModelProvider(this).get(AddressBookViewModel.class);
        viewModel.pageTo.observe(this, new Event.Observer<Integer>() {
            @Override
            protected void onEvent(final Integer position) {
                if (!twoPanes)
                    pager.setCurrentItem(position, true);
            }
        });
        viewModel.showEditAddressBookEntryDialog.observe(this, new Event.Observer<Address>() {
            @Override
            protected void onEvent(final Address address) {
                EditAddressBookEntryFragment.edit(fragmentManager, address);
            }
        });
        viewModel.showScanOwnAddressDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                final DialogBuilder dialog = DialogBuilder.dialog(AddressBookActivity.this,
                        R.string.address_book_options_scan_title, R.string.address_book_options_scan_own_address);
                dialog.singleDismissButton(null);
                dialog.show();
            }
        });
        viewModel.showScanInvalidDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                final DialogBuilder dialog = DialogBuilder.dialog(AddressBookActivity.this,
                        R.string.address_book_options_scan_title, R.string.address_book_options_scan_invalid);
                dialog.singleDismissButton(null);
                dialog.show();
            }
        });

        if (twoPanes) {
            final RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
            recyclerView.setClipToPadding(false);
            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                final int width = recyclerView.getWidth();
                recyclerView.setPadding(0, 0, width / 2, 0);
                pager.setCurrentItem(0);
            });
            pager.setUserInputEnabled(false);
            pagerTabs.setMode(ViewPagerTabs.Mode.STATIC);
        } else {
            pager.setPageTransformer(new ZoomOutPageTransformer());
            pager.registerOnPageChangeCallback(pagerTabs.getPageChangeCallback());
            pagerTabs.setMode(ViewPagerTabs.Mode.DYNAMIC);
        }

        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new AddressBookActivity.PagerAdapter());
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                new InputParser.StringInputParser(input) {
                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                        if (paymentIntent.hasAddress()) {
                            final Wallet wallet = walletActivityViewModel.wallet.getValue();
                            final Address address = paymentIntent.getAddress();
                            if (!wallet.isAddressMine(address)) {
                                viewModel.showEditAddressBookEntryDialog.setValue(new Event<>(address));
                            } else {
                                viewModel.showScanOwnAddressDialog.setValue(Event.simple());
                            }
                        } else {
                            viewModel.showScanInvalidDialog.setValue(Event.simple());
                        }
                    }

                    @Override
                    protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                        cannotClassify(input);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        viewModel.showScanInvalidDialog.setValue(Event.simple());
                    }
                }.parse();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.address_book_activity_options, menu);
        final PackageManager pm = getPackageManager();
        menu.findItem(R.id.sending_addresses_options_scan).setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.sending_addresses_options_scan) {
            ScanActivity.startForResult(this, REQUEST_CODE_SCAN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class PagerAdapter extends FragmentStateAdapter {
        public PagerAdapter() {
            super(AddressBookActivity.this);
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            if (position == POSITION_WALLET_ADDRESSES)
                return new WalletAddressesFragment();
            else if (position == POSITION_SENDING_ADDRESSES)
                return new SendingAddressesFragment();
            else
                throw new IllegalArgumentException();
        }
    }
}
