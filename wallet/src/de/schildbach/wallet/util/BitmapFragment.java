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

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class BitmapFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = BitmapFragment.class.getName();

	private static final String KEY_BITMAP = "bitmap";
	private static final String KEY_ADDRESS = "address";
	private static final String KEY_LABEL = "label";

	private static final Logger log = LoggerFactory.getLogger(BitmapFragment.class);

	public static void show(final FragmentManager fm, final Bitmap bitmap)
	{
		instance(bitmap, null, null).show(fm, FRAGMENT_TAG);
	}

	public static void show(final FragmentManager fm, final Bitmap bitmap, final Spanned label, @Nullable final CharSequence address)
	{
		instance(bitmap, label, address).show(fm, FRAGMENT_TAG);
	}

	private static BitmapFragment instance(final Bitmap bitmap, @Nullable final Spanned label, @Nullable final CharSequence address)
	{
		final BitmapFragment fragment = new BitmapFragment();

		final Bundle args = new Bundle();
		args.putParcelable(KEY_BITMAP, bitmap);
		if (label != null)
			args.putCharSequence(KEY_LABEL, Html.toHtml(label));
		if (address != null)
			args.putCharSequence(KEY_ADDRESS, address);
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
		final Bitmap bitmap = (Bitmap) args.getParcelable(KEY_BITMAP);
		final CharSequence label = args.getCharSequence(KEY_LABEL);
		final CharSequence address = args.getCharSequence(KEY_ADDRESS);

		final Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.bitmap_dialog);
		dialog.setCanceledOnTouchOutside(true);

		final ImageView imageView = (ImageView) dialog.findViewById(R.id.bitmap_dialog_image);
		imageView.setImageBitmap(bitmap);

		final View labelButtonView = dialog.findViewById(R.id.bitmap_dialog_label_button);
		final TextView labelView = (TextView) dialog.findViewById(R.id.bitmap_dialog_label);
		if (getResources().getBoolean(R.bool.show_bitmap_dialog_label) && label != null)
		{
			labelView.setText(Html.fromHtml(Formats.maybeRemoveOuterHtmlParagraph(label)));
			labelButtonView.setVisibility(View.VISIBLE);

			if (address != null)
			{
				labelButtonView.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(final View v)
					{
						final Intent intent = new Intent(Intent.ACTION_SEND);
						intent.setType("text/plain");
						intent.putExtra(Intent.EXTRA_TEXT, address);
						startActivity(Intent.createChooser(intent, getString(R.string.bitmap_fragment_share)));
						log.info("address shared via intent: {}", address);
					}
				});
			}
			else
			{
				labelButtonView.setEnabled(false);
			}
		}
		else
		{
			labelButtonView.setVisibility(View.GONE);
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
