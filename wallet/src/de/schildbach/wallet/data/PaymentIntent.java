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

package de.schildbach.wallet.data;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.common.io.BaseEncoding;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.GenericUtils;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.SendRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static androidx.core.util.Preconditions.checkArgument;

/**
 * @author Andreas Schildbach
 */
public final class PaymentIntent implements Parcelable {
    public enum Standard {
        BIP21, BIP70
    }

    public final static class Output implements Parcelable {
        public final Coin amount;
        public final Script script;

        public Output(final Coin amount, final Script script) {
            this.amount = amount;
            this.script = script;
        }

        public static Output valueOf(final PaymentProtocol.Output output)
                throws PaymentProtocolException.InvalidOutputs {
            try {
                final Script script = new Script(output.scriptData);
                return new PaymentIntent.Output(output.amount, script);
            } catch (final ScriptException x) {
                throw new PaymentProtocolException.InvalidOutputs(
                        "unparseable script in output: " + Constants.HEX.encode(output.scriptData));
            }
        }

        public boolean hasAmount() {
            return amount != null && amount.signum() != 0;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();

            builder.append(getClass().getSimpleName());
            builder.append('[');
            builder.append(hasAmount() ? amount.toPlainString() : "null");
            builder.append(',');
            final Address toAddress = WalletUtils.getToAddress(script);
            if (ScriptPattern.isP2PK(script))
                builder.append(Constants.HEX.encode(ScriptPattern.extractKeyFromP2PK(script)));
            else if (ScriptPattern.isSentToMultisig(script))
                builder.append("multisig");
            else if (toAddress != null)
                builder.append(toAddress);
            else
                builder.append("unknown");
            builder.append(']');

            return builder.toString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeSerializable(amount);

            final byte[] program = script.getProgram();
            dest.writeInt(program.length);
            dest.writeByteArray(program);
        }

        public static final Parcelable.Creator<Output> CREATOR = new Parcelable.Creator<Output>() {
            @Override
            public Output createFromParcel(final Parcel in) {
                return new Output(in);
            }

            @Override
            public Output[] newArray(final int size) {
                return new Output[size];
            }
        };

        private Output(final Parcel in) {
            amount = (Coin) in.readSerializable();

            final int programLength = in.readInt();
            final byte[] program = new byte[programLength];
            in.readByteArray(program);
            script = new Script(program);
        }
    }

    @Nullable
    public final Standard standard;

    @Nullable
    public final String payeeName;

    @Nullable
    public final String payeeVerifiedBy;

    @Nullable
    public final Output[] outputs;

    @Nullable
    public final String memo;

    @Nullable
    public final String paymentUrl;

    @Nullable
    public final byte[] payeeData;

    @Nullable
    public final String paymentRequestUrl;

    @Nullable
    public final byte[] paymentRequestHash;

    private static final Logger log = LoggerFactory.getLogger(PaymentIntent.class);

    public PaymentIntent(@Nullable final Standard standard, @Nullable final String payeeName,
            @Nullable final String payeeVerifiedBy, @Nullable final Output[] outputs, @Nullable final String memo,
            @Nullable final String paymentUrl, @Nullable final byte[] payeeData,
            @Nullable final String paymentRequestUrl, @Nullable final byte[] paymentRequestHash) {
        this.standard = standard;
        this.payeeName = payeeName;
        this.payeeVerifiedBy = payeeVerifiedBy;
        this.outputs = outputs;
        this.memo = memo;
        this.paymentUrl = paymentUrl;
        this.payeeData = payeeData;
        this.paymentRequestUrl = paymentRequestUrl;
        this.paymentRequestHash = paymentRequestHash;
    }

    private PaymentIntent(final Address address, @Nullable final String addressLabel) {
        this(null, null, null, buildSimplePayTo(Coin.ZERO, address), addressLabel, null, null, null, null);
    }

    public static PaymentIntent blank() {
        return new PaymentIntent(null, null, null, null, null, null, null, null, null);
    }

    public static PaymentIntent fromAddress(final Address address, @Nullable final String addressLabel) {
        return new PaymentIntent(address, addressLabel);
    }

    public static PaymentIntent fromAddress(final String address, @Nullable final String addressLabel)
            throws AddressFormatException {
        return new PaymentIntent(Address.fromString(Constants.NETWORK_PARAMETERS, address), addressLabel);
    }

    public static PaymentIntent from(final String address, @Nullable final String addressLabel,
            @Nullable final Coin amount) throws AddressFormatException {
        return new PaymentIntent(null, null, null,
                buildSimplePayTo(amount, Address.fromString(Constants.NETWORK_PARAMETERS, address)), addressLabel, null,
                null, null, null);
    }

