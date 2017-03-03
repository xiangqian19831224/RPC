package ipc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Hashtable;

/**
 * Created by lxq on 2016/12/13.
 */
public class TestHashTable {
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
    public static void main(String[] args){
        Hashtable<ConnectionId, String> ht = new Hashtable<ConnectionId, String>();
        InetSocketAddress isa1 = new InetSocketAddress("192.168.13.194",1111);
        InetSocketAddress isa2 = new InetSocketAddress("192.168.13.194",1111);
        final ConnectionId cID1 = new ConnectionId(isa1);
        final ConnectionId cID2 = new ConnectionId(isa1);;

        ht.put(cID1,"id-1");
        ht.put(cID2, "id-2");

        System.out.println(ht.get(cID1));
        System.out.println(ht.get(cID2));

        if(ht.containsKey(cID1)){
            System.out.println("ID1:" + ht.get(cID1));
        }
    }
}
