/*
 * Copyright 2012-2014 the original author or authors.
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.support.v4.content.CursorLoader;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class ExchangeRateLoader extends CursorLoader implements OnSharedPreferenceChangeListener
{
	private final SharedPreferences prefs;

	public ExchangeRateLoader(final Context context)
	{
		super(context, ExchangeRatesProvider.contentUri(context.getPackageName()), null, ExchangeRatesProvider.KEY_CURRENCY_CODE,
				new String[] { null }, null);

		prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	@Override
	protected void onStartLoading()
	{
		super.onStartLoading();

		prefs.registerOnSharedPreferenceChangeListener(this);

		onCurrencyChange();
	}

	@Override
	protected void onStopLoading()
	{
		prefs.unregisterOnSharedPreferenceChangeListener(this);

		super.onStopLoading();
	}

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	{
		if (Constants.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
			onCurrencyChange();
	}

	private void onCurrencyChange()
	{
		final String exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, null);

		setSelectionArgs(new String[] { exchangeCurrency });

		forceLoad();
	}
}
