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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Window;
import android.widget.ImageView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import de.schildbach.wallet.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class BitmapFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = BitmapFragment.class.getName();
    private static final String KEY_BITMAP = "bitmap";

    public static void show(final FragmentManager fm, final Bitmap bitmap) {
        instance(bitmap).show(fm, FRAGMENT_TAG);
    }

    private static BitmapFragment instance(final Bitmap bitmap) {
        final BitmapFragment fragment = new BitmapFragment();

        final Bundle args = new Bundle();
        args.putParcelable(KEY_BITMAP, bitmap);
        fragment.setArguments(args);

        return fragment;
    }

    private Activity activity;

    private static final Logger log = LoggerFactory.getLogger(BitmapFragment.class);

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (AbstractWalletActivity) context;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("opening dialog {}", getClass().getName());
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final BitmapDrawable bitmap = new BitmapDrawable(getResources(), (Bitmap) args.getParcelable(KEY_BITMAP));
        bitmap.setFilterBitmap(false);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.bitmap_dialog);
        dialog.setCanceledOnTouchOutside(true);

        final ImageView imageView = dialog.findViewById(R.id.bitmap_dialog_image);
        imageView.setImageDrawable(bitmap);
        imageView.setOnClickListener(v -> dismissAllowingStateLoss());

        return dialog;
    }
}
