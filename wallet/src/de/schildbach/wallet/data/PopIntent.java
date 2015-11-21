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

package de.schildbach.wallet.data;

import android.os.Parcel;
import android.os.Parcelable;
import se.rosenbaum.jpop.PopRequestURI;

/**
 * @author Kalle Rosenbaum
 */
public class PopIntent implements Parcelable
{
	private final PopRequestURI popRequestURI;

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel parcel, int i)
	{
		parcel.writeString(popRequestURI.toURIString());
	}

	public static final Parcelable.Creator<PopIntent> CREATOR = new Parcelable.Creator<PopIntent>()
	{
		@Override
		public PopIntent createFromParcel(final Parcel in)
		{
			return new PopIntent(in);
		}

		@Override
		public PopIntent[] newArray(final int size)
		{
			return new PopIntent[size];
		}
	};

	private PopIntent(final Parcel in)
	{
		popRequestURI = new PopRequestURI(in.readString());
	}

	private PopIntent(final PopRequestURI popRequestURI)
	{
		this.popRequestURI = popRequestURI;
	}

	public static PopIntent fromPopRequestURI(final PopRequestURI popRequestURI)
	{
		return new PopIntent(popRequestURI);
	}

	public PopRequestURI getPopRequestURI()
	{
		return popRequestURI;
	}
}
