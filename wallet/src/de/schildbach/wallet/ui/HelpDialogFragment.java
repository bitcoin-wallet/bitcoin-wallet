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

import java.util.Locale;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.Window;
import android.webkit.WebView;

/**
 * @author Andreas Schildbach
 */
public final class HelpDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = HelpDialogFragment.class.getName();

	private static final String KEY_PAGE = "page";

	public static void page(final FragmentManager fm, @Nonnull final String page)
	{
		final DialogFragment newFragment = HelpDialogFragment.instance(page);
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private static HelpDialogFragment instance(@Nonnull final String page)
	{
		final HelpDialogFragment fragment = new HelpDialogFragment();

		final Bundle args = new Bundle();
		args.putString(KEY_PAGE, page);
		fragment.setArguments(args);

		return fragment;
	}

	private Activity activity;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Bundle args = getArguments();
		final String page = args.getString(KEY_PAGE);

		final WebView webView = new WebView(activity);
		webView.loadUrl("file:///android_asset/" + page + languagePrefix() + ".html");

		final Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}

	private final static String languagePrefix()
	{
		final String language = Locale.getDefault().getLanguage();
		if ("de".equals(language))
			return "_de";
		else if ("cs".equals(language))
			return "_cs";
		else if ("el".equals(language))
			return "_el";
		else if ("es".equals(language))
			return "_es";
		else if ("fr".equals(language))
			return "_fr";
		else if ("it".equals(language))
			return "_it";
		else if ("nl".equals(language))
			return "_nl";
		else if ("pl".equals(language))
			return "_pl";
		else if ("ru".equals(language))
			return "_ru";
		else if ("sk".equals(language))
			return "_sk";
		else if ("sv".equals(language))
			return "_sv";
		else if ("tr".equals(language))
			return "_tr";
		else if ("zh".equals(language))
			return "_zh";
		else
			return "";
	}
}
