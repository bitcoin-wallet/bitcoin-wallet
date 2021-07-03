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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * @author Andreas Schildbach
 */
public class ProgressDialogFragment extends DialogFragment {
    public static class Observer implements androidx.lifecycle.Observer<String> {
        private final FragmentManager fm;

        public Observer(final FragmentManager fm) {
            this.fm = fm;
        }

        @Override
        public void onChanged(final String message) {
            final DialogFragment fragment = (DialogFragment) fm.findFragmentByTag(FRAGMENT_TAG);
            if (fragment != null) {
                fm.beginTransaction().remove(fragment).commit();
            }
            if (message != null) {
                final Fragment newFragment = new ProgressDialogFragment();
                final Bundle args = new Bundle();
                args.putString(KEY_MESSAGE, message);
                newFragment.setArguments(args);
                fm.beginTransaction().add(newFragment, FRAGMENT_TAG).commit();
            }
        }
    }

    private static final String FRAGMENT_TAG = ProgressDialogFragment.class.getName();
    private static final String KEY_MESSAGE = "message";

    private Activity activity;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (Activity) context;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final String message = args.getString(KEY_MESSAGE);

        return ProgressDialog.show(activity, null, message, true);
    }
}
