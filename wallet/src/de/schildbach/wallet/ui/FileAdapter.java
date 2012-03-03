/*
 * Copyright 2012-2015 the original author or authors.
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
import java.util.List;

import de.schildbach.wallet.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public abstract class FileAdapter extends ArrayAdapter<File> {
    protected final Context context;
    protected final LayoutInflater inflater;

    public FileAdapter(final Context context) {
        super(context, 0);

        this.context = context;
        inflater = LayoutInflater.from(context);
    }

    public void setFiles(final List<File> files) {
        clear();
        for (final File file : files)
            add(file);
    }

    @Override
    public View getView(final int position, View row, final ViewGroup parent) {
        final File file = getItem(position);

        if (row == null)
            row = inflater.inflate(R.layout.spinner_item, null);

        final TextView textView = (TextView) row.findViewById(android.R.id.text1);
        textView.setText(file.getName());

        return row;
    }
}
