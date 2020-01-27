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

package de.schildbach.wallet.ui.preference;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import androidx.core.app.ShareCompat;
import de.schildbach.wallet.R;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.util.Qr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class ExtendedPublicKeyFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = ExtendedPublicKeyFragment.class.getName();

    private static final String KEY_EXTENDED_PUBLIC_KEY = "extended_public_key";

    private static final Logger log = LoggerFactory.getLogger(ExtendedPublicKeyFragment.class);

    public static void show(final FragmentManager fm, final CharSequence base58) {
        instance(base58).show(fm, FRAGMENT_TAG);
    }

    private static ExtendedPublicKeyFragment instance(final CharSequence base58) {
        final ExtendedPublicKeyFragment fragment = new ExtendedPublicKeyFragment();

        final Bundle args = new Bundle();
        args.putCharSequence(KEY_EXTENDED_PUBLIC_KEY, base58);
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
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final String base58 = getArguments().getCharSequence(KEY_EXTENDED_PUBLIC_KEY).toString();

        final View view = LayoutInflater.from(activity).inflate(R.layout.extended_public_key_dialog, null);

        final BitmapDrawable bitmap = new BitmapDrawable(getResources(), Qr.bitmap(base58));
        bitmap.setFilterBitmap(false);
        final ImageView imageView = view.findViewById(R.id.extended_public_key_dialog_image);
        imageView.setImageDrawable(bitmap);

        final DialogBuilder dialog = DialogBuilder.custom(activity, 0, view);
        dialog.setNegativeButton(R.string.button_dismiss, (d, which) -> dismissAllowingStateLoss());
        dialog.setPositiveButton(R.string.button_share, (d, which) -> {
            final ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity);
            builder.setType("text/plain");
            builder.setText(base58);
            builder.setSubject(getString(R.string.extended_public_key_fragment_title));
            builder.setChooserTitle(R.string.extended_public_key_fragment_share);
            builder.startChooser();
            log.info("extended public key shared via intent: {}", base58);
        });

        return dialog.show();
    }
}