    public static PaymentIntent fromBitcoinUri(final BitcoinURI bitcoinUri) {
        final Address address = bitcoinUri.getAddress();
        final Output[] outputs = address != null ? buildSimplePayTo(bitcoinUri.getAmount(), address) : null;
        final String bluetoothMac = (String) bitcoinUri.getParameterByName(Bluetooth.MAC_URI_PARAM);
        final String paymentRequestHashStr = (String) bitcoinUri.getParameterByName("h");
        final byte[] paymentRequestHash = paymentRequestHashStr != null ? base64UrlDecode(paymentRequestHashStr) : null;

        return new PaymentIntent(PaymentIntent.Standard.BIP21, null, null, outputs, bitcoinUri.getLabel(),
                bluetoothMac != null ? "bt:" + bluetoothMac : null, null, bitcoinUri.getPaymentRequestUrl(),
                paymentRequestHash);
    }

    private static final BaseEncoding BASE64URL = BaseEncoding.base64Url().omitPadding();

    private static byte[] base64UrlDecode(final String encoded) {
        try {
            return BASE64URL.decode(encoded);
        } catch (final IllegalArgumentException x) {
            log.info("cannot base64url-decode: " + encoded);
            return null;
        }
    }

    public PaymentIntent mergeWithEditedValues(@Nullable final Coin editedAmount,
            @Nullable final Address editedAddress) {
        final Output[] outputs;

        if (hasOutputs()) {
            if (mayEditAmount()) {
                checkArgument(editedAmount != null);

                // put all coins on first output, skip the others
                outputs = new Output[] { new Output(editedAmount, this.outputs[0].script) };
            } else {
                // exact copy of outputs
                outputs = this.outputs;
            }
        } else {
            checkArgument(editedAmount != null);
            checkArgument(editedAddress != null);

            // custom output
            outputs = buildSimplePayTo(editedAmount, editedAddress);
        }

        return new PaymentIntent(standard, payeeName, payeeVerifiedBy, outputs, memo, null, payeeData, null, null);
    }

    public SendRequest toSendRequest() {
        final Transaction transaction = new Transaction(Constants.NETWORK_PARAMETERS);
        for (final PaymentIntent.Output output : outputs)
            transaction.addOutput(output.amount, output.script);
        return SendRequest.forTx(transaction);
    }

    private static Output[] buildSimplePayTo(final Coin amount, final Address address) {
        return new Output[] { new Output(amount, ScriptBuilder.createOutputScript(address)) };
    }

    public boolean hasPayee() {
        return payeeName != null;
    }

    public boolean hasOutputs() {
        return outputs != null && outputs.length > 0;
    }

    public boolean hasAddress() {
        return getAddress() != null;
    }

    public Address getAddress() {
        if (outputs == null || outputs.length != 1)
            throw new IllegalStateException();

        final Script script = outputs[0].script;
        final Address address = WalletUtils.getToAddress(script);
        if (address == null)
            throw new IllegalStateException();

        return address;
    }

    public boolean mayEditAddress() {
        return standard == null;
    }

    public boolean hasAmount() {
        if (hasOutputs())
            for (final Output output : outputs)
                if (output.hasAmount())
                    return true;

        return false;
    }

    public Coin getAmount() {
        Coin amount = Coin.ZERO;

        if (hasOutputs())
            for (final Output output : outputs)
                if (output.hasAmount())
                    amount = amount.add(output.amount);

        if (amount.signum() != 0)
            return amount;
        else
            return null;
    }

    public boolean mayEditAmount() {
        return !(standard == Standard.BIP70 && hasAmount());
    }

    public boolean hasPaymentUrl() {
        return paymentUrl != null;
    }

    public boolean isSupportedPaymentUrl() {
        return isHttpPaymentUrl() || isBluetoothPaymentUrl();
    }

    public boolean isHttpPaymentUrl() {
        return paymentUrl != null && (GenericUtils.startsWithIgnoreCase(paymentUrl, "http:")
                || GenericUtils.startsWithIgnoreCase(paymentUrl, "https:"));
    }

    public boolean isBluetoothPaymentUrl() {
        return Bluetooth.isBluetoothUrl(paymentUrl);
    }

    public boolean hasPaymentRequestUrl() {
        return paymentRequestUrl != null;
    }

    public boolean isSupportedPaymentRequestUrl() {
        return isHttpPaymentRequestUrl() || isBluetoothPaymentRequestUrl();
    }

