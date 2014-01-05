/*
 * Copyright 2011-2014 the original author or authors.
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

package de.schildbach.wallet.util;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public class BitmapFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = BitmapFragment.class.getName();

	private static final String KEY_BITMAP = "bitmap";

	public static void show(final FragmentManager fm, @Nonnull final Bitmap bitmap)
	{
		final DialogFragment newFragment = instance(bitmap);
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private static BitmapFragment instance(@Nonnull final Bitmap bitmap)
	{
		final BitmapFragment fragment = new BitmapFragment();

		final Bundle args = new Bundle();
		args.putParcelable(KEY_BITMAP, bitmap);
		fragment.setArguments(args);

		return fragment;
	}

	private Activity activity;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Bitmap bitmap = (Bitmap) getArguments().getParcelable(KEY_BITMAP);

		final Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.bitmap_dialog);
		dialog.setCanceledOnTouchOutside(true);

		final ImageView imageView = (ImageView) dialog.findViewById(R.id.bitmap_dialog_image);
		imageView.setImageBitmap(bitmap);
		imageView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				dismiss();
			}
		});

		return dialog;
	}
}
