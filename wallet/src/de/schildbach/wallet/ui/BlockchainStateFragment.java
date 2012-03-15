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

package de.schildbach.wallet.ui;

import java.util.Date;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class BlockchainStateFragment extends Fragment
{
	private TextView messageView;
	private TextView disclaimerView;

	private int download;
	private Date bestChainDate;

	private final Handler delayMessageHandler = new Handler();

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
			download = intent.getIntExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD, BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
			bestChainDate = (Date) intent.getSerializableExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE);

			updateView();
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Activity activity = getActivity();

		activity.bindService(new Intent(activity, BlockchainService.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.blockchain_state_fragment, container);

		messageView = (TextView) view.findViewById(R.id.blockchain_state_message);
		disclaimerView = (TextView) view.findViewById(R.id.blockchain_state_disclaimer);

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

		activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));

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

		delayMessageHandler.removeCallbacksAndMessages(null);

		activity.unbindService(serviceConnection);

		super.onDestroy();
	}

	private void updateView()
	{
		if (download != BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK)
		{
			messageView.setVisibility(View.VISIBLE);
			disclaimerView.setVisibility(View.INVISIBLE);

			if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM) != 0)
				messageView.setText(R.string.blockchain_state_message_problem_storage);
			else if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM) != 0)
				messageView.setText(R.string.blockchain_state_message_problem_power);
			else if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM) != 0)
				messageView.setText(R.string.blockchain_state_message_problem_network);
		}
		else if (bestChainDate != null)
		{
			final long blockchainLagHours = (System.currentTimeMillis() - bestChainDate.getTime()) / 1000 / 60 / 60;
			final boolean blockchainUptodate = blockchainLagHours < Constants.BLOCKCHAIN_UPTODATE_THRESHOLD_HOURS;

			messageView.setVisibility(blockchainUptodate ? View.INVISIBLE : View.VISIBLE);
			disclaimerView.setVisibility(blockchainUptodate ? View.VISIBLE : View.INVISIBLE);

			final String downloading = getString(R.string.blockchain_state_message_downloading);
			final String stalled = getString(R.string.blockchain_state_message_stalled);

			final String stalledText;
			if (blockchainLagHours < 48)
			{
				messageView.setText(getString(R.string.blockchain_state_message_hours, downloading, blockchainLagHours));
				stalledText = getString(R.string.blockchain_state_message_hours, stalled, blockchainLagHours);
			}
			else if (blockchainLagHours < 24 * 14)
			{
				messageView.setText(getString(R.string.blockchain_state_message_days, downloading, blockchainLagHours / 24));
				stalledText = getString(R.string.blockchain_state_message_days, stalled, blockchainLagHours / 24);
			}
			else
			{
				messageView.setText(getString(R.string.blockchain_state_message_weeks, downloading, blockchainLagHours / 24 / 7));
				stalledText = getString(R.string.blockchain_state_message_weeks, stalled, blockchainLagHours / 24 / 7);
			}

			delayMessageHandler.removeCallbacksAndMessages(null);
			delayMessageHandler.postDelayed(new Runnable()
			{
				public void run()
				{
					messageView.setText(stalledText);
				}
			}, Constants.BLOCKCHAIN_DOWNLOAD_THRESHOLD_MS);
		}
		else
		{
			messageView.setVisibility(View.INVISIBLE);
			disclaimerView.setVisibility(View.VISIBLE);
		}
	}
}
