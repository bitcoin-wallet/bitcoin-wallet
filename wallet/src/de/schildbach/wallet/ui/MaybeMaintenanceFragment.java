/*
 * Copyright 2014-2015 the original author or authors.
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

import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.DeterministicUpgradeRequiresPassword;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.ListenableFuture;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.ui.send.MaintenanceDialogFragment;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

/**
 * @author Andreas Schildbach
 */
public class MaybeMaintenanceFragment extends Fragment {
    private static final String FRAGMENT_TAG = MaybeMaintenanceFragment.class.getName();

    public static void add(final FragmentManager fm) {
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new MaybeMaintenanceFragment();
            fm.beginTransaction().add(fragment, FRAGMENT_TAG).commit();
        }
    }

    private Wallet wallet;
    private LocalBroadcastManager broadcastManager;
    private boolean dialogWasShown = false;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        final WalletApplication application = ((AbstractWalletActivity) activity).getWalletApplication();
        this.wallet = application.getWallet();
        this.broadcastManager = LocalBroadcastManager.getInstance(activity);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        broadcastManager.registerReceiver(broadcastReceiver,
                new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
    }

    @Override
    public void onPause() {
        broadcastManager.unregisterReceiver(broadcastReceiver);

        super.onPause();
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent broadcast) {
            final BlockchainState blockchainState = BlockchainState.fromIntent(broadcast);

            if (!dialogWasShown && !blockchainState.replaying && maintenanceRecommended()) {
                MaintenanceDialogFragment.show(getFragmentManager());
                dialogWasShown = true;
            }
        }
    };

    private boolean maintenanceRecommended() {
        try {
            final ListenableFuture<List<Transaction>> result = wallet.doMaintenance(null, false);
            return !result.get().isEmpty();
        } catch (final DeterministicUpgradeRequiresPassword x) {
            return true;
        } catch (final Exception x) {
            throw new RuntimeException(x);
        }
    }
}
