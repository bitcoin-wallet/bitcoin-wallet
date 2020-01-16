/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.backup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import de.schildbach.wallet.ui.AbstractWalletActivity;

/**
 * @author Andreas Schildbach
 */
public class BackupWalletActivity extends AbstractWalletActivity {

    public static void start(final Context context) {
        context.startActivity(new Intent(context, BackupWalletActivity.class));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("Referrer: {}", getReferrer());
        BackupWalletDialogFragment.show(getSupportFragmentManager());
    }
}
