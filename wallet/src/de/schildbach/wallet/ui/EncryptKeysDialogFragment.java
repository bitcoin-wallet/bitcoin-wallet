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

package de.schildbach.wallet.ui;

import java.security.SecureRandom;

import javax.annotation.Nullable;

import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.google.protobuf.ByteString;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public class EncryptKeysDialogFragment extends DialogFragment
{
	private static final int SCRYPT_ITERATIONS = 4096;

	private static final String FRAGMENT_TAG = EncryptKeysDialogFragment.class.getName();

	public static void show(final FragmentManager fm)
	{
		final DialogFragment newFragment = new EncryptKeysDialogFragment();
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private AbstractWalletActivity activity;
	private WalletApplication application;
	private Wallet wallet;

	@Nullable
	private AlertDialog dialog;

	private View oldPasswordGroup;
	private EditText oldPasswordView;
	private EditText newPasswordView;
	private View badPasswordView;
	private TextView passwordStrengthView;
	private CheckBox showView;
	private Button positiveButton, negativeButton;

	private final Handler handler = new Handler();
	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private enum State
	{
		INPUT, CRYPTING, DONE
	}

	private State state = State.INPUT;

	private static final Logger log = LoggerFactory.getLogger(EncryptKeysDialogFragment.class);

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
		final View view = LayoutInflater.from(activity).inflate(R.layout.encrypt_keys_dialog, null);

		oldPasswordGroup = view.findViewById(R.id.encrypt_keys_dialog_password_old_group);

		oldPasswordView = (EditText) view.findViewById(R.id.encrypt_keys_dialog_password_old);
		oldPasswordView.setText(null);

		newPasswordView = (EditText) view.findViewById(R.id.encrypt_keys_dialog_password_new);
		newPasswordView.setText(null);

		badPasswordView = view.findViewById(R.id.encrypt_keys_dialog_bad_password);

		passwordStrengthView = (TextView) view.findViewById(R.id.encrypt_keys_dialog_password_strength);

		showView = (CheckBox) view.findViewById(R.id.encrypt_keys_dialog_show);

		final DialogBuilder builder = new DialogBuilder(activity);
		builder.setTitle(R.string.encrypt_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.button_ok, null); // dummy, just to make it show
		builder.setNegativeButton(R.string.button_cancel, null);
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

				oldPasswordView.addTextChangedListener(textWatcher);
				newPasswordView.addTextChangedListener(textWatcher);

				showView = (CheckBox) dialog.findViewById(R.id.encrypt_keys_dialog_show);
				showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(newPasswordView, oldPasswordView));
				showView.setChecked(true);

				EncryptKeysDialogFragment.this.dialog = dialog;
				updateView();
			}
		});

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

		oldPasswordView.removeTextChangedListener(textWatcher);
		newPasswordView.removeTextChangedListener(textWatcher);

		showView.setOnCheckedChangeListener(null);

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
		final boolean isEncrypted = wallet.isEncrypted();
		final String oldPassword = oldPasswordView.getText().toString().trim();
		final String password = newPasswordView.getText().toString().trim();

		state = State.CRYPTING;
		updateView();

		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				final byte[] salt = new byte[KeyCrypterScrypt.SALT_LENGTH];
				new SecureRandom().nextBytes(salt);
				final KeyCrypter keyCrypter = new KeyCrypterScrypt(Protos.ScryptParameters.newBuilder().setSalt(ByteString.copyFrom(salt))
						.setN(SCRYPT_ITERATIONS).build());

				final KeyParameter oldKey = isEncrypted ? wallet.getKeyCrypter().deriveKey(oldPassword) : null;

				final KeyParameter newKey = password.isEmpty() ? null : keyCrypter.deriveKey(password);

				handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							if (oldKey != null)
								wallet.decrypt(oldKey);

							if (newKey != null)
								wallet.encrypt(keyCrypter, newKey);

							application.backupWallet();

							state = State.DONE;
							updateView();

							log.info("spending password set or changed");

							delayedDismiss();
						}
						catch (final KeyCrypterException x)
						{
							badPasswordView.setVisibility(View.VISIBLE);

							state = State.INPUT;
							updateView();

							oldPasswordView.requestFocus();

							log.info("remove or change of spending password failed");
						}
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
				});
			}
		});
	}

	private void wipePasswords()
	{
		oldPasswordView.setText(null);
		newPasswordView.setText(null);
	}

	private void updateView()
	{
		if (dialog == null)
			return;

		final boolean hasOldPassword = !oldPasswordView.getText().toString().trim().isEmpty();
		final boolean hasPassword = !newPasswordView.getText().toString().trim().isEmpty();

		oldPasswordGroup.setVisibility(wallet.isEncrypted() ? View.VISIBLE : View.GONE);
		oldPasswordView.setEnabled(state == State.INPUT);

		newPasswordView.setEnabled(state == State.INPUT);

		final int passwordLength = newPasswordView.getText().length();
		passwordStrengthView.setVisibility(state == State.INPUT && passwordLength > 0 ? View.VISIBLE : View.INVISIBLE);
		if (passwordLength < 4)
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_weak);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_weak));
		}
		else if (passwordLength < 6)
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_fair);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_fair));
		}
		else if (passwordLength < 8)
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_good);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_less_significant));
		}
		else
		{
			passwordStrengthView.setText(R.string.encrypt_keys_dialog_password_strength_strong);
			passwordStrengthView.setTextColor(getResources().getColor(R.color.fg_password_strength_strong));
		}

		showView.setEnabled(state == State.INPUT);

		if (state == State.INPUT)
		{
			if (wallet.isEncrypted())
			{
				positiveButton.setText(hasPassword ? R.string.button_edit : R.string.button_remove);
				positiveButton.setEnabled(hasOldPassword);
			}
			else
			{
				positiveButton.setText(R.string.button_set);
				positiveButton.setEnabled(hasPassword);
			}

			negativeButton.setEnabled(true);
		}
		else if (state == State.CRYPTING)
		{
			positiveButton.setText(newPasswordView.getText().toString().trim().isEmpty() ? R.string.encrypt_keys_dialog_state_decrypting
					: R.string.encrypt_keys_dialog_state_encrypting);
			positiveButton.setEnabled(false);
			negativeButton.setEnabled(false);
		}
		else if (state == State.DONE)
		{
			positiveButton.setText(R.string.encrypt_keys_dialog_state_done);
			positiveButton.setEnabled(false);
			negativeButton.setEnabled(false);
		}
	}
}
