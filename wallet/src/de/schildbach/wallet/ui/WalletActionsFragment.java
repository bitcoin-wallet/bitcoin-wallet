/*
 * Copyright 2013-2015 the original author or authors.
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
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletActionsFragment extends Fragment
{
	private WalletActivity activity;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (WalletActivity) activity;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_actions_fragment, container);

		final View requestButton = view.findViewById(R.id.wallet_actions_request);
		requestButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				activity.handleRequestCoins();
			}
		});

		final View sendButton = view.findViewById(R.id.wallet_actions_send);
		sendButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				activity.handleSendCoins();
			}
		});

		final View sendQrButton = view.findViewById(R.id.wallet_actions_send_qr);
		sendQrButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				activity.handleScan();
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

	private void updateView()
	{
		final boolean showActions = !getResources().getBoolean(R.bool.wallet_actions_top);

		final View view = getView();
		final ViewParent parent = view.getParent();
		final View fragment = parent instanceof FrameLayout ? (FrameLayout) parent : view;
		fragment.setVisibility(showActions ? View.VISIBLE : View.GONE);
	}
}
