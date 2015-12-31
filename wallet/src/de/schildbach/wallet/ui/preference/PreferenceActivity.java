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

package de.schildbach.wallet.ui.preference;

import java.util.List;

import android.view.MenuItem;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class PreferenceActivity extends android.preference.PreferenceActivity
{
	@Override
	public void onBuildHeaders(final List<Header> target)
	{
		loadHeadersFromResource(R.xml.preference_headers, target);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected boolean isValidFragment(final String fragmentName)
	{
		return SettingsFragment.class.getName().equals(fragmentName) || DiagnosticsFragment.class.getName().equals(fragmentName)
				|| AboutFragment.class.getName().equals(fragmentName);
	}
}
