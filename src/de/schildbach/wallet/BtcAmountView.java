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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.google.bitcoin.core.Utils;

import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class BtcAmountView extends FrameLayout
{
	public static interface Listener
	{
		void changed();
	}

	private EditText textView;
	private View chooseView;
	private Listener listener;

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

	public BtcAmountView(final Context context)
	{
		super(context);
	}

	public BtcAmountView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		final Context context = getContext();

		textView = (EditText) getChildAt(0);
		textView.setHint("0.00");
		textView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		textView.addTextChangedListener(textChangedListener);

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
		textView.setText(Utils.bitcoinValueToFriendlyString(amount));
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

	private void updateAppearance()
	{
		final Resources resources = getResources();
		final float density = resources.getDisplayMetrics().density;

		final String amount = textView.getText().toString().trim();

		final Drawable leftDrawable = new CurrencyCodeDrawable("BTC", 24f * density, 10.5f * density);

		final Drawable rightDrawable = amount.length() > 0 ? resources.getDrawable(R.drawable.ic_input_delete) : null;

		textView.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, rightDrawable, null);
		chooseView.setOnClickListener(rightDrawable != null ? new OnClickListener()
		{
			public void onClick(View v)
			{
				textView.setText(null);
			}
		} : null);
		chooseView.requestLayout();

		textView.setTextColor(isValidAmount() ? Color.BLACK : Color.RED);
	}
}
