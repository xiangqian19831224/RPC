package com.bird.ipc;

import com.bird.Serialization.RpcRequest;
import com.bird.Serialization.RpcResponse;
import com.bird.Serialization.Writable;
import com.bird.net.NetUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by lxq on 2016/11/28.
 */
public class Server {
    private static final Logger LOG = Logger.getLogger(Server.class);
    private Listener listener = null;
    private Responder responder = null;
    private Handler[] handlers = null;

    private String bindAddress;
    private int port;
    private CallQueueManager<Call> callQueue;//Handler线程处理
    private int handlerCount;
    private int maxQueueSize = 1000;
    private String prefix = "callqueue";

    @SuppressWarnings("unchecked")
    public Server(String bindAddress, int port, int handlerCount, int readerCount) throws IOException {
        this.bindAddress = bindAddress;
        this.port = port;
        this.handlerCount = handlerCount;
        this.readerCount = readerCount;
        this.callQueue = new CallQueueManager<Call>(getQueueClass(),
                maxQueueSize, prefix);

        // Start the listener here and let it bind to the port
        listener = new Listener();
        this.port = listener.getAddress().getPort();

        // Create the responder here
        responder = new Responder();
    }

    static Class<? extends BlockingQueue<Call>> getQueueClass() {
        Class<?> queueClass = LinkedBlockingQueue.class;
        return CallQueueManager.convertQueueClass(queueClass, Call.class);
    }

    public static void bind(ServerSocket socket, InetSocketAddress address) throws IOException {
        socket.bind(address);
    }

    /**
     * Starts the service.  Must be called before any calls will be handled.
     */
    public synchronized void start() {
        responder.start();
        listener.start();
        handlers = new Handler[handlerCount];

        for (int i = 0; i < handlerCount; i++) {
            handlers[i] = new Handler(i);
            handlers[i].start();
        }
    }

    /**
     * Stops the service.  No new calls will be handled after this is called.
     */
    public synchronized void stop() {
        LOG.info("Stopping server on " + port);
        running = false;
        if (handlers != null) {
            for (int i = 0; i < handlerCount; i++) {
                if (handlers[i] != null) {
                    handlers[i].interrupt();
                }
            }
        }
        listener.interrupt();
        listener.doStop();
        responder.interrupt();
        notifyAll();
    }

    /**
     * A call queued for handling.
     */
    public static class Call {
        private final int callId;             // the client's call id
        private final Connection connection;  // connection to client
        private ByteBuffer rpcResponse;       // the response for this call
        private Writable rpcRequest;

        public Call(int id, Connection connection, Writable rpcRequest) {
            this.callId = id;
            this.connection = connection;
            this.rpcRequest = rpcRequest;
        }

        public void setRpcResponse(byte[] rpcResponse) {
            this.rpcResponse = ByteBuffer.wrap(rpcResponse);
        }
    }


    private static int NIO_BUFFER_LIMIT = 8 * 1024; //should not be more than 64KB.

    /**
     * Only one of readCh or writeCh should be non-null.
     */
    private static int channelIO(ReadableByteChannel readCh,
                                 WritableByteChannel writeCh,
                                 ByteBuffer buf) throws IOException {

        int originalLimit = buf.limit();
        int initialRemaining = buf.remaining();
        int ret = 0;

        while (buf.remaining() > 0) {
            try {
                int ioSize = Math.min(buf.remaining(), NIO_BUFFER_LIMIT);
                buf.limit(buf.position() + ioSize);
                ret = (readCh == null) ? writeCh.write(buf) : readCh.read(buf);
                if (ret < ioSize) {
                    break;
                }
            } finally {
                buf.limit(originalLimit);
            }
        }

        int nBytes = initialRemaining - buf.remaining();
        return (nBytes > 0) ? nBytes : ret;
    }

    /**
     * Reads calls from a connection and queues them for handling.
     */
    public class Connection {
        private SocketChannel channel;
        private LinkedList<Call> responseQueue;

        public Connection(SocketChannel channel) {
            this.channel = channel;
            this.responseQueue = new LinkedList<Call>();
        }

        public int readAndProcess() throws IOException {
            while (true) {
                int callId = 0;
                //步骤一：读取请求信息
                InputStream inputStream = NetUtils.getInputStream(channel);
                DataInputStream in = new DataInputStream(inputStream);
                callId = in.readInt();
                RpcRequest rpcRequest = new RpcRequest();
                rpcRequest.readFields(in);

                if (LOG.isDebugEnabled()) {
                    LOG.info("CallID-Request:  (" + callId + "," + rpcRequest.getRequest() + ")");
                }

                //步骤二：请求存入处理队列中
                processOneRpc(callId, rpcRequest);

                return 0;
            }
        }

