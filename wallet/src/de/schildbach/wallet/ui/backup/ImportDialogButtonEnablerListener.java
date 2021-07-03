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

package de.schildbach.wallet.ui.backup;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public class ImportDialogButtonEnablerListener implements TextWatcher, OnItemSelectedListener {
    private final TextView passwordView;
    private final AlertDialog dialog;

    public ImportDialogButtonEnablerListener(final TextView passwordView, final AlertDialog dialog) {
        this.passwordView = passwordView;
        this.dialog = dialog;

        handle();
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
        handle();
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
        handle();
    }

    @Override
    public void afterTextChanged(final Editable s) {
        handle();
    }

    @Override
    public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
    }

    @Override
    public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
    }

    public void handle() {
        final boolean needsPassword = needsPassword();
        final boolean hasPassword = !passwordView.getText().toString().trim().isEmpty();
        final boolean hasFile = hasFile();

        final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        button.setEnabled(hasFile && (!needsPassword || hasPassword));
    }

    protected boolean hasFile() {
        return true;
    }

    protected boolean needsPassword() {
        return true;
    }
}
