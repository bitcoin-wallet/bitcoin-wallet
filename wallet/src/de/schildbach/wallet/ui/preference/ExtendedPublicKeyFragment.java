/*
 * Copyright 2015 the original author or authors.
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

package de.schildbach.wallet.ui.preference;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class ExtendedPublicKeyFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = ExtendedPublicKeyFragment.class.getName();

	private static final String KEY_XPUB = "xpub";

	private static final Logger log = LoggerFactory.getLogger(ExtendedPublicKeyFragment.class);

	public static void show(final FragmentManager fm, final CharSequence xpub)
	{
		instance(xpub).show(fm, FRAGMENT_TAG);
	}

	private static ExtendedPublicKeyFragment instance(final CharSequence xpub)
	{
		final ExtendedPublicKeyFragment fragment = new ExtendedPublicKeyFragment();

		final Bundle args = new Bundle();
		args.putCharSequence(KEY_XPUB, xpub);
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
		final String xpub = getArguments().getCharSequence(KEY_XPUB).toString();

		final View view = LayoutInflater.from(activity).inflate(R.layout.extended_public_key_dialog, null);

		final ImageView imageView = (ImageView) view.findViewById(R.id.extended_public_key_dialog_image);
		final int size = getResources().getDimensionPixelSize(R.dimen.bitmap_dialog_qr_size);
		final Bitmap bitmap = Qr.bitmap(xpub, size);
		imageView.setImageBitmap(bitmap);

		final DialogBuilder dialog = new DialogBuilder(activity);
		dialog.setView(view);
		dialog.setNegativeButton(R.string.button_dismiss, null);
		dialog.setPositiveButton(R.string.button_share, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, xpub);
				intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.extended_public_key_fragment_title));
				startActivity(Intent.createChooser(intent, getString(R.string.extended_public_key_fragment_share)));
				log.info("xpub shared via intent: {}", xpub);
			}
		});

		return dialog.show();
	}
}
