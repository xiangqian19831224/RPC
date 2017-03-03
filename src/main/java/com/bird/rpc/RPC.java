package com.bird.rpc;

import com.bird.Serialization.RpcRequest;
import com.bird.Serialization.RpcResponse;
import com.bird.ipc.Client;
import com.bird.ipc.Server;
import com.bird.utils.PropertyUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by lxq on 2016/12/22.
 */
public class RPC {
    private final static Logger log = Logger.getLogger(RPC.class);
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private Server server = null;//每个引擎都是一个服务分片
    private List<Client> clients = null;//每个引擎都需要调用其他分片的接口

    public RPC() throws IOException {
        //获取本机ip信息和端口信息
        String selfIP = null;
        int selfPort = -1;
        int selfSegmentCount = -1;
        selfIP = PropertyUtils.getProValu("SelfIP").trim();
        selfPort = Integer.parseInt(PropertyUtils.getProValu("SelfPort").trim());
        if (!isPortAvailable(selfPort)) {
            log.error("The port is occupied.");
            return;
        }

        //获取配置文件信息
        int serverCount = Integer.parseInt(PropertyUtils.getProValu("ServerCount").trim());
        int fragmentationCount = Integer.parseInt(PropertyUtils.getProValu("FragmentationCount").trim());
        if (serverCount <= 0 || fragmentationCount <= 0 || fragmentationCount > serverCount) {
            log.error("there are errors in application.properties,it's about serverCount and fragmentationCount");
            return;
        }

        String[] serverIPs = new String[serverCount];
        int[] serverPorts = new int[serverCount];
        try {
            for (int i = 0; i < serverIPs.length; i++) {
                String serverName = "Server" + i;
                String serverPort = "Port" + i;
                String hostnameOrIP = PropertyUtils.getProValu(serverName).trim();
                serverIPs[i] = InetAddress.getByName(hostnameOrIP).getHostAddress();
                serverPorts[i] = Integer.parseInt(PropertyUtils.getProValu(serverPort).trim());
                if (serverIPs[i].equals(selfIP) && serverPorts[i] == selfPort) {
                    if (selfSegmentCount != -1) {
                        log.error("Cannot repeat the same ip-port");
                        return;
                    }

                    selfSegmentCount = i % fragmentationCount;
                }
            }
        } catch (UnknownHostException e) {
            log.error("there are errors in application.properties,it's about serverIp or serverPort", e);
            return;
        }

        //初始化Server和Clients
        int readerCount = Integer.parseInt(PropertyUtils.getProValu("ReaderCount").trim());
        int handlerCount = Integer.parseInt(PropertyUtils.getProValu("HandlerCount").trim());
        server = new Server(selfIP, selfPort, handlerCount, readerCount);
        server.start();

        int sendThreadCount = Integer.parseInt(PropertyUtils.getProValu("SendThreadCount").trim());
        int connectionTimeout = Integer.parseInt(PropertyUtils.getProValu("ConnectionTimeout").trim());
        int rpcTimeout = Integer.parseInt(PropertyUtils.getProValu("RpcTimeout").trim());
        clients = new ArrayList<Client>();
        for (int i = 0; i < fragmentationCount; i++) {
//            if (selfSegmentCount == i) {
//                continue;
//            }

            List<InetSocketAddress> rpcServers = new ArrayList<InetSocketAddress>();
            //初始化各个
            for (int j = i; j < serverCount; j += fragmentationCount) {
                InetSocketAddress rpcServer = new InetSocketAddress(serverIPs[j], serverPorts[j]);
                rpcServers.add(rpcServer);
            }

            Client client = new Client((short) i, rpcServers, sendThreadCount, connectionTimeout, rpcTimeout);
            clients.add(client);
        }
    }

    private boolean isPortAvailable(int port) {
        try {
            ServerSocket server = new ServerSocket(port);
            server.close();
            return true;
        } catch (IOException e) {
            log.error("The port is occupied.", e);
        }
        return false;
    }

    public String rpcCall(Object obj) throws InterruptedException, ExecutionException {
        List<RpcCallTask> rpcTasks = new ArrayList<RpcCallTask>();
        for (Client client : clients) {
            RpcCallTask rct = new RpcCallTask(client, obj);
            rpcTasks.add(rct);
        }

        List<Future<String>> rpcInvokes = executorService.invokeAll(rpcTasks);
        List<String> rpcResults = new ArrayList<String>();
        for (Future<String> future : rpcInvokes) {
            String rpcResult = future.get();
            if (rpcResult == null) {
                rpcResults.add("null");
                continue;
            }
            rpcResults.add(rpcResult);
        }

        return mergeRpcResults(rpcResults, obj);
    }

    private String mergeRpcResults(List<String> rpcResults, Object obj) {
        //*********************
        String result = "";
        for (String rpcRes : rpcResults) {
            result += rpcRes;
        }

        return result;
    }


    static class RpcCallTask implements Callable<String> {
        private Client client = null;
        private Object obj = null;

        public RpcCallTask(Client client, Object obj) {
            this.client = client;
            this.obj = obj;
        }

        @Override
        public String call() {
            //*********************
            RpcRequest rpcRequest = (RpcRequest) obj;
            RpcResponse response = null;
            response = (RpcResponse) client.call(rpcRequest);

            if (response == null) {
                return "null";
            }

            return response.getResponse();
        }
    }

    public static void main(String[] args) throws IOException {
        RPC rpc = new RPC();
        RpcRequest rrt = new RpcRequest();
        rrt.setRequest("Request");

        try {
            while (true) {
                String rpcStr = rpc.rpcCall(rrt);
                log.info(rpcStr);
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}
