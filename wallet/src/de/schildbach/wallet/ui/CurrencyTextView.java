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

import java.math.BigInteger;

import javax.annotation.Nonnull;

import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.widget.TextView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class CurrencyTextView extends TextView
{
	private String prefix = null;
	private ForegroundColorSpan prefixColorSpan = null;
	private BigInteger amount = null;
	private int precision = 0;
	private int shift = 0;
	private boolean alwaysSigned = false;
	private RelativeSizeSpan prefixRelativeSizeSpan = null;
	private RelativeSizeSpan insignificantRelativeSizeSpan = null;

	public CurrencyTextView(final Context context)
	{
		super(context);
	}

	public CurrencyTextView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
	}

	public void setPrefix(@Nonnull final String prefix)
	{
		this.prefix = prefix + Constants.CHAR_HAIR_SPACE;
		updateView();
	}

	public void setPrefixColor(final int prefixColor)
	{
		this.prefixColorSpan = new ForegroundColorSpan(prefixColor);
		updateView();
	}

	public void setAmount(@Nonnull final BigInteger amount)
	{
		this.amount = amount;
		updateView();
	}

	public void setPrecision(final int precision, final int shift)
	{
		this.precision = precision;
		this.shift = shift;
		updateView();
	}

	public void setAlwaysSigned(final boolean alwaysSigned)
	{
		this.alwaysSigned = alwaysSigned;
		updateView();
	}

	public void setStrikeThru(final boolean strikeThru)
	{
		if (strikeThru)
			setPaintFlags(getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
		else
			setPaintFlags(getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
	}

	public void setInsignificantRelativeSize(final float insignificantRelativeSize)
	{
		if (insignificantRelativeSize != 1)
		{
			this.prefixRelativeSizeSpan = new RelativeSizeSpan(insignificantRelativeSize);
			this.insignificantRelativeSizeSpan = new RelativeSizeSpan(insignificantRelativeSize);
		}
		else
		{
			this.prefixRelativeSizeSpan = null;
			this.insignificantRelativeSizeSpan = null;
		}
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		setPrefixColor(getResources().getColor(R.color.fg_less_significant));
		setInsignificantRelativeSize(0.85f);
		setSingleLine();
	}

	private void updateView()
	{
		final Editable text;

		if (amount != null)
		{
			final String s;
			if (alwaysSigned)
				s = GenericUtils.formatValue(amount, Constants.CURRENCY_PLUS_SIGN, Constants.CURRENCY_MINUS_SIGN, precision, shift);
			else
				s = GenericUtils.formatValue(amount, precision, shift);

			text = new SpannableStringBuilder(s);
			WalletUtils.formatSignificant(text, insignificantRelativeSizeSpan);

			if (prefix != null)
			{
				text.insert(0, prefix);
				if (prefixRelativeSizeSpan != null)
					text.setSpan(prefixRelativeSizeSpan, 0, prefix.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				if (prefixColorSpan != null)
					text.setSpan(prefixColorSpan, 0, prefix.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
		else
		{
			text = null;
		}

		setText(text);
	}
}
