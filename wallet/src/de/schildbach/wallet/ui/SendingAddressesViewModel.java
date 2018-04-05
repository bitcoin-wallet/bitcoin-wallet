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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.Wallet;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AbstractWalletLiveData;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.data.WalletLiveData;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ClipboardManager.OnPrimaryClipChangedListener;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.CursorLoader;

/**
 * @author Andreas Schildbach
 */
public class SendingAddressesViewModel extends AndroidViewModel {
    private final WalletApplication application;
    public final WalletLiveData wallet;
    public final AddressBookLiveData addressBook;
    public final AddressesToExcludeLiveData addressesToExclude;
    public final ClipLiveData clip;

    public SendingAddressesViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.wallet = new WalletLiveData(this.application);
        this.addressBook = new AddressBookLiveData(this.application);
        this.addressesToExclude = new AddressesToExcludeLiveData(this.application);
        this.clip = new ClipLiveData(this.application);
    }

    public static class AddressBookLiveData extends LiveData<Cursor> {
        private final CursorLoader loader;

        public AddressBookLiveData(final WalletApplication application) {
            final Uri uri = AddressBookProvider.contentUri(application.getPackageName());
            this.loader = new CursorLoader(application, uri, null, AddressBookProvider.SELECTION_NOTIN,
                    new String[] { "" }, AddressBookProvider.KEY_LABEL + " COLLATE LOCALIZED ASC") {
                @Override
                public void deliverResult(final Cursor cursor) {
                    setValue(cursor);
                }
            };
        }

        @Override
        protected void onActive() {
            loader.startLoading();
        }

        @Override
        protected void onInactive() {
            loader.stopLoading();
        }

        public void setWalletAddressesSelection(final String walletAddressesSelection) {
            loader.setSelectionArgs(new String[] { walletAddressesSelection != null ? walletAddressesSelection : "" });
            loader.forceLoad();
        }
    }

    public class AddressesToExcludeLiveData extends AbstractWalletLiveData<Set<Address>> {
        public AddressesToExcludeLiveData(final WalletApplication application) {
            super(application);
        }

        @Override
        protected void onWalletActive(final Wallet wallet) {
            loadAddressesToExclude();
        }

        private void loadAddressesToExclude() {
            final Wallet wallet = getWallet();
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    final List<ECKey> derivedKeys = wallet.getIssuedReceiveKeys();
                    Collections.sort(derivedKeys, DeterministicKey.CHILDNUM_ORDER);
                    final List<ECKey> randomKeys = wallet.getImportedKeys();

                    final Set<Address> addresses = new HashSet<>(derivedKeys.size() + randomKeys.size());
                    for (final ECKey key : Iterables.concat(derivedKeys, randomKeys))
                        addresses.add(key.toAddress(Constants.NETWORK_PARAMETERS));
                    postValue(addresses);
                }
            });
        }

        public String commaSeparated() {
            return Joiner.on(',').join(getValue());
        }
    }

    public static class ClipLiveData extends LiveData<ClipData> implements OnPrimaryClipChangedListener {
        private final ClipboardManager clipboardManager;

        public ClipLiveData(final WalletApplication application) {
            clipboardManager = (ClipboardManager) application.getSystemService(Context.CLIPBOARD_SERVICE);
        }

        @Override
        protected void onActive() {
            clipboardManager.addPrimaryClipChangedListener(this);
            onPrimaryClipChanged();
        }

        @Override
        protected void onInactive() {
            clipboardManager.removePrimaryClipChangedListener(this);
        }

        @Override
        public void onPrimaryClipChanged() {
            setValue(clipboardManager.getPrimaryClip());
        }

        public void setClipData(final ClipData clipData) {
            clipboardManager.setPrimaryClip(clipData);
        }
    }
}
