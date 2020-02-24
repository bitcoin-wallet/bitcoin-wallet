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
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.util.ViewPagerTabs;
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

    private WalletAddressesFragment walletAddressesFragment;
    private SendingAddressesFragment sendingAddressesFragment;

    private AddressBookViewModel viewModel;

    private static final String TAG_LEFT = "wallet_addresses";
    private static final String TAG_RIGHT = "sending_addresses";

    private static final int REQUEST_CODE_SCAN = 0;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.address_book_content);

        final FragmentManager fragmentManager = getSupportFragmentManager();

        viewModel = new ViewModelProvider(this).get(AddressBookViewModel.class);
        viewModel.wallet.observe(this, wallet -> invalidateOptionsMenu());
        viewModel.showEditAddressBookEntryDialog.observe(this, new Event.Observer<Address>() {
            @Override
            public void onEvent(final Address address) {
                EditAddressBookEntryFragment.edit(fragmentManager, address);
            }
        });
        viewModel.showScanOwnAddressDialog.observe(this, new Event.Observer<Void>() {
            @Override
            public void onEvent(final Void v) {
                final DialogBuilder dialog = DialogBuilder.dialog(AddressBookActivity.this,
                        R.string.address_book_options_scan_title, R.string.address_book_options_scan_own_address);
                dialog.singleDismissButton(null);
                dialog.show();
            }
        });
        viewModel.showScanInvalidDialog.observe(this, new Event.Observer<Void>() {
            @Override
            public void onEvent(final Void v) {
                final DialogBuilder dialog = DialogBuilder.dialog(AddressBookActivity.this,
                        R.string.address_book_options_scan_title, R.string.address_book_options_scan_invalid);
                dialog.singleDismissButton(null);
                dialog.show();
            }
        });

        walletAddressesFragment = (WalletAddressesFragment) fragmentManager.findFragmentByTag(TAG_LEFT);
        sendingAddressesFragment = (SendingAddressesFragment) fragmentManager.findFragmentByTag(TAG_RIGHT);

        final FragmentTransaction removal = fragmentManager.beginTransaction();

        if (walletAddressesFragment == null)
            walletAddressesFragment = new WalletAddressesFragment();
        else
            removal.remove(walletAddressesFragment);

        if (sendingAddressesFragment == null)
            sendingAddressesFragment = new SendingAddressesFragment();
        else
            removal.remove(sendingAddressesFragment);

        if (!removal.isEmpty()) {
            removal.commit();
            fragmentManager.executePendingTransactions();
        }

        final ViewPager pager = findViewById(R.id.address_book_pager);
        if (pager != null) {
            pager.setAdapter(
                    new TwoFragmentAdapter(fragmentManager, walletAddressesFragment, sendingAddressesFragment));

            final ViewPagerTabs pagerTabs = findViewById(R.id.address_book_pager_tabs);
            pagerTabs.addTabLabels(R.string.address_book_list_receiving_title,
                    R.string.address_book_list_sending_title);

            pager.setOnPageChangeListener(pagerTabs);
            final int position = 1;
            pager.setCurrentItem(position);
            pager.setPageMargin(2);
            pager.setPageMarginDrawable(R.color.bg_level0);

            pagerTabs.onPageSelected(position);
            pagerTabs.onPageScrolled(position, 0, 0);
        } else {
            fragmentManager.beginTransaction().add(R.id.wallet_addresses_fragment, walletAddressesFragment, TAG_LEFT)
                    .add(R.id.sending_addresses_fragment, sendingAddressesFragment, TAG_RIGHT).commit();
        }
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
                            final Wallet wallet = viewModel.wallet.getValue();
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

    private static class TwoFragmentAdapter extends PagerAdapter {
        private final FragmentManager fragmentManager;
        private final Fragment left;
        private final Fragment right;

        private FragmentTransaction currentTransaction = null;
        private Fragment currentPrimaryItem = null;

        public TwoFragmentAdapter(final FragmentManager fragmentManager, final Fragment left, final Fragment right) {
            this.fragmentManager = fragmentManager;
            this.left = left;
            this.right = right;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            if (currentTransaction == null)
                currentTransaction = fragmentManager.beginTransaction();

            final String tag = (position == 0) ? TAG_LEFT : TAG_RIGHT;
            final Fragment fragment = (position == 0) ? left : right;
            currentTransaction.add(container.getId(), fragment, tag);

            if (fragment != currentPrimaryItem) {
                fragment.setMenuVisibility(false);
                fragment.setUserVisibleHint(false);
            }

            return fragment;
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position, final Object object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPrimaryItem(final ViewGroup container, final int position, final Object object) {
            final Fragment fragment = (Fragment) object;
            if (fragment != currentPrimaryItem) {
                if (currentPrimaryItem != null) {
                    currentPrimaryItem.setMenuVisibility(false);
                    currentPrimaryItem.setUserVisibleHint(false);
                }
                if (fragment != null) {
                    fragment.setMenuVisibility(true);
                    fragment.setUserVisibleHint(true);
                }
                currentPrimaryItem = fragment;
            }
        }

        @Override
        public void finishUpdate(final ViewGroup container) {
            if (currentTransaction != null) {
                currentTransaction.commitAllowingStateLoss();
                currentTransaction = null;
                fragmentManager.executePendingTransactions();
            }
        }

        @Override
        public boolean isViewFromObject(final View view, final Object object) {
            return ((Fragment) object).getView() == view;
        }
    }
}
