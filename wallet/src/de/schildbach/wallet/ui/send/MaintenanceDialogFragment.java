/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.ui.send;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DeterministicUpgradeRequiresPassword;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class MaintenanceDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = MaintenanceDialogFragment.class.getName();

	public static void show(final FragmentManager fm)
	{
		Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);
		if (fragment == null)
		{
			fragment = new MaintenanceDialogFragment();
			fm.beginTransaction().add(fragment, FRAGMENT_TAG).commit();
		}
	}

	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Wallet wallet;

	@Nullable
	private AlertDialog dialog;

	private View passwordGroup;
	private EditText passwordView;
	private View badPasswordView;
	private Button positiveButton, negativeButton;

	private Handler handler = new Handler();
	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private enum State
	{
		INPUT, DECRYPTING, DONE
	}

	private State state = State.INPUT;

	private static final Logger log = LoggerFactory.getLogger(MaintenanceDialogFragment.class);

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final View view = LayoutInflater.from(activity).inflate(R.layout.maintenance_dialog, null);

		Coin value = Coin.ZERO;
		Coin fee = Coin.ZERO;
		for (final Transaction tx : determineMaintenanceTransactions())
		{
			value = value.add(tx.getValueSentFromMe(wallet));
			fee = fee.add(tx.getFee());
		}
		final TextView messageView = (TextView) view.findViewById(R.id.maintenance_dialog_message);
		final MonetaryFormat format = application.getConfiguration().getFormat();
		messageView.setText(getString(R.string.maintenance_dialog_message, format.format(value), format.format(fee)));

		passwordGroup = view.findViewById(R.id.maintenance_dialog_password_group);

		passwordView = (EditText) view.findViewById(R.id.maintenance_dialog_password);
		passwordView.setText(null);

		badPasswordView = view.findViewById(R.id.maintenance_dialog_bad_password);

		final DialogBuilder builder = new DialogBuilder(activity);
		builder.setTitle(R.string.maintenance_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.maintenance_dialog_button_move, null); // dummy, just to make it show
		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.setCancelable(false);

		final AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);

		dialog.setOnShowListener(new OnShowListener()
		{
			@Override
			public void onShow(final DialogInterface d)
			{
				positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
				negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);

				positiveButton.setTypeface(Typeface.DEFAULT_BOLD);
				positiveButton.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(final View v)
					{
						log.info("user decided to do maintenance");
						handleGo();
					}
				});
				negativeButton.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(final View v)
					{
						log.info("user decided to dismiss");
						dismiss();
					}
				});

				passwordView.addTextChangedListener(textWatcher);

				MaintenanceDialogFragment.this.dialog = dialog;
				updateView();
			}
		});

		log.info("showing maintenance dialog");

		return dialog;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onDismiss(final DialogInterface dialog)
	{
		this.dialog = null;

		wipePasswords();

		super.onDismiss(dialog);
	}

	@Override
	public void onDestroy()
	{
		backgroundThread.getLooper().quit();

		super.onDestroy();
	}

	private void handleGo()
	{
		state = State.DECRYPTING;
		updateView();

		if (wallet.isEncrypted())
		{
			new DeriveKeyTask(backgroundHandler)
			{
				@Override
				protected void onSuccess(KeyParameter encryptionKey)
				{
					doMaintenance(encryptionKey);
				}
			}.deriveKey(wallet.getKeyCrypter(), passwordView.getText().toString().trim());

			updateView();
		}
		else
		{
			doMaintenance(null);
		}
	}

	private void doMaintenance(final KeyParameter encryptionKey)
	{
		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

				try
				{
					wallet.doMaintenance(encryptionKey, true);

					handler.post(new Runnable()
					{
						@Override
						public void run()
						{
							state = State.DONE;
							updateView();

							delayedDismiss();
						}
					});
				}
				catch (final KeyCrypterException x)
				{
					handler.post(new Runnable()
					{
						@Override
						public void run()
						{
							badPasswordView.setVisibility(View.VISIBLE);

							state = State.INPUT;
							updateView();

							passwordView.requestFocus();

							log.info("bad spending password");
						}
					});
				}
			}
		});
	}

	private void delayedDismiss()
	{
		handler.postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				dismiss();
			}
		}, 2000);
	}

	private void wipePasswords()
	{
		passwordView.setText(null);
	}

	private void updateView()
	{
		if (dialog == null)
			return;

		final boolean needsPassword = wallet.isEncrypted();
		passwordGroup.setVisibility(needsPassword ? View.VISIBLE : View.GONE);

		if (state == State.INPUT)
		{
			positiveButton.setText(R.string.maintenance_dialog_button_move);
			positiveButton.setEnabled(!needsPassword || passwordView.getText().toString().trim().length() > 0);
			negativeButton.setEnabled(true);
		}
		else if (state == State.DECRYPTING)
		{
			positiveButton.setText(R.string.maintenance_dialog_state_decrypting);
			positiveButton.setEnabled(false);
			negativeButton.setEnabled(false);
		}
		else if (state == State.DONE)
		{
			positiveButton.setText(R.string.maintenance_dialog_state_done);
			positiveButton.setEnabled(false);
			negativeButton.setEnabled(false);
		}
	}

	private List<Transaction> determineMaintenanceTransactions()
	{
		try
		{
			final ListenableFuture<List<Transaction>> result = wallet.doMaintenance(null, false);
			return result.get();
		}
		catch (final DeterministicUpgradeRequiresPassword x)
		{
			return Collections.emptyList();
		}
		catch (final Exception x)
		{
			throw new RuntimeException(x);
		}
	}

	private final TextWatcher textWatcher = new TextWatcher()
	{
		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
			badPasswordView.setVisibility(View.INVISIBLE);
			updateView();
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		@Override
		public void afterTextChanged(final Editable s)
		{
		}
	};
}
