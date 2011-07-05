/*
 * Copyright 2010 the original author or authors.
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

package de.schildbach.wallet;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public class ActionBarFragment extends Fragment
{
	private ImageView iconView;
	private TextView primaryTitleView;
	private TextView secondaryTitleView;
	private ImageButton button;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.action_bar_fragment, container);

		iconView = (ImageView) view.findViewById(R.id.action_bar_icon);
		primaryTitleView = (TextView) view.findViewById(R.id.action_bar_primary_title);
		secondaryTitleView = (TextView) view.findViewById(R.id.action_bar_secondary_title);
		button = (ImageButton) view.findViewById(R.id.action_bar_button);

		return view;
	}

	public void setIcon(final int iconRes)
	{
		iconView.setImageResource(iconRes);
	}

	public void setPrimaryTitle(final int titleRes)
	{
		primaryTitleView.setText(titleRes);
	}

	public void setSecondaryTitle(final CharSequence title)
	{
		secondaryTitleView.setText(title);
		secondaryTitleView.setVisibility(title != null ? View.VISIBLE : View.GONE);
	}

	public ImageButton getButton()
	{
		return button;
	}
}
