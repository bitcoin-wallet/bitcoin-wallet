package de.schildbach.wallet.ui.preference;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.schildbach.wallet_test.R;

/**
 * @author Max Keller
 */
public class PreferenceFragment extends Fragment{

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.preference_fragment, container, false);

        ViewPager pager = (ViewPager) root.findViewById(R.id.settings_pager);
        TabLayout tabs = (TabLayout) root.findViewById(R.id.settings_tabs);
        SettingsFragmentAdapter adaper = new SettingsFragmentAdapter(getFragmentManager());

        pager.setAdapter(adaper);
        tabs.setupWithViewPager(pager);

        return root;
    }

    private class SettingsFragmentAdapter extends FragmentStatePagerAdapter {
        public SettingsFragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new SettingsFragment();
                case 1:
                    return new DiagnosticsFragment();
                case 2:
                    return new AboutFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.preferences_activity_title);
                case 1:
                    return getString(R.string.preferences_category_diagnostics);
                case 2:
                    return getString(R.string.about_title);
                default:
                    return null;
            }
        }
    }
}
