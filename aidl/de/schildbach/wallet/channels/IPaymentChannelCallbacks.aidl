/**
 * Copyright 2016 the original author or authors.
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

package de.schildbach.wallet.channels;

interface IPaymentChannelCallbacks {
    /**
     * Sends a payment channel message. This message is a valid protobuf-encoded message generated
     * by bitcoinj.
     */
    oneway void sendMessage(in byte[] message);

    /**
     * Closes this payment channel's connection - calling this function is equivalent to closing the
     * TCP stream under a TCP-transported channel.
     */
    oneway void closeConnection();
}
