/*
 * Copyright 2013 Google Inc.
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

package de.schildbach.wallet.util;

import java.io.*;
import java.util.HashMap;
import java.util.Set;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps track of which app opened a payment channel by the channel's contract hash
 */
public class PaymentChannelContractToCreatorMap implements WalletExtension {
	private static final String EXTENSION_ID = PaymentChannelContractToCreatorMap.class.getName();
	private static final Logger log = LoggerFactory.getLogger(PaymentChannelContractToCreatorMap.class);
	private Runnable newContractCallback;

	private static class CreatorAndSpentFlag implements Serializable {
		public String creatorApp;
		public boolean contractSpendSeen = false;
		public CreatorAndSpentFlag(String creatorApp) { this.creatorApp = creatorApp; }
	}

	private HashMap<Sha256Hash, CreatorAndSpentFlag> contractHashToAppMap = new HashMap<Sha256Hash, CreatorAndSpentFlag>();
	private final Wallet containingWallet;

	public PaymentChannelContractToCreatorMap(Wallet wallet) {
		this.containingWallet = wallet;
	}

	/**
	 * Returns the human-readable name of the app which created the contract with the given hash, or null
	 */
	public synchronized String getCreatorApp(Sha256Hash contractHash) {
		CreatorAndSpentFlag creator = contractHashToAppMap.get(contractHash);
		return creator == null ? null : creator.creatorApp;
	}

	/**
	 * Returns true if a spend of the payment channel contract with the given hash has been seen.
	 */
	public synchronized boolean isSpendSeen(Sha256Hash contractHash) {
		CreatorAndSpentFlag creator = contractHashToAppMap.get(contractHash);
		return creator != null && creator.contractSpendSeen;
	}

	/**
	 * Sets the creator of the payment channel with the given contract hash in human-readable form
	 */
	public void setCreatorApp(Sha256Hash contractHash, String appName) {
		Runnable runCallback;
		synchronized (this) {
			log.info("Adding new contract with hash " + contractHash.toString());
			if (!contractHashToAppMap.containsKey(contractHash))
				contractHashToAppMap.put(contractHash, new CreatorAndSpentFlag(appName));
			containingWallet.addOrUpdateExtension(this);
			runCallback= newContractCallback;
		}
		if (runCallback != null)
			runCallback.run();
	}

	public synchronized void setNewContractCallback(Runnable runnable) {
		this.newContractCallback = runnable;
	}

	/**
	 * Checks if the given transaction spends a payment channel contract of ours and updates state if it does
	 */
	public synchronized void checkContractSpent(Transaction tx) {
		for (TransactionInput input : tx.getInputs()) {
			CreatorAndSpentFlag creator = contractHashToAppMap.get(input.getOutpoint().getHash());
			if (creator != null && !creator.contractSpendSeen) {
				log.info("Contract spend seen for contract " + input.getOutpoint().getHash().toString());
				creator.contractSpendSeen = true;
				containingWallet.addOrUpdateExtension(this);
			}
		}
	}

	/**
	 * Gets the set of contracts which are stored in this map
	 */
	public synchronized Set<Sha256Hash> getContractSet() {
		return contractHashToAppMap.keySet();
	}

	@Override
	public String getWalletExtensionID() {
		return EXTENSION_ID;
	}

	@Override
	public boolean isWalletExtensionMandatory() {
		return false;
	}

	@Override
	public synchronized byte[] serializeWalletExtension() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(out);
			oos.writeObject(contractHashToAppMap);
			return out.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public synchronized void deserializeWalletExtension(Wallet containingWallet, byte[] data) throws Exception {
		contractHashToAppMap.clear();
		ByteArrayInputStream inStream = new ByteArrayInputStream(data);
		ObjectInputStream ois = new ObjectInputStream(inStream);
		contractHashToAppMap = (HashMap<Sha256Hash, CreatorAndSpentFlag>) ois.readObject();
	}
}
