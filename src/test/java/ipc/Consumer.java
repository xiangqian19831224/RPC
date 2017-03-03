package ipc;

import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.concurrent.BlockingQueue;

public class Consumer implements Runnable{

    protected BlockingQueue queue = null;

    public Consumer(BlockingQueue queue) {
        this.queue = queue;
    }

    public void run() {
        try {
            System.out.println(queue.take());
            System.out.println(queue.take());
            System.out.println(queue.take());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Created by lxq on 2016/12/15.
     */
    public static class TestClassEqual {
        public static class ConnectionId {
            InetSocketAddress address;

            ConnectionId(InetSocketAddress address) {
                this.address = address;
            }

            @Override
            public boolean equals(Object obj){
                if(obj == this){
                    return true;
                }

                if(obj instanceof ConnectionId){
                    ConnectionId that = (ConnectionId) obj;
                    return isEqual(this.address, that.address);
                }

                return false;
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

            static boolean isEqual(Object a, Object b) {
                return a == null ? b == null : a.equals(b);
            }
        }

        public static void main(String[] args){
            Hashtable<ConnectionId, String> tHT = new Hashtable<ConnectionId, String>();
            InetSocketAddress address = new InetSocketAddress("127.0.0.1",8888);
            InetSocketAddress address2 = new InetSocketAddress("192.0.0.1",8888);
            ConnectionId cId1 = new ConnectionId(address);
            ConnectionId cId2 = new ConnectionId(address);
            ConnectionId cId3 = new ConnectionId(address2);
            tHT.put(cId1,"first");
            tHT.put(cId2,"second");
            System.out.println(tHT.get(cId1));
            System.out.println(tHT.get(cId2));
            System.out.println(tHT.get(cId3));
            tHT.put(cId3,"third");
            System.out.println(tHT.get(cId3));

        }
    }
}