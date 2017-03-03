package com.bird.ipc;

import com.bird.Serialization.RpcRequest;
import com.bird.Serialization.RpcResponse;
import com.bird.Serialization.Writable;
import com.bird.net.NetUtils;
import org.apache.log4j.Logger;

import javax.net.SocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lxq on 2016/11/28.
 * 一个CLient多存一种分片的链接，有几个备份就做几个链接
 */
public class Client {
    private static final Logger LOG = Logger.getLogger(Client.class);
    private static final AtomicInteger callIdCounter = new AtomicInteger();
    private static int rpcTimeout = 100000;//rpc时间不能超过一秒钟
    private static int sendThreadCount = 100;//发送线程最大数

    private final short fragmentationId;//分片id
    private final int connectionTimeout;//创建链接时间 单位毫秒
    private List<InetSocketAddress> servers;//几个分片有几个访问，包括本地
    private Hashtable<ConnectionId, Connection> connections = new Hashtable<ConnectionId, Connection>();
    private AtomicBoolean running = new AtomicBoolean(true); // if client runs

    private final ExecutorService sendParamsExecutor;
    private final Random rd = new Random();//随机生成器
    private final Hashtable<Integer, Long> lastFailedServer = new Hashtable<Integer, Long>();
    private final Hashtable<Integer, Integer> failedInterval = new Hashtable<Integer, Integer>();
    private final RpcServerSelect rpcServerSelect = new RpcServerSelect();

    public Client(short fragmentationId, List<InetSocketAddress> servers, int sendThreadCount, int connectionTimeout, int rpcTimeout) {
        //步骤一：初始化成员变量
        this.fragmentationId = fragmentationId;
        this.servers = servers;
        this.sendThreadCount = sendThreadCount;
        this.connectionTimeout = connectionTimeout;
        this.rpcTimeout = rpcTimeout;

        sendParamsExecutor = Executors.newFixedThreadPool(sendThreadCount);
    }

    class RpcServerSelect {
        public int selectServer() {
            int serverNum = servers.size();
            if (serverNum == 0) {
                return -1;
            }

            //select server
            int index = rd.nextInt(serverNum);
            if (servers.size() > lastFailedServer.size() && lastFailedServer.containsKey(index)) {
                int totalCount = 2 * servers.size();
                for (int i = 0; i < totalCount; i++) {
                    long interval = System.currentTimeMillis() - lastFailedServer.get(index);
                    if (interval > failedInterval.get(index)) {
                        break;
                    }

                    index = rd.nextInt(serverNum);
                    if (!lastFailedServer.containsKey(index)) {
                        break;
                    }
                }
            }

            return index;
        }

        public void updateRpcServerState(int index, Writable response) {
            if (response == null) {
                if (lastFailedServer.containsKey(index)) {
                    int intervalTime = 2 * failedInterval.get(index);
                    intervalTime = intervalTime > 600000 ? 600000 : intervalTime;
                    lastFailedServer.put(index, System.currentTimeMillis());
                    failedInterval.put(index, intervalTime);
                } else {
                    lastFailedServer.put(index, System.currentTimeMillis());
                    failedInterval.put(index, 10000);
                }
            } else {
                if (lastFailedServer.containsKey(index)) {
                    lastFailedServer.remove(index);
                    failedInterval.remove(index);
                }
            }
        }
    }

    public Writable call(Writable param) {
        //select server
        int index = rpcServerSelect.selectServer();
        if (index < 0) {
            return null;
        }

        InetSocketAddress addr = servers.get(index);
        Writable response = null;
        try {
            ConnectionId remoteId = ConnectionId.getConnectionId(addr);
            response = call(param, remoteId);
        } catch (IOException e) {
            LOG.error("error happend", e);
        } finally {
            rpcServerSelect.updateRpcServerState(index, response);
        }

        return response;
    }

    private Writable call(Writable rpcRequest, ConnectionId remoteId) throws IOException {
        final Call call = new Call(rpcRequest);
        Connection connection = getConnection(remoteId, call);
        if (connection == null) {//won't appear
            return null;
        }

        try {
            connection.sendRpcRequest(call);                 // send the rpc request
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("interrupted waiting to send rpc request to server", e);
            throw new IOException(e);
        }

        boolean interrupted = false;
        synchronized (call) {
            while (!call.done) {
                try {
                    if (!connection.calls.containsKey(call.id)) {       //won't appear
                        return null;
                    }

                    call.wait(rpcTimeout * 2);                           // wait for the result
                    if (!call.done) {//
                        connection.calls.remove(call.id);
                        return null;
                    }
                } catch (InterruptedException ie) {
                    // save the fact that we were interrupted
                    interrupted = true;
                }
            }

            if (interrupted) {
                // set the interrupt flag now that we are done waiting
                Thread.currentThread().interrupt();
            }

            if (call.error != null) {//locat here when error happened
                call.error.fillInStackTrace();
                throw call.error;
            } else {
                return call.getRpcResponse();
            }
        }
    }

