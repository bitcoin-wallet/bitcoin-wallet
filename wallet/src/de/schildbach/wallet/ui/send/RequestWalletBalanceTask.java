/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.ui.send;

import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet_test.R;

import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * @author Andreas Schildbach
 */
public final class RequestWalletBalanceTask {
    private final Handler backgroundHandler;
    private final Handler callbackHandler;
    private final ResultCallback resultCallback;

    private static final Logger log = LoggerFactory.getLogger(RequestWalletBalanceTask.class);

    public interface ResultCallback {
        void onResult(Collection<Transaction> transactions);

        void onFail(int messageResId, Object... messageArgs);
    }

    public RequestWalletBalanceTask(final Handler backgroundHandler, final ResultCallback resultCallback) {
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
        this.resultCallback = resultCallback;
    }

    public static class JsonRpcRequest {
        public final int id;
        public final String method;
        public final String[] params;

        private static transient int idCounter = 0;

        public JsonRpcRequest(final String method, final String[] params) {
            this.id = idCounter++;
            this.method = method;
            this.params = params;
        }
    }

    public static class JsonRpcResponse {
        public int id;
        public Utxo[] result;

        public static class Utxo {
            public String tx_hash;
            public int tx_pos;
            public long value;
            public int height;
        }
    }

    public void requestWalletBalance(final AssetManager assets, final Address address) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

