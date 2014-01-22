/*
 * Copyright 2013-2014 the original author or authors.
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class WalletDisclaimerFragment extends Fragment implements OnSharedPreferenceChangeListener
{
	private Activity activity;
	private SharedPreferences prefs;

	private int download;

	private TextView messageView;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (WalletActivity) activity;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		messageView = (TextView) inflater.inflate(R.layout.wallet_disclaimer_fragment, container);

		messageView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				final boolean showBackup = prefs.getBoolean(Constants.PREFS_KEY_REMIND_BACKUP, true);
				if (showBackup)
					((WalletActivity) activity).handleExportKeys();
				else
					HelpDialogFragment.page(getFragmentManager(), R.string.help_safety);
			}
		});

		return messageView;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		prefs.registerOnSharedPreferenceChangeListener(this);

		activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));

		updateView();
	}

	@Override
	public void onPause()
	{
		activity.unregisterReceiver(broadcastReceiver);

		prefs.unregisterOnSharedPreferenceChangeListener(this);

		super.onPause();
	}

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	{
		if (Constants.PREFS_KEY_DISCLAIMER.equals(key) || Constants.PREFS_KEY_REMIND_BACKUP.equals(key))
			updateView();
	}

	private void updateView()
	{
		if (!isResumed())
			return;

		final boolean showBackup = prefs.getBoolean(Constants.PREFS_KEY_REMIND_BACKUP, true);
		final boolean showSafety = prefs.getBoolean(Constants.PREFS_KEY_DISCLAIMER, true);

		final int progressResId;
		if (download == BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK)
			progressResId = 0;
		else if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM) != 0)
			progressResId = R.string.blockchain_state_progress_problem_storage;
		else if ((download & BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM) != 0)
			progressResId = R.string.blockchain_state_progress_problem_network;
		else
			throw new IllegalStateException("download=" + download);

		final SpannableStringBuilder text = new SpannableStringBuilder();
		if (progressResId != 0)
			text.append(Html.fromHtml("<b>" + getString(progressResId) + "</b>"));
		if (progressResId != 0 && (showBackup || showSafety))
			text.append('\n');
		if (showBackup)
			text.append(Html.fromHtml(getString(R.string.wallet_disclaimer_fragment_remind_backup)));
		if (showBackup && showSafety)
			text.append('\n');
		if (showSafety)
			text.append(Html.fromHtml(getString(R.string.wallet_disclaimer_fragment_remind_safety)));
		messageView.setText(text);

		final View view = getView();
		final ViewParent parent = view.getParent();
		final View fragment = parent instanceof FrameLayout ? (FrameLayout) parent : view;
		fragment.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
	}

	private final BlockchainBroadcastReceiver broadcastReceiver = new BlockchainBroadcastReceiver();

	private final class BlockchainBroadcastReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			download = intent.getIntExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD, BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);

			updateView();
		}
	}
}