    //null: 表示没有连接还没有获取到
    private Connection getConnection(ConnectionId remoteId, Call call) {
        if (!running.get()) {
            // the client is stopped
            return null;
        }
        Connection connection;

        /* we could avoid this allocation for each RPC by having a
         * connectionsId object and with set() method. We need to manage the
         * refs for keys in HashMap properly. For now its ok.
         */
        do {
            synchronized (connections) {
                connection = connections.get(remoteId);
                if (connection != null && !connection.isAlive()) {
                    connections.remove(remoteId);
                    connection = null;
                }

                if (connection == null) {
                    connection = new Connection(remoteId);
                    connections.put(remoteId, connection);
                    try {
                        if (!connection.setupIOstreams()) {
                            connections.remove(remoteId);
                            return null;
                        }
                    } catch (IOException e) {
                        //说明服务还没有起来
                        connections.remove(remoteId);
                        return null;
                    }

                    connection.start();
                } else if (!connection.isAlive()) {
                    connections.remove(remoteId);
                }
            }
        } while (!connection.addCall(call));

        return connection;
    }

    private static class ConnectionId {
        InetSocketAddress address;

        ConnectionId(InetSocketAddress address) {
            this.address = address;
        }

        InetSocketAddress getAddress() {
            return address;
        }

        static ConnectionId getConnectionId(InetSocketAddress addr) throws IOException {
            return new ConnectionId(addr);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof ConnectionId) {
                ConnectionId that = (ConnectionId) obj;
                return isEqual(this.address, that.address);
            }
            return false;
        }

        static boolean isEqual(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }

        @Override
        public int hashCode() {
            int result = ((address == null) ? 0 : address.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return address.toString();
        }
    }


    private static class Call {
        final int id;                   // call id
        final Writable rpcRequest;      // the serialized rpc request
        Writable rpcResponse;           // null if rpc has error
        IOException error;              // exception, null if success
        boolean done;                   // true when call is done

        private Call(Writable param) {
            this.rpcRequest = param;
            int callId = nextCallId();

            this.id = callId;
        }


        public synchronized void setException(IOException error) {
            this.error = error;
            notify();
        }

        public synchronized void setRpcResponse(Writable rpcResponse) {
            this.rpcResponse = rpcResponse;
            this.done = true;
            notify();
        }

        public synchronized Writable getRpcResponse() {
            return rpcResponse;
        }