                try {
                    final Map<InetSocketAddress, String> servers = loadElectrumServers(
                            assets.open(Constants.Files.ELECTRUM_SERVERS_FILENAME));
                    final Map.Entry<InetSocketAddress, String> server = new LinkedList<>(servers.entrySet())
                            .get(new Random().nextInt(servers.size()));
                    final InetSocketAddress socketAddress = server.getKey();
                    final String type = server.getValue();
                    log.info("trying to request wallet balance from {}: {}", socketAddress, address);
                    final Socket socket;
                    if (type.equalsIgnoreCase("tls")) {
                        final SocketFactory sf = SSLSocketFactory.getDefault();
                        socket = sf.createSocket(socketAddress.getHostName(), socketAddress.getPort());
                        final SSLSession sslSession = ((SSLSocket) socket).getSession();
                        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(socketAddress.getHostName(),
                                sslSession))
                            throw new SSLHandshakeException("Expected " + socketAddress.getHostName() + ", got "
                                    + sslSession.getPeerPrincipal());
                    } else if (type.equalsIgnoreCase("tcp")) {
                        socket = new Socket();
                        socket.connect(new InetSocketAddress(socketAddress.getHostName(), socketAddress.getPort()),
                                5000);
                    } else {
                        throw new IllegalStateException("Cannot handle: " + type);
                    }
                    final BufferedSink sink = Okio.buffer(Okio.sink(socket));
                    sink.timeout().timeout(5000, TimeUnit.MILLISECONDS);
                    final BufferedSource source = Okio.buffer(Okio.source(socket));
                    source.timeout().timeout(5000, TimeUnit.MILLISECONDS);
                    final Moshi moshi = new Moshi.Builder().build();
                    final JsonAdapter<JsonRpcRequest> requestAdapter = moshi.adapter(JsonRpcRequest.class);
                    final JsonRpcRequest request = new JsonRpcRequest("blockchain.address.listunspent",
                            new String[] { address.toBase58() });
                    requestAdapter.toJson(sink, request);
                    sink.writeUtf8("\n").flush();
                    final JsonAdapter<JsonRpcResponse> responseAdapter = moshi.adapter(JsonRpcResponse.class);
                    final JsonRpcResponse response = responseAdapter.fromJson(source);
                    if (response.id == request.id) {
                        final Map<Sha256Hash, Transaction> transactions = new HashMap<>();
                        for (final JsonRpcResponse.Utxo utxo : response.result) {
                            if (utxo.height > 0) {
                                final Sha256Hash utxoHash = Sha256Hash.wrap(utxo.tx_hash);
                                final int utxoIndex = utxo.tx_pos;
                                final Coin utxoValue = Coin.valueOf(utxo.value);

                                Transaction tx = transactions.get(utxoHash);
                                if (tx == null) {
                                    tx = new FakeTransaction(Constants.NETWORK_PARAMETERS, utxoHash);
                                    tx.getConfidence().setConfidenceType(ConfidenceType.BUILDING);
                                    transactions.put(utxoHash, tx);
                                }

                                final TransactionOutput output = new TransactionOutput(Constants.NETWORK_PARAMETERS, tx,
                                        utxoValue, ScriptBuilder.createOutputScript(address).getProgram());
                                if (tx.getOutputs().size() > utxoIndex) {
                                    // Work around not being able to replace outputs on transactions
                                    final List<TransactionOutput> outputs = new ArrayList<TransactionOutput>(
                                            tx.getOutputs());
                                    final TransactionOutput dummy = outputs.set(utxoIndex, output);
                                    checkState(dummy.getValue().equals(Coin.NEGATIVE_SATOSHI),
                                            "Index %s must be dummy output", utxoIndex);
                                    // Remove and re-add all outputs
                                    tx.clearOutputs();
                                    for (final TransactionOutput o : outputs)
                                        tx.addOutput(o);
                                } else {
                                    // Fill with dummies as needed
                                    while (tx.getOutputs().size() < utxoIndex)
                                        tx.addOutput(new TransactionOutput(Constants.NETWORK_PARAMETERS, tx,
                                                Coin.NEGATIVE_SATOSHI, new byte[] {}));
                                    // Add the real output
                                    tx.addOutput(output);
                                }
                            }
                        }

                        log.info("fetched {} unspent outputs from {}", response.result.length, socketAddress);
                        onResult(transactions.values());
                    } else {
                        log.info("id mismatch response:{} vs request:{}", response.id, request.id);
                        onFail(R.string.error_parse, socketAddress.toString());
                    }
                } catch (final JsonDataException x) {
                    log.info("problem parsing json", x);
                    onFail(R.string.error_parse, x.getMessage());
                } catch (final IOException x) {
                    log.info("problem querying unspent outputs", x);
                    onFail(R.string.error_io, x.getMessage());
                }
            }
        });
    }

    protected void onResult(final Collection<Transaction> transactions) {
        callbackHandler.post(new Runnable() {
            @Override
            public void run() {
                resultCallback.onResult(transactions);
            }
        });
    }

    protected void onFail(final int messageResId, final Object... messageArgs) {
        callbackHandler.post(new Runnable() {
            @Override
            public void run() {
                resultCallback.onFail(messageResId, messageArgs);
            }
        });
    }

    private static class FakeTransaction extends Transaction {
        private final Sha256Hash hash;

        public FakeTransaction(final NetworkParameters params, final Sha256Hash hash) {
            super(params);
            this.hash = hash;
        }

        @Override
        public Sha256Hash getHash() {
            return hash;
        }
    }

    private static Map<InetSocketAddress, String> loadElectrumServers(final InputStream is) throws IOException {
        final Splitter splitter = Splitter.on(':');
        final Map<InetSocketAddress, String> addresses = new HashMap<>();
        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));
            while (true) {
                line = reader.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;

                final Iterator<String> i = splitter.split(line).iterator();
                final String type = i.next();
                final String host = i.next();
                final int port;
                if (i.hasNext())
                    port = Integer.parseInt(i.next());
                else if ("tcp".equalsIgnoreCase(type))
                    port = Constants.ELECTRUM_SERVER_DEFAULT_PORT_TCP;
                else if ("tls".equalsIgnoreCase(type))
                    port = Constants.ELECTRUM_SERVER_DEFAULT_PORT_TLS;
                else
                    throw new IllegalStateException("Cannot handle: " + type);
                addresses.put(InetSocketAddress.createUnresolved(host, port), type);
            }
        } catch (final Exception x) {
            throw new RuntimeException("Error while parsing: '" + line + "'", x);
        } finally {
            if (reader != null)
                reader.close();
            is.close();
        }
        return addresses;
    }
}