    public boolean isHttpPaymentRequestUrl() {
        return paymentRequestUrl != null && (GenericUtils.startsWithIgnoreCase(paymentRequestUrl, "http:")
                || GenericUtils.startsWithIgnoreCase(paymentRequestUrl, "https:"));
    }

    public boolean isBluetoothPaymentRequestUrl() {
        return Bluetooth.isBluetoothUrl(paymentRequestUrl);
    }

    /**
     * Check if given payment intent is only extending on <i>this</i> one, that is it does not alter any of
     * the fields. Address and amount fields must be equal, respectively (non-existence included).
     * 
     * Alternatively, a BIP21+BIP72 request can provide a hash of the BIP70 request.
     * 
     * @param other
     *            payment intent that is checked if it extends this one
     * @return true if it extends
     */
    public boolean isExtendedBy(final PaymentIntent other) {
        // shortcut via hash
        if (standard == Standard.BIP21 && other.standard == Standard.BIP70)
            if (paymentRequestHash != null && Arrays.equals(paymentRequestHash, other.paymentRequestHash))
                return true;

        // TODO memo
        return equalsAmount(other) && equalsAddress(other);
    }

    public boolean equalsAmount(final PaymentIntent other) {
        final boolean hasAmount = hasAmount();
        if (hasAmount != other.hasAmount())
            return false;
        if (hasAmount && !getAmount().equals(other.getAmount()))
            return false;
        return true;
    }

    public boolean equalsAddress(final PaymentIntent other) {
        final boolean hasAddress = hasAddress();
        if (hasAddress != other.hasAddress())
            return false;
        if (hasAddress && !getAddress().equals(other.getAddress()))
            return false;
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append(getClass().getSimpleName());
        builder.append('[');
        builder.append(standard);
        builder.append(',');
        if (hasPayee()) {
            builder.append(payeeName);
            if (payeeVerifiedBy != null)
                builder.append("/").append(payeeVerifiedBy);
            builder.append(',');
        }
        builder.append(hasOutputs() ? Arrays.toString(outputs) : "null");
        builder.append(',');
        builder.append(paymentUrl);
        if (payeeData != null) {
            builder.append(",payeeData=");
            builder.append(Constants.HEX.encode(payeeData));
        }
        if (paymentRequestUrl != null) {
            builder.append(",paymentRequestUrl=");
            builder.append(paymentRequestUrl);
        }
        if (paymentRequestHash != null) {
            builder.append(",paymentRequestHash=");
            builder.append(Constants.HEX.encode(paymentRequestHash));
        }
        builder.append(']');

        return builder.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeSerializable(standard);

        dest.writeString(payeeName);
        dest.writeString(payeeVerifiedBy);

        if (outputs != null) {
            dest.writeInt(outputs.length);
            dest.writeTypedArray(outputs, 0);
        } else {
            dest.writeInt(0);
        }

        dest.writeString(memo);

        dest.writeString(paymentUrl);

        if (payeeData != null) {
            dest.writeInt(payeeData.length);
            dest.writeByteArray(payeeData);
        } else {
            dest.writeInt(0);
        }

        dest.writeString(paymentRequestUrl);

        if (paymentRequestHash != null) {
            dest.writeInt(paymentRequestHash.length);
            dest.writeByteArray(paymentRequestHash);
        } else {
            dest.writeInt(0);
        }
    }

    public static final Parcelable.Creator<PaymentIntent> CREATOR = new Parcelable.Creator<PaymentIntent>() {
        @Override
        public PaymentIntent createFromParcel(final Parcel in) {
            return new PaymentIntent(in);
        }

        @Override
        public PaymentIntent[] newArray(final int size) {
            return new PaymentIntent[size];
        }
    };

    private PaymentIntent(final Parcel in) {
        standard = (Standard) in.readSerializable();

        payeeName = in.readString();
        payeeVerifiedBy = in.readString();

        final int outputsLength = in.readInt();
        if (outputsLength > 0) {
            outputs = new Output[outputsLength];
            in.readTypedArray(outputs, Output.CREATOR);
        } else {
            outputs = null;
        }

        memo = in.readString();

        paymentUrl = in.readString();

        final int payeeDataLength = in.readInt();
        if (payeeDataLength > 0) {
            payeeData = new byte[payeeDataLength];
            in.readByteArray(payeeData);
        } else {
            payeeData = null;
        }

        paymentRequestUrl = in.readString();

        final int paymentRequestHashLength = in.readInt();
        if (paymentRequestHashLength > 0) {
            paymentRequestHash = new byte[paymentRequestHashLength];
            in.readByteArray(paymentRequestHash);
        } else {
            paymentRequestHash = null;
        }
    }
}
