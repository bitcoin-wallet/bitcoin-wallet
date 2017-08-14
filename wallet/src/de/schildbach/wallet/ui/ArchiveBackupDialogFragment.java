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

package de.schildbach.wallet.ui;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WholeStringBuilder;
import de.schildbach.wallet_test.R;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.text.Html;

/**
 * @author Andreas Schildbach
 */
public class ArchiveBackupDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = ArchiveBackupDialogFragment.class.getName();

    private static final String KEY_FILE = "file";

    public static void show(final FragmentManager fm, final File backupFile) {
        final DialogFragment newFragment = instance(backupFile);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private static ArchiveBackupDialogFragment instance(final File backupFile) {
        final ArchiveBackupDialogFragment fragment = new ArchiveBackupDialogFragment();

        final Bundle args = new Bundle();
        args.putSerializable(KEY_FILE, backupFile);
        fragment.setArguments(args);

        return fragment;
    }

    private AbstractWalletActivity activity;

    private static final Logger log = LoggerFactory.getLogger(ArchiveBackupDialogFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractWalletActivity) activity;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final File backupFile = (File) args.getSerializable(KEY_FILE);

        final String path;
        final String backupPath = backupFile.getAbsolutePath();
        final String storagePath = Constants.Files.EXTERNAL_STORAGE_DIR.getAbsolutePath();
        if (backupPath.startsWith(storagePath))
            path = backupPath.substring(storagePath.length());
        else
            path = backupPath;

        final DialogBuilder dialog = new DialogBuilder(activity);
        dialog.setMessage(Html.fromHtml(getString(R.string.export_keys_dialog_success, path)));
        dialog.setPositiveButton(WholeStringBuilder.bold(getString(R.string.export_keys_dialog_button_archive)),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        archiveWalletBackup(backupFile);
                    }
                });
        dialog.setNegativeButton(R.string.button_dismiss, null);

        return dialog.create();
    }

    private void archiveWalletBackup(final File backupFile) {
        final ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity);
        builder.setSubject(getString(R.string.export_keys_dialog_mail_subject)
                + Constants.Files.EXTERNAL_WALLET_BACKUP_SUBJECT_SUFFIX);
        builder.setText(getString(R.string.export_keys_dialog_mail_text) + "\n\n"
                + String.format(Constants.WEBMARKET_APP_URL, activity.getPackageName()) + "\n\n" + Constants.SOURCE_URL
                + '\n');
        builder.setType(Constants.MIMETYPE_WALLET_BACKUP);
        builder.setStream(
                FileProvider.getUriForFile(activity, activity.getPackageName() + ".file_attachment", backupFile));
        builder.setChooserTitle(R.string.export_keys_dialog_mail_intent_chooser);
        builder.startChooser();
        log.info("invoked chooser for archiving wallet backup");
    }
}
