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

package de.schildbach.wallet.util;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import okhttp3.HttpUrl;

/**
 * @author Andreas Schildbach
 */
public enum Installer {
    F_DROID("F-Droid"), GOOGLE_PLAY("Google Play"), AMAZON_APPSTORE("Amazon Appstore"), AURORA_STORE("Aurora Store");

    public final String displayName;

    Installer(final String displayName) {
        this.displayName = displayName;
    }

    public static String installerPackageName(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm.getInstallerPackageName(context.getPackageName());
    }

    public static Installer from(final String installerPackageName) {
        if ("org.fdroid.fdroid".equals(installerPackageName)
                || "org.fdroid.fdroid.privileged".equals(installerPackageName))
            return F_DROID;
        if ("com.android.vending".equals(installerPackageName))
            return GOOGLE_PLAY;
        if ("com.amazon.venezia".equals(installerPackageName))
            return AMAZON_APPSTORE;
        if ("com.aurora.store".equals(installerPackageName))
            return AURORA_STORE;
        return null;
    }

    public static Installer from(final Context context) {
        return from(installerPackageName(context));
    }

    public HttpUrl appStorePageFor(final Application application) {
        final HttpUrl.Builder url;
        if (this == F_DROID) {
            url = HttpUrl.parse("https://f-droid.org/de/packages/").newBuilder();
            url.addPathSegment(application.getPackageName());
        } else if (this == GOOGLE_PLAY || this == AURORA_STORE) {
            url = HttpUrl.parse("https://play.google.com/store/apps/details").newBuilder();
            url.addQueryParameter("id", application.getPackageName());
        } else if (this == AMAZON_APPSTORE) {
            url = HttpUrl.parse("https://www.amazon.com/gp/mas/dl/android").newBuilder();
            url.addQueryParameter("p", application.getPackageName());
        } else {
            throw new IllegalStateException(this.toString());
        }
        return url.build();
    }
}
