/*
 * Copyright 2010 the original author or authors.
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

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.bitcoin.core.Utils;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class CurrencyAmountView extends FrameLayout
{
	public static interface Listener
	{
		void changed();

		void done();
	}

	private TextView textView;
	private View chooseView;
	private Listener listener;
	private String currencyCode = "BTC";
	private int contextButtonResId = 0;
	private OnClickListener contextButtonClickListener;

	private final TextWatcher textChangedListener = new TextWatcher()
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

			spanSignificant(s);
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
	};

	private final OnEditorActionListener editorActionListener = new OnEditorActionListener()
	{
		public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event)
		{
			if (actionId == EditorInfo.IME_ACTION_DONE)
			{
				if (listener != null)
					listener.done();
				return true;
			}

			return false;
		}
	};

	public CurrencyAmountView(final Context context)
	{
		super(context);
	}

	public CurrencyAmountView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		final Context context = getContext();

		textView = (TextView) getChildAt(0);
		textView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		textView.addTextChangedListener(textChangedListener);
		textView.setOnEditorActionListener(editorActionListener);
		setHint(null);

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

		updateAppearance();
	}

	public void setCurrencyCode(final String currencyCode)
	{
		this.currencyCode = currencyCode;

		updateAppearance();
	}

	public void setContextButton(final int contextButtonResId, final OnClickListener contextButtonClickListener)
	{
		this.contextButtonResId = contextButtonResId;
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
			textView.setText(Utils.bitcoinValueToFriendlyString(amount));
		else
			textView.setText(null);
	}

	public void setHint(final BigInteger amount)
	{
		final SpannableStringBuilder hint;
		if (amount != null)
			hint = new SpannableStringBuilder(Utils.bitcoinValueToFriendlyString(amount));
		else
			hint = new SpannableStringBuilder("0.00");

		spanSignificant(hint);
		textView.setHint(hint);
	}

	private static final Pattern P_SIGNIFICANT = Pattern.compile("^\\d*(\\.\\d{0,2})?");
	private static Object SIGNIFICANT_SPAN = new StyleSpan(Typeface.BOLD);

	private static void spanSignificant(final Editable s)
	{
		s.removeSpan(SIGNIFICANT_SPAN);
		final Matcher m = P_SIGNIFICANT.matcher(s);
		if (m.find())
			s.setSpan(SIGNIFICANT_SPAN, 0, m.group().length(), 0);
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
		final Resources resources = getResources();
		final float density = resources.getDisplayMetrics().density;

		final String amount = textView.getText().toString().trim();

		final Drawable leftDrawable = new CurrencyCodeDrawable(currencyCode, textView.getTextSize() * 20f / 24f, 10.5f * density);

		if (textView.isEnabled() && amount.length() > 0)
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, resources.getDrawable(R.drawable.ic_input_delete), null);
			chooseView.setOnClickListener(deleteClickListener);
		}
		else if (contextButtonResId != 0)
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, resources.getDrawable(contextButtonResId), null);
			chooseView.setOnClickListener(contextButtonClickListener);
		}
		else
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, null, null);
			chooseView.setOnClickListener(null);
		}

		chooseView.requestLayout();

		textView.setTextColor(isValidAmount() ? Color.BLACK : Color.RED);
	}
}
