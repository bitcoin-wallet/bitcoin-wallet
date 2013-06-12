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

/**
 * <p>A callback listener which is called as the channel state changes</p>
 *
 * <p>In order to simplify server-side state management, each channel is assigned a unique id string, which is used in
 * requests to specify which channel the request is made for.</p>
 */
oneway interface IChannelCallback {
    /**
     * Called when the channel is successfully opened.
     * @param contractHash The Bitcoin transaction hash of the channel contract. If some kind of server identity
     *                     verification is required, the client should compare this value with the server's via some
     *                     trusted mechanism (ie over an SSL connection to the server)
     */
    void channelOpen(in byte[] contractHash);

    /** Called if the channel fails to open (connection times out, etc) */
    void channelOpenFailed();

    /** Called when the given protobuf (in the form of an encoded byte array) should be sent to the server (in order) */
    void sendProtobuf(in byte[] protobuf);

    /** Called when the connection to the server should be closed due to server request, client request, or error */
    void closeConnection();
}
