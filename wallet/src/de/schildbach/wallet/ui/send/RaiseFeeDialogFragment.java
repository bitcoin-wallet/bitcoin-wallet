/*
 * Copyright 2015 the original author or authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
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
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class RaiseFeeDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = RaiseFeeDialogFragment.class.getName();
	private static final String KEY_TRANSACTION = "transaction";
	private static final Coin FEE_RAISE = FeeCategory.PRIORITY.feePerKb.multiply(2);

	public static void show(final FragmentManager fm, final Transaction tx)
	{
		final DialogFragment newFragment = instance(tx);
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private static RaiseFeeDialogFragment instance(final Transaction tx)
	{
		final RaiseFeeDialogFragment fragment = new RaiseFeeDialogFragment();

		final Bundle args = new Bundle();
		args.putSerializable(KEY_TRANSACTION, tx.getHash().getBytes());
		fragment.setArguments(args);

		return fragment;
	}

	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Configuration config;
	private Wallet wallet;

	private Transaction transaction;

	@Nullable
	private AlertDialog dialog;

	private View passwordGroup;
	private EditText passwordView;
	private View badPasswordView;
	private Button positiveButton, negativeButton;

	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private enum State
	{
		INPUT, DECRYPTING, DONE
	}

	private State state = State.INPUT;

	private static final Logger log = LoggerFactory.getLogger(RaiseFeeDialogFragment.class);

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.config = application.getConfiguration();
		this.wallet = application.getWallet();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Bundle args = getArguments();
		final byte[] txHash = (byte[]) args.getSerializable(KEY_TRANSACTION);
		transaction = checkNotNull(wallet.getTransaction(Sha256Hash.wrap(txHash)));

		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final View view = LayoutInflater.from(activity).inflate(R.layout.raise_fee_dialog, null);

		final TextView messageView = (TextView) view.findViewById(R.id.raise_fee_dialog_message);
		messageView.setText(getString(R.string.raise_fee_dialog_message, config.getFormat().format(FEE_RAISE)));

		passwordGroup = view.findViewById(R.id.raise_fee_dialog_password_group);

		passwordView = (EditText) view.findViewById(R.id.raise_fee_dialog_password);
		passwordView.setText(null);

		badPasswordView = view.findViewById(R.id.raise_fee_dialog_bad_password);

		final DialogBuilder builder = new DialogBuilder(activity);
		builder.setTitle(R.string.raise_fee_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.raise_fee_dialog_button_raise, null); // dummy, just to make it show
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
						handleGo();
					}
				});
				negativeButton.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(final View v)
					{
						dismiss();
					}
				});

				passwordView.addTextChangedListener(textWatcher);

				RaiseFeeDialogFragment.this.dialog = dialog;
				updateView();
			}
		});

		log.info("showing raise fee dialog");

		return dialog;
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
				protected void onSuccess(final KeyParameter encryptionKey)
				{
					doRaiseFee(encryptionKey);
				}
			}.deriveKey(wallet.getKeyCrypter(), passwordView.getText().toString().trim());

			updateView();
		}
		else
		{
			doRaiseFee(null);
		}
	}

	private void doRaiseFee(final KeyParameter encryptionKey)
	{
		// construct child-pays-for-parent
		final TransactionOutput outputToSpend = checkNotNull(findSpendableOutput(wallet, transaction));
		final Transaction transactionToSend = new Transaction(Constants.NETWORK_PARAMETERS);
		transactionToSend.addInput(outputToSpend);
		transactionToSend.addOutput(outputToSpend.getValue().subtract(FEE_RAISE), wallet.freshAddress(KeyPurpose.CHANGE));
		transactionToSend.setPurpose(Transaction.Purpose.RAISE_FEE);

		final SendRequest sendRequest = SendRequest.forTx(transactionToSend);
		sendRequest.aesKey = encryptionKey;

		try
		{
			wallet.signTransaction(sendRequest);

			log.info("raise fee: cpfp {}", transactionToSend);

			wallet.commitTx(transactionToSend);
			application.broadcastTransaction(transactionToSend);

			state = State.DONE;
			updateView();

			dismiss();
		}
		catch (final KeyCrypterException x)
		{
			badPasswordView.setVisibility(View.VISIBLE);

			state = State.INPUT;
			updateView();

			passwordView.requestFocus();

			log.info("raise fee: bad spending password");
		}
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
			positiveButton.setText(R.string.raise_fee_dialog_button_raise);
			positiveButton.setEnabled(!needsPassword || passwordView.getText().toString().trim().length() > 0);
			negativeButton.setEnabled(true);
		}
		else if (state == State.DECRYPTING)
		{
			positiveButton.setText(R.string.raise_fee_dialog_state_decrypting);
			positiveButton.setEnabled(false);
			negativeButton.setEnabled(false);
		}
		else if (state == State.DONE)
		{
			positiveButton.setText(R.string.raise_fee_dialog_state_done);
			positiveButton.setEnabled(false);
			negativeButton.setEnabled(false);
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

	public static boolean feeCanBeRaised(final Wallet wallet, final Transaction transaction)
	{
		if (transaction.getConfidence().getDepthInBlocks() > 0)
			return false;

		if (WalletUtils.isPayToManyTransaction(transaction))
			return false;

		if (findSpendableOutput(wallet, transaction) == null)
			return false;

		return true;
	}

	private static @Nullable TransactionOutput findSpendableOutput(final Wallet wallet, final Transaction transaction)
	{
		for (final TransactionOutput output : transaction.getOutputs())
		{
			if (output.isMine(wallet) && output.isAvailableForSpending() && output.getValue().isGreaterThan(FEE_RAISE))
				return output;
		}

		return null;
	}
}
