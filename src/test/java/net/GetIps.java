package net;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by lxq on 2016/12/22.
 */
public class GetIps {
    /**
     * 获取本地ip地址，有可能会有多个地址, 若有多个网卡则会搜集多个网卡的ip地址
     */
    public static Set<InetAddress> resolveLocalAddresses() {
        Set<InetAddress> addrs = new HashSet<InetAddress>();
        Enumeration<NetworkInterface> ns = null;
        try {
            ns = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            // ignored...
        }
        while (ns != null && ns.hasMoreElements()) {
            NetworkInterface n = ns.nextElement();
            Enumeration<InetAddress> is = n.getInetAddresses();
            while (is.hasMoreElements()) {
                InetAddress i = is.nextElement();
                System.out.println("all:\t" + i.getHostAddress());
                if (!i.isLoopbackAddress() && !i.isLinkLocalAddress() && !i.isMulticastAddress()
                        && !isSpecialIp(i.getHostAddress())) addrs.add(i);
            }
        }
        return addrs;
    }

    public static Set<String> resolveLocalIps() {
        Set<InetAddress> addrs = resolveLocalAddresses();
        Set<String> ret = new HashSet<String>();
        for (InetAddress addr : addrs)
            ret.add(addr.getHostAddress());
        return ret;
    }

    private static boolean isSpecialIp(String ip) {
        if (ip.contains(":")) return true;
        if (ip.startsWith("127.")) return true;
        if (ip.startsWith("169.254.")) return true;
        if (ip.equals("255.255.255.255")) return true;
        return false;
    }


    public static boolean isPortAvailable(int port) {
        try {
            ServerSocket server = new ServerSocket(port);
            System.out.println("The port is available.");
            return true;
        } catch (IOException e) {
            System.out.println("The port is occupied.");
        }
        return false;
    }

    public static void main(String[] args) {
        Set<String> addrs = resolveLocalIps();
        for (String ip : addrs) {
            System.out.println("IP:\t" + ip);
        }

        try {
            System.out.println("本机IP:\t" + InetAddress.getLocalHost().getHostAddress());
            InetAddress iaddr = InetAddress.getByName("192.168.13.191");
            System.out.println("ip->ip:\t" + iaddr.getHostAddress());

            iaddr = InetAddress.getByName("crawler-01");
            System.out.println("crawler-01:\t" + iaddr.getHostAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        System.out.println("Server" + (1+1));

        System.out.println(isPortAvailable(41594));
        System.out.println(isPortAvailable(135));
        System.out.println(isPortAvailable(8416));
    }
}
