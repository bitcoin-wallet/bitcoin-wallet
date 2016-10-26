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

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.os.Bundle;

/**
 * @author Andreas Schildbach
 */
public class ProgressDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = ProgressDialogFragment.class.getName();

    private static final String KEY_MESSAGE = "message";

    public static void showProgress(final FragmentManager fm, final String message) {
        final ProgressDialogFragment fragment = instance(message);
        fragment.show(fm, FRAGMENT_TAG);
    }

    public static void dismissProgress(final FragmentManager fm) {
        final DialogFragment fragment = (DialogFragment) fm.findFragmentByTag(FRAGMENT_TAG);
        fragment.dismiss();
    }

    private static ProgressDialogFragment instance(final String message) {
        final ProgressDialogFragment fragment = new ProgressDialogFragment();

        final Bundle args = new Bundle();
        args.putString(KEY_MESSAGE, message);
        fragment.setArguments(args);

        return fragment;
    }

    private Activity activity;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
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
