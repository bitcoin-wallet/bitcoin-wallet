/*
 * Copyright 2011-2013 the original author or authors.
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
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Html;
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
	private Activity activity;

	private TextView disclaimerView;
	private TextView progressView;
	private View replayingView;

	private int download;
	private Date bestChainDate;
	private boolean replaying;

	private final Handler delayMessageHandler = new Handler();

	private final class BlockchainBroadcastReceiver extends BroadcastReceiver
	{
		public AtomicBoolean active = new AtomicBoolean(false);

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			download = intent.getIntExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD, BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
			bestChainDate = (Date) intent.getSerializableExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE);
			replaying = intent.getBooleanExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_REPLAYING, false);

			if (active.get())
				updateView();
		}
	}

	private final BlockchainBroadcastReceiver broadcastReceiver = new BlockchainBroadcastReceiver();

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.blockchain_state_fragment, container);

		disclaimerView = (TextView) view.findViewById(R.id.blockchain_state_disclaimer);
		disclaimerView.setText(Html.fromHtml(getString(R.string.blockchain_state_disclaimer)));
		disclaimerView.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				activity.showDialog(WalletActivity.DIALOG_SAFETY);
			}
		});

		progressView = (TextView) view.findViewById(R.id.blockchain_state_progress);

		replayingView = view.findViewById(R.id.blockchain_state_replaying);

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
		broadcastReceiver.active.set(true);

		updateView();
	}

	@Override
	public void onPause()
	{
		broadcastReceiver.active.set(false);
		activity.unregisterReceiver(broadcastReceiver);

		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		delayMessageHandler.removeCallbacksAndMessages(null);

		super.onDestroy();
	}

	private void updateView()
	{
		if (download != BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK)
		{
			progressView.setVisibility(View.VISIBLE);
			disclaimerView.setVisibility(View.GONE);

			if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM) != 0)
				progressView.setText(R.string.blockchain_state_progress_problem_storage);
			else if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_POWER_PROBLEM) != 0)
				progressView.setText(R.string.blockchain_state_progress_problem_power);
			else if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM) != 0)
				progressView.setText(R.string.blockchain_state_progress_problem_network);
		}
		else if (bestChainDate != null)
		{
			final long blockchainLagHours = (System.currentTimeMillis() - bestChainDate.getTime()) / 1000 / 60 / 60;
			final boolean blockchainUptodate = blockchainLagHours < Constants.BLOCKCHAIN_UPTODATE_THRESHOLD_HOURS;

			progressView.setVisibility(blockchainUptodate ? View.GONE : View.VISIBLE);
			disclaimerView.setVisibility(blockchainUptodate ? View.VISIBLE : View.GONE);

			final String downloading = getString(R.string.blockchain_state_progress_downloading);
			final String stalled = getString(R.string.blockchain_state_progress_stalled);

			final String stalledText;
			if (blockchainLagHours < 48)
			{
				progressView.setText(getString(R.string.blockchain_state_progress_hours, downloading, blockchainLagHours));
				stalledText = getString(R.string.blockchain_state_progress_hours, stalled, blockchainLagHours);
			}
			else if (blockchainLagHours < 24 * 14)
			{
				progressView.setText(getString(R.string.blockchain_state_progress_days, downloading, blockchainLagHours / 24));
				stalledText = getString(R.string.blockchain_state_progress_days, stalled, blockchainLagHours / 24);
			}
			else
			{
				progressView.setText(getString(R.string.blockchain_state_progress_weeks, downloading, blockchainLagHours / 24 / 7));
				stalledText = getString(R.string.blockchain_state_progress_weeks, stalled, blockchainLagHours / 24 / 7);
			}

			delayMessageHandler.removeCallbacksAndMessages(null);
			delayMessageHandler.postDelayed(new Runnable()
			{
				public void run()
				{
					progressView.setText(stalledText);
				}
			}, Constants.BLOCKCHAIN_DOWNLOAD_THRESHOLD_MS);
		}
		else
		{
			progressView.setVisibility(View.GONE);
			disclaimerView.setVisibility(View.VISIBLE);
		}

		replayingView.setVisibility(replaying ? View.VISIBLE : View.GONE);
	}
}
