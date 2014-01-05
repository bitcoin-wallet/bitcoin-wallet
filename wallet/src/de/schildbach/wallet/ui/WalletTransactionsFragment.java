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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import de.schildbach.wallet.ui.TransactionsListFragment.Direction;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet_ltc.R;

/**
 * @author Andreas Schildbach, Litecoin Dev Team
 */
public final class WalletTransactionsFragment extends Fragment
{
	private static final int INITIAL_PAGE = 1;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_transactions_fragment, container, false);

		final ViewPagerTabs pagerTabs = (ViewPagerTabs) view.findViewById(R.id.transactions_pager_tabs);
		pagerTabs.addTabLabels(R.string.wallet_transactions_fragment_tab_received, R.string.wallet_transactions_fragment_tab_all,
				R.string.wallet_transactions_fragment_tab_sent);

		final PagerAdapter pagerAdapter = new PagerAdapter(getFragmentManager());

		final ViewPager pager = (ViewPager) view.findViewById(R.id.transactions_pager);
		pager.setAdapter(pagerAdapter);
		pager.setOnPageChangeListener(pagerTabs);
		pager.setCurrentItem(INITIAL_PAGE);
		pager.setPageMargin(2);
		pager.setPageMarginDrawable(R.color.bg_less_bright);
		pagerTabs.onPageScrolled(INITIAL_PAGE, 0, 0); // should not be needed

		return view;
	}

	private static class PagerAdapter extends FragmentStatePagerAdapter
	{
		public PagerAdapter(final FragmentManager fm)
		{
			super(fm);
		}

		@Override
		public int getCount()
		{
			return 3;
		}

		@Override
		public Fragment getItem(final int position)
		{
			final Direction direction;
			if (position == 0)
				direction = Direction.RECEIVED;
			else if (position == 1)
				direction = null;
			else if (position == 2)
				direction = Direction.SENT;
			else
				throw new IllegalStateException();

			return TransactionsListFragment.instance(direction);
		}
	}
}
