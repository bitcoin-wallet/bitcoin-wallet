/*
 * Copyright 2013 the original author or authors.
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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletActionsFragment extends Fragment implements OnSharedPreferenceChangeListener
{
	private Activity activity;
	private SharedPreferences prefs;

	private View actionsView;
	private TextView disclaimerView;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_actions_fragment, container);

		actionsView = view.findViewById(R.id.wallet_actions);

		disclaimerView = (TextView) view.findViewById(R.id.wallet_actions_disclaimer);
		disclaimerView.setText(Html.fromHtml(getString(R.string.wallet_disclaimer)));
		disclaimerView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				HelpDialogFragment.page(getFragmentManager(), "safety");
			}
		});

		final Button requestButton = (Button) view.findViewById(R.id.wallet_actions_request);
		requestButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(new Intent(activity, RequestCoinsActivity.class));
			}
		});

		final Button sendButton = (Button) view.findViewById(R.id.wallet_actions_send);
		sendButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				startActivity(new Intent(activity, SendCoinsActivity.class));
			}
		});

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
	}

	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	{
		if (Constants.PREFS_KEY_DISCLAIMER.equals(key))
			updateView();
	}

	private void updateView()
	{
		final boolean showDisclaimer = prefs.getBoolean(Constants.PREFS_KEY_DISCLAIMER, true);
		final boolean showActions = !getResources().getBoolean(R.bool.wallet_actions_top);

		actionsView.setVisibility(showActions ? View.VISIBLE : View.GONE);
		disclaimerView.setVisibility(showDisclaimer ? View.VISIBLE : View.GONE);

		getView().setVisibility(showDisclaimer || showActions ? View.VISIBLE : View.GONE);
	}
}
