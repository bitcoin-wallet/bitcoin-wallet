/*
 * Copyright 2011-2012 the original author or authors.
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

import java.util.Date;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class BlockchainStateFragment extends Fragment
{
	private TextView messageView;
	private TextView disclaimerView;

	private int download;
	private Date bestChainDate;

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
		}

		public void onServiceDisconnected(final ComponentName name)
		{
		}
	};

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			download = intent.getIntExtra(Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD, Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
			bestChainDate = (Date) intent.getSerializableExtra(Service.ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE);

			updateView();
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Activity activity = getActivity();

		activity.bindService(new Intent(activity, Service.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.blockchain_state_fragment, container);

		messageView = (TextView) view.findViewById(R.id.wallet_message);
		disclaimerView = (TextView) view.findViewById(R.id.wallet_disclaimer);

		disclaimerView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				final Activity activity = getActivity();

				activity.showDialog(WalletActivity.DIALOG_SAFETY);
			}
		});

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		final Activity activity = getActivity();

		activity.registerReceiver(broadcastReceiver, new IntentFilter(Service.ACTION_BLOCKCHAIN_STATE));

		updateView();
	}

	@Override
	public void onPause()
	{
		final Activity activity = getActivity();

		activity.unregisterReceiver(broadcastReceiver);

		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		final Activity activity = getActivity();

		activity.unbindService(serviceConnection);

		super.onDestroy();
	}

	private void updateView()
	{
		if (download != Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK)
		{
			messageView.setVisibility(View.VISIBLE);
			disclaimerView.setVisibility(View.INVISIBLE);

			if ((download & Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM) != 0)
				messageView.setText(R.string.wallet_message_blockchain_problem_storage);
			else if ((download & Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM) != 0)
				messageView.setText(R.string.wallet_message_blockchain_problem_power);
			else if ((download & Service.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM) != 0)
				messageView.setText(R.string.wallet_message_blockchain_problem_network);
		}
		else if (bestChainDate != null)
		{
			final long spanHours = (System.currentTimeMillis() - bestChainDate.getTime()) / 1000 / 60 / 60;

			messageView.setVisibility(spanHours < 2 ? View.INVISIBLE : View.VISIBLE);
			disclaimerView.setVisibility(spanHours < 2 ? View.VISIBLE : View.INVISIBLE);

			if (spanHours < 48)
				messageView.setText(getString(R.string.wallet_message_blockchain_hours, spanHours));
			else if (spanHours < 24 * 14)
				messageView.setText(getString(R.string.wallet_message_blockchain_days, spanHours / 24));
			else
				messageView.setText(getString(R.string.wallet_message_blockchain_weeks, spanHours / 24 / 7));
		}
		else
		{
			messageView.setVisibility(View.INVISIBLE);
			disclaimerView.setVisibility(View.VISIBLE);
		}
	}
}