        public void write(DataOutput out) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(id);
            rpcRequest.write(dos);
            dos.close();
            out.write(baos.toByteArray());
            ((DataOutputStream) out).flush();
        }
    }


    private class Connection extends Thread {
        private InetSocketAddress server;//Server地址
        private final ConnectionId remoteId;

        private Socket socket = null;
        private DataInputStream in;
        private DataOutputStream out;

        private Hashtable<Integer, Call> calls = new Hashtable<Integer, Call>();
        private AtomicBoolean shouldCloseConnection = new AtomicBoolean();  // indicate if the connection is closed
        private IOException closeException; // close reason

        private final Object sendRpcRequestLock = new Object();

        public Connection(ConnectionId remoteId) {
            this.server = remoteId.getAddress();
            this.remoteId = remoteId;
        }

        private synchronized boolean addCall(Call call) {
            calls.put(call.id, call);
            notify();
            return true;
        }

        private void closeConnection() {
            if (socket == null) {
                return;
            }
            // close the current connection
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                LOG.warn("Not able to close a socket", e);
            }
            // set socket to null so that the next call to setupIOstreams
            // can start the process of connect all over again.
            socket = null;
        }


        public boolean setupIOstreams() throws IOException {
            if (setupConnection()) {
                InputStream inStream = NetUtils.getInputStream(socket);
                OutputStream outStream = NetUtils.getOutputStream(socket);
                this.in = new DataInputStream(new BufferedInputStream(inStream));
                this.out = new DataOutputStream(new BufferedOutputStream(outStream));
                return true;
            }

            return false;
        }

        private boolean setupConnection() {
            try {
                this.socket = SocketFactory.getDefault().createSocket();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);

                LOG.info("连接：" + server.getAddress() + ":" + server.getPort());
                NetUtils.connect(this.socket, server, connectionTimeout);

                if (rpcTimeout > 0) {
                    socket.setSoTimeout(rpcTimeout);
                }
            } catch (IOException e) {
                LOG.error("无法打开获取socket:" + server.getAddress() + ":" + server.getPort(), e);

                //处理链接错误问题
                closeConnection();
                return false;
            }

            return true;
        }

        public void receiveRpcResponse() {
            RpcResponse rpcResponse = new RpcResponse();
            Call call = null;
            try {
                //只考虑正确返回的情况
                int callId = readInt(in);
                call = calls.get(callId);

                rpcResponse.readFields(in);
                call.setRpcResponse(rpcResponse);

                if (calls.containsKey(callId)) {
                    calls.remove(callId);
                }
            } catch (IOException e) {
                if (connections.containsKey(remoteId)) {
                    Connection brokenConn = connections.get(remoteId);
                    Hashtable<Integer, Call> brokenCalls = brokenConn.calls;
                    Set<Map.Entry<Integer, Call>> brokenCallSet = brokenCalls.entrySet();
                    for (Map.Entry<Integer, Call> entry : brokenCallSet) {
                        int id = entry.getKey();
                        Call value = entry.getValue();
                        if (calls.containsKey(id)) {
                            calls.remove(id);
                        }
                        rpcResponse.setResponse(null);
                        value.setRpcResponse(rpcResponse);
                    }

                    connections.remove(remoteId);
                }


                LOG.error("Cannot receive response");
                markClosed(e);
            }
        }

        private int convertByteValue(int value) {
            if (value < 0) {
                value = 127 + (129 + value);
            }

            return value;
        }

        //错误：返回Integer.MIN_VALUE
        private int readInt(DataInput in) throws IOException {
            int byte1 = in.readByte();
            int byte2 = convertByteValue(in.readByte());
            int byte3 = convertByteValue(in.readByte());
            int byte4 = convertByteValue(in.readByte());

            int value = (byte1 << 24) + (byte2 << 16) + (byte3 << 8) + byte4;

            return value;
        }

        public void sendRpcRequest(final Call call)
                throws InterruptedException, IOException {
            if (shouldCloseConnection.get()) {
                return;
            }

            synchronized (sendRpcRequestLock) {
                Future<?> senderFuture = sendParamsExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            synchronized (Connection.this.out) {
                                if (shouldCloseConnection.get()) {
                                    return;
                                }

                                call.write(out);
                                out.flush();
                            }
                        } catch (IOException e) {
                            // exception at this point would leave the connection in an
                            // unrecoverable state (eg half a call left on the wire).
                            // So, close the connection, killing any outstanding calls
                            markClosed(e);
                        }
                    }
                });

                try {
                    senderFuture.get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();

                    // cause should only be a RuntimeException as the Runnable above
                    // catches IOException
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else {
                        throw new RuntimeException("unexpected checked exception", cause);
                    }
                }
            }
        }

        synchronized public void close() {
            try {
                out.close();
                in.close();
            } catch (IOException e) {
                LOG.error("关闭server: " + server.getAddress().getHostAddress() + " 的输入输出流失败", e);
            }

            closeConnection();
        }

        private synchronized boolean waitForWork() {
            if (calls.isEmpty() && !shouldCloseConnection.get() && running.get()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            if (!calls.isEmpty() && !shouldCloseConnection.get() && running.get()) {
                return true;
            }

            if (shouldCloseConnection.get()) {
                return false;
            }

            if (calls.isEmpty()) {
                markClosed(null);
                return false;
            }


            LOG.info("markClosed((IOException)new IOException().initCause(\n" + "new InterruptedException()))");
            // get stopped but there are still pending requests
            markClosed((IOException) new IOException().initCause(
                    new InterruptedException()));
            return false;
        }

        @Override
        public void run() {
            if (LOG.isDebugEnabled())
                LOG.debug(getName() + ": starting, having connections "
                        + connections.size());

            while (waitForWork()) {//wait here for work - read or close connection
                receiveRpcResponse();
            }


            LOG.info("close connection:\t" + server.getHostName() + ":" + server.getPort());
            close();
        }

        private synchronized void markClosed(IOException e) {
            if (shouldCloseConnection.compareAndSet(false, true)) {
                closeException = e;
                notifyAll();
            }
        }
    }

    public static int nextCallId() {
        return callIdCounter.getAndIncrement() & 0x7FFFFFFF;
    }

    public static void main(String[] args) {
        short fragmentationId = 1;

        for (int i = 0; i < 127; i++) {
            nextCallId();
        }

        String ip = "192.168.13.194";
        int port1 = 1111;
        InetSocketAddress address1 = new InetSocketAddress(ip, port1);

        int port2 = 2222;
        InetSocketAddress address2 = new InetSocketAddress(ip, port2);

        List<InetSocketAddress> servers = new ArrayList<InetSocketAddress>();
        servers.add(address1);
        servers.add(address2);

        int sendThreadCount = 50;
        int connectionTimeout = 10000;
        int rpcTimeOut = 10;
        Client client = new Client(fragmentationId, servers, sendThreadCount, connectionTimeout, rpcTimeOut);

        RpcRequest rpcRequest = new RpcRequest();
        String request = "request-";
        int count = 0;
        while (true) {
            rpcRequest.setRequest(request + count);
            RpcResponse rpcResponse = (RpcResponse) client.call(rpcRequest);
            count++;

            if (rpcResponse == null) {
                LOG.info("Request-" + count + ":\tCannot get response,maybe have no connection");
                continue;
            }
        }
    }
}