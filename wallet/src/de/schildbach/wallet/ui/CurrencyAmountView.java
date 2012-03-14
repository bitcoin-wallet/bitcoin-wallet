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

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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

import com.google.bitcoin.core.Utils;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class CurrencyAmountView extends FrameLayout
{
	public static interface Listener
	{
		void changed();

		void done();
	}

	private int significantColor, lessSignificantColor, errorColor;
	private Drawable deleteButtonDrawable, contextButtonDrawable;
	private CurrencyCodeDrawable currencyCodeDrawable;
	private boolean amountSigned = false;
	private boolean validateAmount = true;

	private TextView textView;
	private View chooseView;
	private Listener listener;
	private OnClickListener contextButtonClickListener;

	private final class TextViewListener implements TextWatcher, OnFocusChangeListener, OnEditorActionListener
	{
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

			updateSpans(s);
		}

		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
			updateAppearance();
			if (listener != null)
				listener.changed();
		}

		public void onFocusChange(final View v, final boolean hasFocus)
		{
			if (!hasFocus)
			{
				final BigInteger amount = getAmount();
				if (amount != null)
					setAmount(amount);
			}
		}

		public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event)
		{
			if (actionId == EditorInfo.IME_ACTION_DONE && listener != null)
				listener.done();

			return false;
		}
	}

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
		significantColor = resources.getColor(R.color.significant);
		lessSignificantColor = resources.getColor(R.color.less_significant);
		errorColor = resources.getColor(R.color.error);
		deleteButtonDrawable = resources.getDrawable(R.drawable.ic_input_delete);
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		final Context context = getContext();

		final TextViewListener textViewListener = new TextViewListener();

		textView = (TextView) getChildAt(0);
		textView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		textView.addTextChangedListener(textViewListener);
		textView.setOnFocusChangeListener(textViewListener);
		textView.setOnEditorActionListener(textViewListener);
		textView.setHintTextColor(lessSignificantColor);
		setHint(null);
		setValidateAmount(textView instanceof EditText);

		chooseView = new View(context)
		{
			@Override
			protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec)
			{
				setMeasuredDimension(textView.getCompoundPaddingRight(), textView.getMeasuredHeight());
			}
		};
		final LayoutParams chooseViewParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		chooseViewParams.gravity = Gravity.RIGHT;
		chooseView.setLayoutParams(chooseViewParams);
		this.addView(chooseView);

		setCurrencyCode(Constants.CURRENCY_CODE_BITCOIN);

		updateAppearance();
	}

	public void setCurrencyCode(final String currencyCode)
	{
		if (currencyCode != null)
		{
			final float textSize = textView.getTextSize();
			currencyCodeDrawable = new CurrencyCodeDrawable(currencyCode, textSize * 20f / 24f, lessSignificantColor, textSize * 0.37f);
		}
		else
		{
			currencyCodeDrawable = null;
		}

		updateAppearance();
	}

	public void setAmountSigned(final boolean amountSigned)
	{
		this.amountSigned = amountSigned;
	}

	public void setValidateAmount(final boolean validateAmount)
	{
		this.validateAmount = validateAmount;
	}

	public void setContextButton(final int contextButtonResId, final OnClickListener contextButtonClickListener)
	{
		this.contextButtonDrawable = getContext().getResources().getDrawable(contextButtonResId);
		this.contextButtonClickListener = contextButtonClickListener;

		updateAppearance();
	}

	public void setListener(final Listener listener)
	{
		this.listener = listener;
	}

	public BigInteger getAmount()
	{
		if (isValidAmount())
			return Utils.toNanoCoins(textView.getText().toString().trim());
		else
			return null;
	}

	public void setAmount(final BigInteger amount)
	{
		if (amount != null)
			textView.setText(amountSigned ? WalletUtils.formatValue(amount, Constants.CURRENCY_PLUS_SIGN, Constants.CURRENCY_MINUS_SIGN)
					: WalletUtils.formatValue(amount));
		else
			textView.setText(null);
	}

	public void setHint(final BigInteger amount)
	{
		final SpannableStringBuilder hint;
		if (amount != null)
			hint = new SpannableStringBuilder(WalletUtils.formatValue(amount));
		else
			hint = new SpannableStringBuilder("0.00");

		updateSpans(hint);
		textView.setHint(hint);
	}

	@Override
	public void setEnabled(final boolean enabled)
	{
		super.setEnabled(enabled);

		textView.setEnabled(enabled);
	}

	public void setTextColor(final int color)
	{
		significantColor = color;

		updateAppearance();
	}

	private static final Pattern P_SIGNIFICANT = Pattern.compile("^([-+]" + Constants.THIN_SPACE + ")?\\d*(\\.\\d{0,2})?");
	private static Object SIGNIFICANT_SPAN = new StyleSpan(Typeface.BOLD);
	private static Object UNSIGNIFICANT_SPAN = new RelativeSizeSpan(0.85f);

	private static void updateSpans(final Editable s)
	{
		s.removeSpan(SIGNIFICANT_SPAN);
		s.removeSpan(UNSIGNIFICANT_SPAN);

		final Matcher m = P_SIGNIFICANT.matcher(s);
		if (m.find())
		{
			final int pivot = m.group().length();
			s.setSpan(SIGNIFICANT_SPAN, 0, pivot, 0);
			if (s.length() > pivot)
				s.setSpan(UNSIGNIFICANT_SPAN, pivot, s.length(), 0);
		}
	}

	private boolean isValidAmount()
	{
		final String amount = textView.getText().toString().trim();

		try
		{
			if (amount.length() > 0)
			{
				final BigInteger nanoCoins = Utils.toNanoCoins(amount);
				if (nanoCoins.signum() >= 0)
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
		public void onClick(final View v)
		{
			textView.setText(null);
		}
	};

	private void updateAppearance()
	{
		final String amount = textView.getText().toString().trim();

		if (textView.isEnabled() && amount.length() > 0)
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencyCodeDrawable, null, deleteButtonDrawable, null);
			chooseView.setOnClickListener(deleteClickListener);
		}
		else if (contextButtonDrawable != null)
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencyCodeDrawable, null, contextButtonDrawable, null);
			chooseView.setOnClickListener(contextButtonClickListener);
		}
		else
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencyCodeDrawable, null, null, null);
			chooseView.setOnClickListener(null);
		}

		chooseView.requestLayout();

		textView.setTextColor(!validateAmount || isValidAmount() ? significantColor : errorColor);
	}
}