        /**
         * Process an RPC Request - handle connection setup and decoding of
         * request into a Call
         */
        private void processOneRpc(int callId, RpcRequest rpcRequest) throws IOException {
            Call call = new Call(callId, this, rpcRequest);
            try {
                callQueue.put(call);
            } catch (InterruptedException e) {
                LOG.error("callqueue for handling is full", e);
            }
        }
    }


    private static final ThreadLocal<Server> SERVER = new ThreadLocal<Server>();
    private int readerPendingConnectionQueue = 100;//每个reader处理队列链接数
    volatile private boolean running = true;// true while server runs
    private int readerCount = 20;// number of read threads

    /**
     * Handles queued calls .
     */
    private class Handler extends Thread {
        public Handler(int instanceNumber) {
            this.setDaemon(true);
            this.setName("IPC Server handler " + instanceNumber + " on " + port);
        }

        @Override
        public void run() {
            LOG.debug(Thread.currentThread().getName() + ": starting");
            SERVER.set(Server.this);
            while (running) {
                try {
                    // 步骤一：获取访问请求
                    final Call call = callQueue.take(); // pop the queue; maybe blocked here
                    if (!call.connection.channel.isOpen()) {
                        LOG.info(Thread.currentThread().getName() + ": skipped " + call);
                        continue;
                    }

                    //步骤二：处理请求
                    processOneCommand(call);

                    // 步骤三： 处理访问请求并返回
                    responder.doRespond(call);
                } catch (Exception e) {
                    LOG.error(Thread.currentThread().getName() + " caught an exception", e);
                }
            }
            LOG.debug(Thread.currentThread().getName() + ": exiting");
        }
    }

    // Sends responses of RPC back to clients.1
    private int channelWrite(WritableByteChannel channel,
                             ByteBuffer buffer) throws IOException {
        int count = (buffer.remaining() <= NIO_BUFFER_LIMIT) ?
                channel.write(buffer) : channelIO(null, channel, buffer);
        return count;
    }

    private class Responder extends Thread {
        private final Selector writeSelector;
        private int pending;         // connections waiting to register

        final static int PURGE_INTERVAL = 900000; // 15mins

        Responder() throws IOException {
            this.setName("IPC Server Responder");
            this.setDaemon(true);
            writeSelector = Selector.open(); // create a selector
            pending = 0;
        }

        @Override
        public void run() {
            LOG.info(Thread.currentThread().getName() + ": starting");
            SERVER.set(Server.this);
            try {
                doRunLoop();
            } finally {
                LOG.info("Stopping " + Thread.currentThread().getName());
                try {
                    writeSelector.close();
                } catch (IOException ioe) {
                    LOG.error("Couldn't close write selector in " + Thread.currentThread().getName(), ioe);
                }
            }
        }

        private void doRunLoop() {
            while (running) {
                try {
                    waitPending();     // If a channel is being registered, wait.
                    writeSelector.select(PURGE_INTERVAL);
                    Iterator<SelectionKey> iter = writeSelector.selectedKeys().iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();
                        try {
                            if (key.isValid() && key.isWritable()) {
                                doAsyncWrite(key);
                            }
                        } catch (IOException e) {
                            LOG.info(Thread.currentThread().getName() + ": doAsyncWrite threw exception " + e);
                        }
                    }
                } catch (OutOfMemoryError e) {
                    //
                    // we can run out of memory if we have too many threads
                    // log the event and sleep for a minute and give
                    // some thread(s) a chance to finish
                    //
                    LOG.warn("Out of Memory in server select", e);
                    try {
                        Thread.sleep(60000);
                    } catch (Exception ie) {
                    }
                } catch (Exception e) {
                    LOG.warn("Exception in Responder", e);
                }
            }
        }

        private void doAsyncWrite(SelectionKey key) throws IOException {
            Call call = (Call) key.attachment();
            if (call == null) {
                return;
            }
            if (key.channel() != call.connection.channel) {
                throw new IOException("doAsyncWrite: bad channel");
            }

            synchronized (call.connection.responseQueue) {
                if (processResponse(call.connection.responseQueue, false)) {
                    try {
                        key.interestOps(0);
                    } catch (CancelledKeyException e) {
                        /* The Listener/reader might have closed the socket.
                         * We don't explicitly cancel the key, so not sure if this will
                         * ever fire.
                         * This warning could be removed.
                         */
                        LOG.warn("Exception while changing ops : " + e);
                    }
                }
            }
        }

