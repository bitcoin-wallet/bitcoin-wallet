/*
 * Copyright 2013 Google Inc.
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

package org.bitcoin;

import org.bitcoin.IChannelCallback;

interface IChannelRemoteService {
    /**
     * Checks if the calling process has permissions to spend the users money. If no such permission has been given
     * returns an intent that should be invoked by the calling app in order to show the permission granting UI.
     * Otherwise returns null.
     *
     * If you ran out of money that the user granted you and you're not in the foreground at the time, then this
     * will once again return an intent to let you request more. However you should consider popping up a notification
     * rather than just invoking the intent immediately, it's more polite that way.
     *
     * @param How much of the users money you would like to be able to spend until the next auth request.
     */
    Intent prepare(long minValue);

    /**
     * Requests that a new channel be opened to the given hostId. In the case of an error, channelOpenFailed may be
     * called before this returns. If the request is entirely invalid, null is returned. Otherwise returns a cookie that
     * can be used with payServer. Note that {@link IChannelCallback.sendProtobuf(byte[])} events will be generated
     * before this method returns.
     *
     * @param listener The callback listener which will be called as the channel's state changes
     * @param hostId Some unique Id identifying the host. It is generally safe to just use the host+port.
     * @return an opaque string that acts as a cookie, usable with payServer and closeConnection.
     */
    String openConnection(IChannelCallback listener, String hostId);

    /**
     * Call this method when a new protobuf has been received from the server. Note that callbacks may be generated
     * before this method returns.
     */
    void messageReceived(String cookie, in byte[] protobuf);

    /**
     * Pays the server the given amount.
     *
     * @return A constant from {@link org.bitcoin.ChannelConstants} or the value remaining in this channel if the send
     *         failed because the value was either too small (and ran afoul of fee rules) or was larger than the value
     *         remaining in this channel.
     */
    long payServer(String cookie, long amount);

    /**
     * Closes the given channel, future calls to this id will return {@link org.bitcoin.ChannelConstants#NO_SUCH_CHANNEL}.
     * The server will be asked to broadcast the last contract so the unspent money will come back into our wallet.
     */
    void closeConnection(String cookie);

    /**
     * Call this before doing an unbind(). It marks the given channel as inactive, so they will be resumed if you do
     * another openConnection on them. If you do openConnection() with the same hostId twice without a
     * disconnectFromWallet() first, you will end up trying to open a duplicate channel and that likely won't be
     * affordable. If the connection to the server dies, call this to enable the state to be resumed.
     */
    void disconnectFromWallet(String cookie);
}
