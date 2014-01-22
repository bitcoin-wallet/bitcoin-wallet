/*
 * Copyright 2013-2014 the original author or authors.
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.Html;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class HelpDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = HelpDialogFragment.class.getName();

	private static final String KEY_MESSAGE = "message";

	public static void page(final FragmentManager fm, final int messageResId)
	{
		final DialogFragment newFragment = HelpDialogFragment.instance(messageResId);
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private static HelpDialogFragment instance(final int messageResId)
	{
		final HelpDialogFragment fragment = new HelpDialogFragment();

		final Bundle args = new Bundle();
		args.putInt(KEY_MESSAGE, messageResId);
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Bundle args = getArguments();
        final int messageResId = args.getInt(KEY_MESSAGE);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setMessage(Html.fromHtml(getString(messageResId)))
                .setNeutralButton(R.string.button_dismiss, null);

        return builder.create();
	}
}
