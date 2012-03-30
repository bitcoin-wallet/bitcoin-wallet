/**
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.integration.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import de.schildbach.wallet.integration.android.BitcoinIntegration;

/**
 * @author Andreas Schildbach
 */
public class SampleActivity extends Activity
{
	private static final String DONATION_ADDRESS = "1PZmMahjbfsTy6DsaRyfStzoWTPppWwDnZ"; // prodnet
	// private static final String DONATION_ADDRESS = "mwEacn7pYszzxfgcNaVUzYvzL6ypRJzB6A"; // testnet
	private static final int REQUEST_CODE = 0;

	private Button donateButton;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.sample_activity);

		donateButton = (Button) findViewById(R.id.sample_donate_button);

		donateButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				BitcoinIntegration.requestForResult(SampleActivity.this, REQUEST_CODE, DONATION_ADDRESS);
			}
		});
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		if (requestCode == REQUEST_CODE)
		{
			if (resultCode == Activity.RESULT_OK)
			{
				final String txHash = BitcoinIntegration.transactionHashFromResult(data);
				if (txHash != null)
					Toast.makeText(this, "User sent transaction: " + txHash, Toast.LENGTH_SHORT).show();
				else
					Toast.makeText(this, "User sent transaction.", Toast.LENGTH_SHORT).show();

				donateButton.setEnabled(false);
				donateButton.setText("Already donated");
			}
			else if (resultCode == Activity.RESULT_CANCELED)
			{
				Toast.makeText(this, "User cancelled.", Toast.LENGTH_SHORT).show();
			}
			else
			{
				Toast.makeText(this, "Unknown result.", Toast.LENGTH_SHORT).show();
			}
		}
	}
}
