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

import de.schildbach.wallet.channels.IPaymentChannelCallbacks;
import de.schildbach.wallet.channels.IPaymentChannelClientInstance;
import de.schildbach.wallet.channels.IPaymentChannelServerInstance;

interface IPaymentChannels {
    /**
     * Creates a new payment channel connection. Does not actually open the channel, but simply
     * opens and sets up the connection through which a channel may be opened. This channel is for
     * sending money from the app to the wallet.
     */
    IPaymentChannelServerInstance createChannelToWallet(IPaymentChannelCallbacks callbacks);

    /**
     * Creates a new payment channel connection. Does not actually open the channel, but simply
     * opens and sets up the connection through which a channel may be opened. This channel is for
     * sending money from the wallet to the app.
     */
    IPaymentChannelClientInstance createChannelFromWallet(IPaymentChannelCallbacks callbacks, long requestedMaxValue, in byte[] serverId, long requestedTimeWindow);
}
