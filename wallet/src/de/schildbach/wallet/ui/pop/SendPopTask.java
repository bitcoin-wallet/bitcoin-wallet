package de.schildbach.wallet.ui.pop;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.pop.PopActivity.Outcome;
import de.schildbach.wallet.ui.pop.PopActivity.State;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import se.rosenbaum.jpop.Pop;
import se.rosenbaum.jpop.PopRequestURI;
import se.rosenbaum.jpop.generate.HttpPopSender;
import se.rosenbaum.jpop.generate.PopGenerationException;
import se.rosenbaum.jpop.generate.PopGenerator;
import se.rosenbaum.jpop.generate.PopSigningException;

public abstract class SendPopTask
{
	private static final Logger log = LoggerFactory.getLogger(SendPopTask.class);

	private final WalletApplication walletApplication;
	private final PopRequestURI popRequestURI;
	private final String pin;
	private final Transaction transactionToProve;
	private final Handler backgroundHandler;
    private final Handler callbackHandler;

	public SendPopTask(final WalletApplication walletApplication, final PopRequestURI popRequestURI, final String pin, final Transaction transactionToProve)
	{
		this.walletApplication = walletApplication;
		this.popRequestURI = popRequestURI;
		this.pin = pin;
		this.transactionToProve = transactionToProve;
		HandlerThread backgroundThread = new HandlerThread("popBackgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());
		callbackHandler = new Handler(Looper.myLooper());
	}

	private KeyParameter getKeyParameter()
	{
		Wallet wallet = walletApplication.getWallet();
		if (!wallet.isEncrypted())
		{
			return null;
		}
		KeyCrypter keyCrypter = wallet.getKeyCrypter();
		if (keyCrypter == null)
		{
			return null;
		}
		return keyCrypter.deriveKey(pin);
	}

	private void publishProgress(final State state)
	{
		callbackHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				onProgressUpdate(state);
			}
		});
	}

	private void publishResult(final Outcome outcome)
	{
		callbackHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				onPostExecute(outcome);
			}
		});
	}

	public void sendPop()
	{
		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				sendPop(popRequestURI);
			}
		});
	}

	private void sendPop(final PopRequestURI popRequestURI)
	{
		Outcome outcome = new Outcome();
		try
		{
			publishProgress(State.DECRYPTING);
			final KeyParameter encryptionKey = getKeyParameter();
			PopGenerator popGenerator = new PopGenerator();
			Pop pop = popGenerator.createPop(transactionToProve, popRequestURI.getN());
			publishProgress(State.SIGNING);
			popGenerator.signPop(pop, walletApplication.getWallet(), encryptionKey);
			HttpPopSender popSender = new HttpPopSender(popRequestURI);
			publishProgress(State.SENDING);
			popSender.sendPop(pop);
			outcome.popSender = popSender;
		}
		catch (PopGenerationException e)
		{
			publishProgress(State.FAILED);
			log.debug("Couldn't create PoP", e);
			outcome.exception = e;
		}
		catch (PopSigningException e)
		{
			publishProgress(State.FAILED);
			log.debug("Couldn't sign PoP", e);
			outcome.exception = e;
		}
		catch (KeyCrypterException e)
		{
			publishProgress(State.INPUT);
			outcome.exception = e;
		}
		publishResult(outcome);
	}

	protected abstract void onProgressUpdate(final State value);

	protected abstract void onPostExecute(final Outcome outcome);
}
