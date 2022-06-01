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

import android.content.Context;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.TypefaceSpan;
import androidx.annotation.Nullable;
import com.google.common.base.Stopwatch;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.service.BlockchainService;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Andreas Schildbach
 */
public class WalletUtils {
    private static final Logger log = LoggerFactory.getLogger(WalletUtils.class);

    public static Spanned formatAddress(final Address address, final int groupSize, final int lineSize) {
        return formatHash(address.toString(), groupSize, lineSize);
    }

    public static Spanned formatAddress(@Nullable final String prefix, final Address address, final int groupSize,
            final int lineSize) {
        return formatHash(prefix, address.toString(), groupSize, lineSize, Constants.CHAR_THIN_SPACE);
    }

    public static Spanned formatHash(final String hash, final int groupSize, final int lineSize) {
        return formatHash(null, hash, groupSize, lineSize, Constants.CHAR_THIN_SPACE);
    }

    public static long longHash(final Sha256Hash hash) {
        final byte[] bytes = hash.getBytes();

        return (bytes[31] & 0xFFl) | ((bytes[30] & 0xFFl) << 8) | ((bytes[29] & 0xFFl) << 16)
                | ((bytes[28] & 0xFFl) << 24) | ((bytes[27] & 0xFFl) << 32) | ((bytes[26] & 0xFFl) << 40)
                | ((bytes[25] & 0xFFl) << 48) | ((bytes[23] & 0xFFl) << 56);
    }

    private static class MonospaceSpan extends TypefaceSpan {
        public MonospaceSpan() {
            super("monospace");
        }

        // TypefaceSpan doesn't implement this, and we need it so that Spanned.equals() works.
        @Override
        public boolean equals(final Object o) {
            if (o == this)
                return true;
            if (o == null || o.getClass() != getClass())
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    public static Spanned formatHash(@Nullable final String prefix, final String hash, final int groupSize,
            final int lineSize, final char groupSeparator) {
        final SpannableStringBuilder builder = prefix != null ? new SpannableStringBuilder(prefix)
                : new SpannableStringBuilder();

        final int len = hash.length();
        for (int i = 0; i < len; i += groupSize) {
            final int end = i + groupSize;
            final String part = hash.substring(i, end < len ? end : len);

            builder.append(part);
            builder.setSpan(new MonospaceSpan(), builder.length() - part.length(), builder.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (end < len) {
                final boolean endOfLine = lineSize > 0 && end % lineSize == 0;
                builder.append(endOfLine ? '\n' : groupSeparator);
            }
        }

        return SpannedString.valueOf(builder);
    }

    @Nullable
    public static Address getToAddress(final Script script) {
        try {
            return script.getToAddress(Constants.NETWORK_PARAMETERS, true);
        } catch (final ScriptException x) {
            return null;
        }
    }

    @Nullable
    public static Address getToAddressOfSent(final Transaction tx, final Wallet wallet) {
        for (final TransactionOutput output : tx.getOutputs()) {
            if (!output.isMine(wallet)) {
                final Script script = output.getScriptPubKey();
                final Address address = getToAddress(script);
                if (address != null)
                    return address;
            }
        }

        return null;
    }

    @Nullable
    public static Address getWalletAddressOfReceived(final Transaction tx, final Wallet wallet) {
        for (final TransactionOutput output : tx.getOutputs()) {
            if (output.isMine(wallet)) {
                final Script script = output.getScriptPubKey();
                final Address address = getToAddress(script);
                if (address != null)
                    return address;
            }
        }

        return null;
    }

    public static boolean isEntirelySelf(final Transaction tx, final Wallet wallet) {
        for (final TransactionInput input : tx.getInputs()) {
            final TransactionOutput connectedOutput = input.getConnectedOutput();
            if (connectedOutput == null || !connectedOutput.isMine(wallet))
                return false;
        }

        for (final TransactionOutput output : tx.getOutputs()) {
            if (!output.isMine(wallet))
                return false;
        }

        return true;
    }

    public static void autoBackupWallet(final Context context, final Wallet wallet) {
        final Stopwatch watch = Stopwatch.createStarted();
        final Protos.Wallet.Builder builder = new WalletProtobufSerializer().walletToProto(wallet).toBuilder();

        // strip redundant
        builder.clearTransaction();
        builder.clearLastSeenBlockHash();
        builder.setLastSeenBlockHeight(-1);
        builder.clearLastSeenBlockTimeSecs();
        final Protos.Wallet walletProto = builder.build();

        try (final OutputStream os = context.openFileOutput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF,
                Context.MODE_PRIVATE)) {
            walletProto.writeTo(os);
            watch.stop();
            log.info("wallet backed up to: '{}', took {}", Constants.Files.WALLET_KEY_BACKUP_PROTOBUF, watch);
        } catch (final IOException x) {
            log.error("problem writing wallet backup", x);
        }
    }

    public static Wallet restoreWalletFromAutoBackup(final Context context) {
        try (final InputStream is = context.openFileInput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF)) {
            final Wallet wallet = new WalletProtobufSerializer().readWallet(is, true, null);
            if (!wallet.isConsistent())
                throw new Error("inconsistent backup");

            BlockchainService.resetBlockchain(context);
            log.info("wallet restored from backup: '" + Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + "'");
            return wallet;
        } catch (final IOException | UnreadableWalletException x) {
            throw new Error("cannot read backup", x);
        }
    }

    public static Wallet restoreWalletFromProtobuf(final InputStream is,
            final NetworkParameters expectedNetworkParameters) throws IOException {
        try {
            final Wallet wallet = new WalletProtobufSerializer().readWallet(is, true, null);

            if (!wallet.getParams().equals(expectedNetworkParameters))
                throw new IOException("bad wallet backup network parameters: " + wallet.getParams().getId());
            if (!wallet.isConsistent())
                throw new IOException("inconsistent wallet backup");

            return wallet;
        } catch (final UnreadableWalletException x) {
            throw new IOException("unreadable wallet", x);
        }
    }

    public static boolean isPayToManyTransaction(final Transaction transaction) {
        return transaction.getOutputs().size() > 20;
    }

    public static @Nullable String uriToProvider(final Uri uri) {
        if (uri == null || !uri.getScheme().equals("content"))
            return null;
        final String host = uri.getHost();
        if ("com.google.android.apps.docs.storage".equals(host) || "com.google.android.apps.docs.storage.legacy".equals(host))
            return "Google Drive";
        if ("org.nextcloud.documents".equals(host))
            return "Nextcloud";
        if ("com.box.android.documents".equals(host))
            return "Box";
        if ("com.android.providers.downloads.documents".equals(host))
            return "internal storage";
        return null;
    }
}
