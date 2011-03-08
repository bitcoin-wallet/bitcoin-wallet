/*
 * Copyright 2010 the original author or authors.
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

package de.schildbach.wallet;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkConnection;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

/**
 * @author Andreas Schildbach
 */
public class WalletActivity extends Activity implements WalletEventListener
{
	private static final String WALLET_FILENAME = "wallet";
	private static final NetworkParameters NETWORK_PARAMS = NetworkParameters.prodNet();

	private Wallet wallet;
	private Peer peer;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.wallet_content);

		loadWallet();

		updateGUI();

		final ECKey key = wallet.keychain.get(0);
		final Address address = new Address(NETWORK_PARAMS, key.getPubKey());

		System.out.println("my bitcoin address: " + address.toString());
		((TextView) findViewById(R.id.bitcoin_address)).setText(address.toString());

		try
		{
			final InetAddress inetAddress = inetAddressFromUnsignedInt(Constants.SEED_NODES[0]);
			final NetworkConnection conn = new NetworkConnection(inetAddress, NETWORK_PARAMS);
			final BlockChain chain = new BlockChain(NETWORK_PARAMS, wallet);
			peer = new Peer(NETWORK_PARAMS, conn, chain);
			peer.start();
			// peer.startBlockChainDownload();

			((TextView) findViewById(R.id.peer_host)).setText(inetAddress.getHostAddress());

			wallet.addEventListener(this);
		}
		catch (Exception x)
		{
			throw new RuntimeException(x);
		}
	}

	public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
	{
		try
		{
			final TransactionInput input = tx.getInputs().get(0);
			final Address from = input.getFromAddress();
			final BigInteger value = tx.getValueSentToMe(w);

			System.out.println("!!!!!!!!!!!!! got bitcoins: " + from + " " + value + " " + Thread.currentThread().getName());

			runOnUiThread(new Runnable()
			{
				public void run()
				{
					saveWallet();

					updateGUI();
				}
			});
		}
		catch (Exception x)
		{
			throw new RuntimeException(x);
		}
	}

	@Override
	protected void onDestroy()
	{
		if (peer != null)
		{
			peer.disconnect();
			peer = null;
		}

		saveWallet();

		super.onDestroy();
	}

	private void updateGUI()
	{
		((TextView) findViewById(R.id.wallet_balance)).setText(Utils.bitcoinValueToFriendlyString(wallet.getBalance()));
	}

	private void loadWallet()
	{
		final File walletFile = new File(getFilesDir(), WALLET_FILENAME);

		try
		{
			wallet = Wallet.loadFromFile(walletFile);
		}
		catch (IOException x)
		{
			wallet = new Wallet(NETWORK_PARAMS);
			wallet.keychain.add(new ECKey());
		}
	}

	private void saveWallet()
	{
		final File walletFile = new File(getFilesDir(), WALLET_FILENAME);

		try
		{
			wallet.saveToFile(walletFile);
		}
		catch (IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	private static InetAddress inetAddressFromUnsignedInt(final long l)
	{
		final byte[] bytes = { (byte) (l & 0x000000ff), (byte) ((l & 0x0000ff00) >> 8), (byte) ((l & 0x00ff0000) >> 16),
				(byte) ((l & 0xff000000) >> 24) };
		try
		{
			return InetAddress.getByAddress(bytes);
		}
		catch (final UnknownHostException x)
		{
			throw new RuntimeException(x);
		}
	}
}
