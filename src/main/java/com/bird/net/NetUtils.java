/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bird.net;

import org.apache.log4j.Logger;

import javax.net.SocketFactory;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class NetUtils {
    private static final Logger LOG = Logger.getLogger(NetUtils.class);
    private static Map<String, String> hostToResolved = new HashMap<String, String>();

    public static OutputStream getOutputStream(SocketChannel sc)
            throws IOException {
        return getOutputStream(sc, 0);
    }

    public static OutputStream getOutputStream(SocketChannel sc, long timeout)
            throws IOException {
        return new SocketOutputStream(sc, timeout);
    }

    public static InputStream getInputStream(SocketChannel sc) throws IOException {
        return new SocketInputStream(sc, 0);
    }

    public static OutputStream getOutputStream(Socket socket)
            throws IOException {
        return (socket.getChannel() == null) ?
                socket.getOutputStream() : new SocketOutputStream(socket, 0);
    }

    public static InputStream getInputStream(Socket socket) throws IOException {
        boolean notHasChannel = (socket.getChannel() == null);
        InputStream stm = (socket.getChannel() == null) ?
                socket.getInputStream() : new SocketInputStream(socket);

        if (!notHasChannel) {
            ((SocketInputStream) stm).setTimeout(socket.getSoTimeout());
        }
        return stm;
    }

    /**
     * @param socket
     * @param address the remote address
     * @param timeout timeout in milliseconds
     */
    public static void connect(Socket socket, SocketAddress address, int timeout) throws IOException {
        connect(socket, address, null, timeout);
    }

    /**
     * Like {@link NetUtils#connect(Socket, SocketAddress, int)} but
     * also takes a local address and port to bind the socket to.
     *
     * @param socket
     * @param endpoint  the remote address
     * @param localAddr the local address to bind the socket to
     * @param timeout   timeout in milliseconds
     */
    public static void connect(Socket socket, SocketAddress endpoint, SocketAddress localAddr, int timeout)
            throws IOException {
        if (socket == null || endpoint == null || timeout < 0) {
            throw new IllegalArgumentException("Illegal argument for connect()");
        }

        SocketChannel ch = socket.getChannel();

        if (localAddr != null) {
            socket.bind(localAddr);
        }

        if (ch == null) {
            // let the default implementation handle it.
            LOG.info("NetUtils.java: socket.connect(endpoint, timeout)，endpoint:\t" + endpoint.toString());
            socket.connect(endpoint, timeout);
        } else {
            SocketIOWithTimeout.connect(ch, endpoint, timeout);
        }
    }
}
