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

package de.schildbach.wallet.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainState.Impediment;

import java.util.Set;

/**
 * @author Andreas Schildbach
 */
public final class WalletDisclaimerFragment extends Fragment {
    private WalletActivity activity;
    private WalletApplication application;

    private TextView messageView;

    private WalletActivityViewModel activityViewModel;
    private WalletDisclaimerViewModel viewModel;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (WalletActivity) context;
        this.application = activity.getWalletApplication();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityViewModel = new ViewModelProvider(activity).get(WalletActivityViewModel.class);
        viewModel = new ViewModelProvider(this).get(WalletDisclaimerViewModel.class);

        application.blockchainState.observe(this, blockchainState -> updateView());
        viewModel.getDisclaimerEnabled().observe(this, disclaimerEnabled -> updateView());
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        messageView = (TextView) inflater.inflate(R.layout.wallet_disclaimer_fragment, container, false);
        messageView.setOnClickListener(v -> activityViewModel.showHelpDialog.setValue(new Event<>(R.string.help_safety)));
        return messageView;
    }

    private void updateView() {
        final BlockchainState blockchainState = application.blockchainState.getValue();
        final Boolean disclaimerEnabled = viewModel.getDisclaimerEnabled().getValue();
        final boolean showDisclaimer = disclaimerEnabled != null && disclaimerEnabled;

        int progressResId = 0;
        if (blockchainState != null) {
            final Set<Impediment> impediments = blockchainState.impediments;
            if (impediments.contains(Impediment.STORAGE))
                progressResId = R.string.blockchain_state_progress_problem_storage;
            else if (impediments.contains(Impediment.NETWORK))
                progressResId = R.string.blockchain_state_progress_problem_network;
        }

        final SpannableStringBuilder text = new SpannableStringBuilder();
        if (progressResId != 0)
            text.append(Html.fromHtml("<b>" + getString(progressResId) + "</b>"));
        if (progressResId != 0 && showDisclaimer)
            text.append('\n');
        if (showDisclaimer)
            text.append(Html.fromHtml(getString(R.string.wallet_disclaimer_fragment_remind_safety)));
        messageView.setText(text);

        final View view = getView();
        final ViewParent parent = view.getParent();
        final View fragment = parent instanceof FrameLayout ? (FrameLayout) parent : view;
        fragment.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
    }
}
