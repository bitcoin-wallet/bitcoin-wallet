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

import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UninitializedMessageException;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.util.Qr;
import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.PrefixedChecksummedBytes;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.bitcoinj.crypto.TrustStoreLoader;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocol.PkiVerificationData;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * @author Andreas Schildbach
 */
public abstract class InputParser {
    private static final Logger log = LoggerFactory.getLogger(InputParser.class);

    public abstract static class StringInputParser extends InputParser {
        private final String input;

        public StringInputParser(final String input) {
            this.input = input;
        }

        @Override
        public void parse() {
            if (input.startsWith("BITCOIN:-")) {
                try {
                    final byte[] serializedPaymentRequest = Qr.decodeBinary(input.substring(9));

                    parseAndHandlePaymentRequest(serializedPaymentRequest);
                } catch (final IOException x) {
                    log.info("i/o error while fetching payment request", x);

                    error(R.string.input_parser_io_error, x.getMessage());
                } catch (final PaymentProtocolException.PkiVerificationException x) {
                    log.info("got unverifyable payment request", x);

                    error(R.string.input_parser_unverifyable_paymentrequest, x.getMessage());
                } catch (final PaymentProtocolException x) {
                    log.info("got invalid payment request", x);

                    error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
                }
            } else if (input.startsWith("bitcoin:") || input.startsWith("BITCOIN:")) {
                try {
                    final BitcoinURI bitcoinUri = new BitcoinURI(null, "bitcoin:" + input.substring(8));
                    final Address address = bitcoinUri.getAddress();
                    if (address != null && !Constants.NETWORK_PARAMETERS.equals(address.getParameters()))
                        throw new BitcoinURIParseException("mismatched network");

                    handlePaymentIntent(PaymentIntent.fromBitcoinUri(bitcoinUri));
                } catch (final BitcoinURIParseException x) {
                    log.info("got invalid bitcoin uri: '" + input + "'", x);

                    error(R.string.input_parser_invalid_bitcoin_uri, input);
                }
            } else if (PATTERN_TRANSACTION_BASE43.matcher(input).matches()) {
                try {
                    final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS,
                            Qr.decodeDecompressBinary(input));
                    handleDirectTransaction(tx);
                } catch (final IOException | ProtocolException x) {
                    log.info("got invalid transaction", x);
                    error(R.string.input_parser_invalid_transaction, x.getMessage());
                }
            } else if (PATTERN_TRANSACTION_HEX.matcher(input).matches()) {
                try {
                    final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, Constants.HEX.decode(input));
                    handleDirectTransaction(tx);
                } catch (final IllegalArgumentException | ProtocolException x) {
                    log.info("got invalid transaction", x);
                    error(R.string.input_parser_invalid_transaction, x.getMessage());
                }
            } else {
                try {
                    handlePrivateKey(DumpedPrivateKey.fromBase58(Constants.NETWORK_PARAMETERS, input));
                } catch (AddressFormatException x) {
                    try {
                        handlePrivateKey(BIP38PrivateKey.fromBase58(Constants.NETWORK_PARAMETERS, input));
                    } catch (final AddressFormatException x2) {
                        try {
                            handlePaymentIntent(PaymentIntent
                                    .fromAddress(Address.fromString(Constants.NETWORK_PARAMETERS, input), null));
                        } catch (AddressFormatException.WrongNetwork x3) {
                            log.info("detected address, but wrong network", x3);
                            error(R.string.input_parser_invalid_address);
                        } catch (AddressFormatException x3) {
                            cannotClassify(input);
                        }
                    }
                }
            }
        }

        protected void handlePrivateKey(final PrefixedChecksummedBytes key) {
            final Address address = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS,
                    ((DumpedPrivateKey) key).getKey());

            handlePaymentIntent(PaymentIntent.fromAddress(address, null));
        }
    }

    public abstract static class BinaryInputParser extends InputParser {
        private final String inputType;
        private final byte[] input;

        public BinaryInputParser(final String inputType, final byte[] input) {
            this.inputType = inputType;
            this.input = input;
        }

        @Override
        public void parse() {
            if (Constants.MIMETYPE_TRANSACTION.equals(inputType)) {
                try {
                    final Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, input);

                    handleDirectTransaction(tx);
                } catch (final VerificationException x) {
                    log.info("got invalid transaction", x);

                    error(R.string.input_parser_invalid_transaction, x.getMessage());
                }
            } else if (PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(inputType)) {
                try {
                    parseAndHandlePaymentRequest(input);
                } catch (final PaymentProtocolException.PkiVerificationException x) {
                    log.info("got unverifyable payment request", x);

                    error(R.string.input_parser_unverifyable_paymentrequest, x.getMessage());
                } catch (final PaymentProtocolException x) {
                    log.info("got invalid payment request", x);

                    error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
                }
            } else {
                cannotClassify(inputType);
            }
        }

        @Override
        protected final void handleDirectTransaction(final Transaction transaction) throws VerificationException {
            throw new UnsupportedOperationException();
        }
    }

    public abstract static class StreamInputParser extends InputParser {
        private final String inputType;
        private final InputStream is;

        public StreamInputParser(final String inputType, final InputStream is) {
            this.inputType = inputType;
            this.is = is;
        }

        @Override
        public void parse() {
            if (PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(inputType)) {
                try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ByteStreams.copy(is, baos);
                    parseAndHandlePaymentRequest(baos.toByteArray());
                } catch (final IOException x) {
                    log.info("i/o error while fetching payment request", x);

                    error(R.string.input_parser_io_error, x.getMessage());
                } catch (final PaymentProtocolException.PkiVerificationException x) {
                    log.info("got unverifyable payment request", x);

                    error(R.string.input_parser_unverifyable_paymentrequest, x.getMessage());
                } catch (final PaymentProtocolException x) {
                    log.info("got invalid payment request", x);

                    error(R.string.input_parser_invalid_paymentrequest, x.getMessage());
                } finally {
                    try {
                        is.close();
                    } catch (IOException x) {
                        x.printStackTrace();
                    }
                }
            } else {
                cannotClassify(inputType);
            }
        }

        @Override
        protected final void handleDirectTransaction(final Transaction transaction) throws VerificationException {
            throw new UnsupportedOperationException();
        }
    }

    public abstract void parse();

    protected final void parseAndHandlePaymentRequest(final byte[] serializedPaymentRequest)
            throws PaymentProtocolException {
        final PaymentIntent paymentIntent = parsePaymentRequest(serializedPaymentRequest);

        handlePaymentIntent(paymentIntent);
    }

    public static PaymentIntent parsePaymentRequest(final byte[] serializedPaymentRequest)
            throws PaymentProtocolException {
        try {
            if (serializedPaymentRequest.length > 50000)
                throw new PaymentProtocolException("payment request too big: " + serializedPaymentRequest.length);

            final Protos.PaymentRequest paymentRequest = Protos.PaymentRequest.parseFrom(serializedPaymentRequest);

            final String pkiName;
            final String pkiCaName;
            if (!"none".equals(paymentRequest.getPkiType())) {
                final KeyStore keystore = new TrustStoreLoader.DefaultTrustStoreLoader().getKeyStore();
                final PkiVerificationData verificationData = PaymentProtocol.verifyPaymentRequestPki(paymentRequest,
                        keystore);
                pkiName = verificationData.displayName;
                pkiCaName = verificationData.rootAuthorityName;
            } else {
                pkiName = null;
                pkiCaName = null;
            }

            final PaymentSession paymentSession = PaymentProtocol.parsePaymentRequest(paymentRequest);

            if (paymentSession.isExpired())
                throw new PaymentProtocolException.Expired("payment details expired: current time " + new Date()
                        + " after expiry time " + paymentSession.getExpires());

            if (!paymentSession.getNetworkParameters().equals(Constants.NETWORK_PARAMETERS))
                throw new PaymentProtocolException.InvalidNetwork(
                        "cannot handle payment request network: " + paymentSession.getNetworkParameters());

            final ArrayList<PaymentIntent.Output> outputs = new ArrayList<>(1);
            for (final PaymentProtocol.Output output : paymentSession.getOutputs())
                outputs.add(PaymentIntent.Output.valueOf(output));

            final String memo = paymentSession.getMemo();

            final String paymentUrl = paymentSession.getPaymentUrl();

            final byte[] merchantData = paymentSession.getMerchantData();

            final byte[] paymentRequestHash = Hashing.sha256().hashBytes(serializedPaymentRequest).asBytes();

            final PaymentIntent paymentIntent = new PaymentIntent(PaymentIntent.Standard.BIP70, pkiName, pkiCaName,
                    outputs.toArray(new PaymentIntent.Output[0]), memo, paymentUrl, merchantData, null,
                    paymentRequestHash);

            if (paymentIntent.hasPaymentUrl() && !paymentIntent.isSupportedPaymentUrl())
                throw new PaymentProtocolException.InvalidPaymentURL(
                        "cannot handle payment url: " + paymentIntent.paymentUrl);

            return paymentIntent;
        } catch (final InvalidProtocolBufferException | UninitializedMessageException x) {
            throw new PaymentProtocolException(x);
        } catch (final FileNotFoundException | KeyStoreException x) {
            throw new RuntimeException(x);
        }
    }

    protected abstract void handlePaymentIntent(PaymentIntent paymentIntent);

    protected abstract void handleDirectTransaction(Transaction transaction) throws VerificationException;

    protected abstract void error(int messageResId, Object... messageArgs);

    protected void cannotClassify(final String input) {
        log.info("cannot classify: '{}'", input);

        error(R.string.input_parser_cannot_classify, input);
    }

    private static final Pattern PATTERN_TRANSACTION_BASE43 = Pattern
            .compile("[0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$\\*\\+\\-\\.\\/\\:]{100,}");
    private static final Pattern PATTERN_TRANSACTION_HEX = Pattern
            .compile("[0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ]{200,}", Pattern.CASE_INSENSITIVE);
}
