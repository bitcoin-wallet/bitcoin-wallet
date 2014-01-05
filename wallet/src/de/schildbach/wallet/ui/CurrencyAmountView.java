/*
 * Copyright 2011-2014 the original author or authors.
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

import java.math.BigInteger;
import java.util.Currency;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.bitcoin.core.Transaction;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class CurrencyAmountView extends FrameLayout
{
	public static interface Listener
	{
		void changed();

		void done();

		void focusChanged(final boolean hasFocus);
	}

	private int significantColor, lessSignificantColor, errorColor;
	private Drawable deleteButtonDrawable, contextButtonDrawable;
	private Drawable currencySymbolDrawable;
	private int inputPrecision = 0;
	private int hintPrecision = 0;
	private int shift = 0;
	private boolean amountSigned = false;
	private boolean smallerInsignificant = true;
	private boolean validateAmount = true;

	private TextView textView;
	private View contextButton;
	private Listener listener;
	private OnClickListener contextButtonClickListener;

	public CurrencyAmountView(final Context context)
	{
		super(context);
		init(context);
	}

	public CurrencyAmountView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		init(context);
	}

	private void init(final Context context)
	{
		final Resources resources = context.getResources();
		significantColor = resources.getColor(R.color.fg_significant);
		lessSignificantColor = resources.getColor(R.color.fg_less_significant);
		errorColor = resources.getColor(R.color.fg_error);
		deleteButtonDrawable = resources.getDrawable(R.drawable.ic_input_delete);
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		final Context context = getContext();

		textView = (TextView) getChildAt(0);
		textView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		textView.setHintTextColor(lessSignificantColor);
		textView.setHorizontalFadingEdgeEnabled(true);
		textView.setSingleLine();
		setHint(null);
		setValidateAmount(textView instanceof EditText);
		textView.addTextChangedListener(textViewListener);
		textView.setOnFocusChangeListener(textViewListener);
		textView.setOnEditorActionListener(textViewListener);

		contextButton = new View(context)
		{
			@Override
			protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec)
			{
				setMeasuredDimension(textView.getCompoundPaddingRight(), textView.getMeasuredHeight());
			}
		};
		final LayoutParams chooseViewParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		chooseViewParams.gravity = Gravity.RIGHT;
		contextButton.setLayoutParams(chooseViewParams);
		this.addView(contextButton);

		updateAppearance();
	}

	public void setCurrencySymbol(@Nullable final String currencyCode)
	{
		if (Constants.CURRENCY_CODE_BTC.equals(currencyCode))
		{
			currencySymbolDrawable = getResources().getDrawable(R.drawable.currency_symbol_btc);
		}
		else if (Constants.CURRENCY_CODE_MBTC.equals(currencyCode))
		{
			currencySymbolDrawable = getResources().getDrawable(R.drawable.currency_symbol_mbtc);
		}
		else if (currencyCode != null)
		{
			final String currencySymbol = currencySymbol(currencyCode);
			final float textSize = textView.getTextSize();
			final float smallerTextSize = textSize * (smallerInsignificant ? (20f / 24f) : 1);
			currencySymbolDrawable = new CurrencySymbolDrawable(currencySymbol, smallerTextSize, lessSignificantColor, textSize * 0.37f);
		}
		else
		{
			currencySymbolDrawable = null;
		}

		updateAppearance();
	}

	public void setInputPrecision(final int inputPrecision)
	{
		this.inputPrecision = inputPrecision;
	}

	public void setHintPrecision(final int hintPrecision)
	{
		this.hintPrecision = hintPrecision;
	}

	public void setShift(final int shift)
	{
		this.shift = shift;
	}

	public void setAmountSigned(final boolean amountSigned)
	{
		this.amountSigned = amountSigned;
	}

	public void setSmallerInsignificant(final boolean smallerInsignificant)
	{
		this.smallerInsignificant = smallerInsignificant;
	}

	public void setValidateAmount(final boolean validateAmount)
	{
		this.validateAmount = validateAmount;
	}

	public void setContextButton(final int contextButtonResId, @Nonnull final OnClickListener contextButtonClickListener)
	{
		this.contextButtonDrawable = getContext().getResources().getDrawable(contextButtonResId);
		this.contextButtonClickListener = contextButtonClickListener;

		updateAppearance();
	}

	public void setListener(@Nonnull final Listener listener)
	{
		this.listener = listener;
	}

	@CheckForNull
	public BigInteger getAmount()
	{
		if (isValidAmount(false))
			return GenericUtils.toNanoCoins(textView.getText().toString().trim(), shift);
		else
			return null;
	}

	public void setAmount(@Nullable final BigInteger amount, final boolean fireListener)
	{
		if (!fireListener)
			textViewListener.setFire(false);

		if (amount != null)
			textView.setText(amountSigned ? GenericUtils.formatValue(amount, Constants.CURRENCY_PLUS_SIGN, Constants.CURRENCY_MINUS_SIGN,
					inputPrecision, shift) : GenericUtils.formatValue(amount, inputPrecision, shift));
		else
			textView.setText(null);

		if (!fireListener)
			textViewListener.setFire(true);
	}

	public void setHint(@Nullable final BigInteger amount)
	{
		final SpannableStringBuilder hint;
		if (amount != null)
			hint = new SpannableStringBuilder(GenericUtils.formatValue(amount, hintPrecision, shift));
		else
			hint = new SpannableStringBuilder("0.00");

		WalletUtils.formatSignificant(hint, smallerInsignificant ? WalletUtils.SMALLER_SPAN : null);
		textView.setHint(hint);
	}

	@Override
	public void setEnabled(final boolean enabled)
	{
		super.setEnabled(enabled);

		textView.setEnabled(enabled);

		updateAppearance();
	}

	public void setTextColor(final int color)
	{
		significantColor = color;

		updateAppearance();
	}

	public void setStrikeThru(final boolean strikeThru)
	{
		if (strikeThru)
			textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
		else
			textView.setPaintFlags(textView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
	}

	private boolean isValidAmount(final boolean zeroIsValid)
	{
		final String amount = textView.getText().toString().trim();

		try
		{
			if (!amount.isEmpty())
			{
				final BigInteger nanoCoins = GenericUtils.toNanoCoins(amount, shift);

				// exactly zero
				if (zeroIsValid && nanoCoins.signum() == 0)
					return true;

				// too small
				if (nanoCoins.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0)
					return false;

				return true;
			}
		}
		catch (final Exception x)
		{
		}

		return false;
	}

	private final OnClickListener deleteClickListener = new OnClickListener()
	{
		@Override
		public void onClick(final View v)
		{
			setAmount(null, true);
			textView.requestFocus();
		}
	};

	private void updateAppearance()
	{
		final boolean enabled = textView.isEnabled();

		contextButton.setEnabled(enabled);

		final String amount = textView.getText().toString().trim();

		if (enabled && !amount.isEmpty())
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencySymbolDrawable, null, deleteButtonDrawable, null);
			contextButton.setOnClickListener(deleteClickListener);
		}
		else if (enabled && contextButtonDrawable != null)
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencySymbolDrawable, null, contextButtonDrawable, null);
			contextButton.setOnClickListener(contextButtonClickListener);
		}
		else
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencySymbolDrawable, null, null, null);
			contextButton.setOnClickListener(null);
		}

		contextButton.requestLayout();

		textView.setTextColor(!validateAmount || isValidAmount(true) ? significantColor : errorColor);
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		final Bundle state = new Bundle();
		state.putParcelable("super_state", super.onSaveInstanceState());
		state.putParcelable("child_textview", textView.onSaveInstanceState());
		state.putSerializable("amount", getAmount());
		return state;
	}

	@Override
	protected void onRestoreInstanceState(final Parcelable state)
	{
		if (state instanceof Bundle)
		{
			final Bundle bundle = (Bundle) state;
			super.onRestoreInstanceState(bundle.getParcelable("super_state"));
			textView.onRestoreInstanceState(bundle.getParcelable("child_textview"));
			setAmount((BigInteger) bundle.getSerializable("amount"), false);
		}
		else
		{
			super.onRestoreInstanceState(state);
		}
	}

	private final TextViewListener textViewListener = new TextViewListener();

	private final class TextViewListener implements TextWatcher, OnFocusChangeListener, OnEditorActionListener
	{
		private boolean fire = true;

		public void setFire(final boolean fire)
		{
			this.fire = fire;
		}

		@Override
		public void afterTextChanged(final Editable s)
		{
			// workaround for German keyboards
			final String original = s.toString();
			final String replaced = original.replace(',', '.');
			if (!replaced.equals(original))
			{
				s.clear();
				s.append(replaced);
			}

			WalletUtils.formatSignificant(s, smallerInsignificant ? WalletUtils.SMALLER_SPAN : null);
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
			updateAppearance();
			if (listener != null && fire)
				listener.changed();
		}

		@Override
		public void onFocusChange(final View v, final boolean hasFocus)
		{
			if (!hasFocus)
			{
				final BigInteger amount = getAmount();
				if (amount != null)
					setAmount(amount, false);
			}

			if (listener != null && fire)
				listener.focusChanged(hasFocus);
		}

		@Override
		public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event)
		{
			if (actionId == EditorInfo.IME_ACTION_DONE && listener != null && fire)
				listener.done();

			return false;
		}
	}

	private static String currencySymbol(@Nonnull final String currencyCode)
	{
		try
		{
			final Currency currency = Currency.getInstance(currencyCode);
			return currency.getSymbol();
		}
		catch (final IllegalArgumentException x)
		{
			return currencyCode;
		}
	}
}
