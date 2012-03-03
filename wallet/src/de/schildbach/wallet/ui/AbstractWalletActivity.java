/*
 * Copyright 2011-2015 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.R;

import android.app.Activity;
import android.app.ActivityManager.TaskDescription;
import android.os.Build;
import android.os.Bundle;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletActivity extends Activity {
    private WalletApplication application;

    protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        application = (WalletApplication) getApplication();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setTaskDescription(new TaskDescription(null, null, getResources().getColor(R.color.bg_action_bar)));

        super.onCreate(savedInstanceState);
    }

    protected WalletApplication getWalletApplication() {
        return application;
    }
}
