/*
 * Copyright 2011-2015 the original author or authors.
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
import javax.annotation.Nullable;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class BitmapFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = BitmapFragment.class.getName();

	private static final String KEY_BITMAP = "bitmap";
	private static final String KEY_LABEL = "label";

	public static void show(final FragmentManager fm, @Nonnull final Bitmap bitmap)
	{
		instance(bitmap, null).show(fm, FRAGMENT_TAG);
	}

	public static void show(final FragmentManager fm, @Nonnull final Bitmap bitmap, @Nonnull final Spanned label)
	{
		instance(bitmap, label).show(fm, FRAGMENT_TAG);
	}

	private static BitmapFragment instance(@Nonnull final Bitmap bitmap, @Nullable final Spanned label)
	{
		final BitmapFragment fragment = new BitmapFragment();

		final Bundle args = new Bundle();
		args.putParcelable(KEY_BITMAP, bitmap);
		if (label != null)
			args.putString(KEY_LABEL, Html.toHtml(label));
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
		final Bundle args = getArguments();

		final Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.bitmap_dialog);
		dialog.setCanceledOnTouchOutside(true);

		final ImageView imageView = (ImageView) dialog.findViewById(R.id.bitmap_dialog_image);
		final Bitmap bitmap = (Bitmap) args.getParcelable(KEY_BITMAP);
		imageView.setImageBitmap(bitmap);

		final TextView labelView = (TextView) dialog.findViewById(R.id.bitmap_dialog_label);
		if (getResources().getBoolean(R.bool.show_bitmap_dialog_label) && args.containsKey(KEY_LABEL))
		{
			final String maybeRemoveOuterHtmlParagraph = Formats.maybeRemoveOuterHtmlParagraph(args.getString(KEY_LABEL));
			final Spanned label = Html.fromHtml(maybeRemoveOuterHtmlParagraph);
			labelView.setText(label);
			labelView.setVisibility(View.VISIBLE);
		}
		else
		{
			labelView.setVisibility(View.GONE);
		}

		final View dialogView = dialog.findViewById(R.id.bitmap_dialog_group);
		dialogView.setOnClickListener(new View.OnClickListener()
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