        // Processes one response. Returns true if there are no more pending
        // data for this channel.
        //
        private boolean processResponse(LinkedList<Call> responseQueue,
                                        boolean inHandler) throws IOException {
            boolean error = true;
            boolean done = false;       // there is more data for this channel.
            int numElements = 0;
            Call call = null;
            try {
                synchronized (responseQueue) {
                    //
                    // If there are no items for this channel, then we are done
                    //
                    numElements = responseQueue.size();
                    if (numElements == 0) {
                        error = false;
                        return true;              // no more data for this channel.
                    }
                    //
                    // Extract the first call
                    //
                    call = responseQueue.removeFirst();
                    SocketChannel channel = call.connection.channel;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(Thread.currentThread().getName() + ": responding to " + call);
                    }
                    //
                    // Send as much data as we can in the non-blocking fashion
                    //
                    int numBytes = channelWrite(channel, call.rpcResponse);
                    if (numBytes < 0) {
                        return true;
                    }
                    if (!call.rpcResponse.hasRemaining()) {
                        //Clear out the response buffer so it can be collected
                        call.rpcResponse = null;
                        if (numElements == 1) {    // last call fully processes.
                            done = true;             // no more data for this channel.
                        } else {
                            done = false;            // more calls pending to be sent.
                        }
                    } else {
                        //
                        // If we were unable to write the entire response out, then
                        // insert in Selector queue.
                        //
                        call.connection.responseQueue.addFirst(call);

                        if (inHandler) {
                            incPending();
                            try {
                                // Wakeup the thread blocked on select, only then can the call
                                // to channel.register() complete.
                                writeSelector.wakeup();
                                channel.register(writeSelector, SelectionKey.OP_WRITE, call);
                            } catch (ClosedChannelException e) {
                                //Its ok. channel might be closed else where.
                                done = true;
                            } finally {
                                decPending();
                            }
                        }
                    }
                    error = false;              // everything went off well
                }
            } finally {
                if (error && call != null) {
                    LOG.warn(Thread.currentThread().getName() + ", call " + call + ": output error");
                }
            }
            return done;
        }

        //
        // Enqueue a response from the application.
        //
        void doRespond(Call call) throws IOException {
            synchronized (call.connection.responseQueue) {
                call.connection.responseQueue.addLast(call);
                if (call.connection.responseQueue.size() == 1) {
                    processResponse(call.connection.responseQueue, true);
                }
            }
        }


        private synchronized void incPending() {   // call waiting to be enqueued.
            pending++;
        }

        private synchronized void decPending() { // call done enqueueing.
            pending--;
            notify();
        }

        private synchronized void waitPending() throws InterruptedException {
            while (pending > 0) {
                wait();
            }
        }
    }

    /**
     * Listens on the socket. Creates jobs for the handler threads
     */
    private class Listener extends Thread {
        private ServerSocketChannel acceptChannel = null; //the accept channel
        private Selector selector = null; //the selector that we use for the server
        private Reader[] readers = null;
        private int currentReader = 0;
        private InetSocketAddress address; //the address we bind at


        public Listener() throws IOException {
            address = new InetSocketAddress(bindAddress, port);
            // Create a new server socket and set to non blocking mode
            acceptChannel = ServerSocketChannel.open();
            acceptChannel.configureBlocking(false);

            // Bind the server socket to the local host and port
            bind(acceptChannel.socket(), address);
            port = acceptChannel.socket().getLocalPort(); //Could be an ephemeral port
            // create a selector;
            selector = Selector.open();
            readers = new Reader[readerCount];
            for (int i = 0; i < readerCount; i++) {
                Reader reader = new Reader("Socket Reader #" + (i + 1) + " for port " + port);
                readers[i] = reader;
                reader.start();
            }

            // Register accepts on the server socket with the selector.
            acceptChannel.register(selector, SelectionKey.OP_ACCEPT);
            this.setName("IPC Server listener on " + port);
            this.setDaemon(true);
        }

        InetSocketAddress getAddress() {
            return (InetSocketAddress) acceptChannel.socket().getLocalSocketAddress();
        }

        private class Reader extends Thread {
            final private BlockingQueue<Connection> pendingConnections;
            private final Selector readSelector;

            Reader(String name) throws IOException {
                super(name);

                this.pendingConnections = new LinkedBlockingQueue<Connection>(readerPendingConnectionQueue);
                this.readSelector = Selector.open();
            }

            @Override
            public void run() {
                LOG.info("Starting " + Thread.currentThread().getName());
                try {
                    doRunLoop();
                } finally {
                    try {
                        readSelector.close();
                    } catch (IOException ioe) {
                        LOG.error("Error closing read selector in " + Thread.currentThread().getName(), ioe);
                    }
                }
            }

            private synchronized void doRunLoop() {
                while (running) {
                    SelectionKey key = null;
                    try {
                        // consume as many connections as currently queued to avoid
                        // unbridled acceptance of connections that starves the select
                        int size = pendingConnections.size();
                        for (int i = size; i > 0; i--) {
                            Connection conn = pendingConnections.take();
                            conn.channel.register(readSelector, SelectionKey.OP_READ, conn);
                        }
                        readSelector.select();

                        Iterator<SelectionKey> iter = readSelector.selectedKeys().iterator();
                        while (iter.hasNext()) {
                            key = iter.next();
                            iter.remove();
                            if (key.isValid()) {
                                if (key.isReadable()) {
                                    doRead(key);
                                }
                            }
                            key = null;
                        }
                    } catch (InterruptedException e) {
                        if (running) {
                            LOG.info(Thread.currentThread().getName() + " unexpectedly interrupted", e);
                        }
                    } catch (IOException ex) {
                        LOG.info("Connection closed:" + ex);
                        LOG.info("Thread:" + getName() + "\tKey:" + key);

                        //说明链接挂掉，关掉相应的通道
                        try {
                            if (key != null && key.channel() != null) {
                                key.channel().close();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            /**
             * Updating the readSelector while it's being used is not thread-safe,
             * so the connection must be queued.  The reader will drain the queue
             * and update its readSelector before performing the next select
             */
            public void addConnection(Connection conn) throws InterruptedException {
                pendingConnections.put(conn);
                readSelector.wakeup();
            }

            void shutdown() {
                assert !running;
                readSelector.wakeup();
                try {
                    join();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void run() {
            LOG.info(Thread.currentThread().getName() + ": starting");
            SERVER.set(Server.this);
            while (running) {
                SelectionKey key = null;
                try {
                    getSelector().select();
                    Iterator<SelectionKey> iter = getSelector().selectedKeys().iterator();
                    while (iter.hasNext()) {
                        key = iter.next();
                        iter.remove();
                        try {
                            if (key.isValid()) {
                                if (key.isAcceptable())
                                    doAccept(key);
                            }
                        } catch (IOException e) {
                        }
                        key = null;
                    }
                } catch (OutOfMemoryError e) {
                    // we can run out of memory if we have too many threads
                    // log the event and sleep for a minute and give
                    // some thread(s) a chance to finish
                    LOG.warn("Out of Memory in server select", e);
                    try {
                        Thread.sleep(60000);
                    } catch (Exception ie) {
                    }
                } catch (Exception e) {
                    LOG.error(e.toString());
                }
            }
            LOG.info("Stopping " + Thread.currentThread().getName());

            synchronized (this) {
                try {
                    acceptChannel.close();
                    selector.close();
                } catch (IOException e) {
                }

                selector = null;
                acceptChannel = null;
            }
        }

        void doAccept(SelectionKey key) throws InterruptedException, IOException, OutOfMemoryError {
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel channel;
            while ((channel = server.accept()) != null) {
                channel.configureBlocking(false);
                channel.socket().setTcpNoDelay(true);
                channel.socket().setKeepAlive(true);

                Reader reader = getReader();
                Connection c = new Connection(channel);
                key.attach(c);  // so closeCurrentConnection can get the object
                reader.addConnection(c);
            }
        }

        void doRead(SelectionKey key) throws IOException {
            Connection c = (Connection) key.attachment();
            if (c == null) {
                return;
            }

            c.readAndProcess();
        }

        synchronized void doStop() {
            if (selector != null) {
                selector.wakeup();
                Thread.yield();
            }
            if (acceptChannel != null) {
                try {
                    acceptChannel.socket().close();
                } catch (IOException e) {
                    LOG.info(Thread.currentThread().getName() + ":Exception in closing listener socket. " + e);
                }
            }
            for (Reader r : readers) {
                r.shutdown();
            }
        }

        synchronized Selector getSelector() {
            return selector;
        }

        // The method that will return the next reader to work with
        // Simplistic implementation of round robin for now
        Reader getReader() {
            currentReader = (currentReader + 1) % readers.length;
            return readers[currentReader];
        }
    }

    private void processOneCommand(Call call) throws IOException {
        //将来融合的时候需要填充
        String response = "{llllresult:接搜郑群}";


        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        int callId = call.callId;
        out.writeInt(callId);

        Random rd = new Random();
        int sleepTime =  rd.nextInt(20);
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        RpcResponse rpcResponse = new RpcResponse();
        rpcResponse.setResponse("CallId-" + callId + ":\t" + response + " sleep:" + sleepTime);
        rpcResponse.write(out);
        out.close();

        call.setRpcResponse(buf.toByteArray());
    }

    public static void main(String[] args) {
        String bindAddress = "192.168.13.194";
        int port = 1111;
        int handlerCount = 10;
        int readerCount = 10;
        try {
            Server server = new Server(bindAddress, port, handlerCount, readerCount);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}