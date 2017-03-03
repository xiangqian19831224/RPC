package net;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class MyServer {

    public static void main(String[] args) throws Exception{
        Selector selector = Selector.open();//打开Selector
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();//打开channel
        serverSocketChannel.configureBlocking(false);//非阻塞方式
        serverSocketChannel.socket().setReuseAddress(true);//是否已本地ip发布，相当于localhost
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));//端口
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // 注册
        while(selector.select() > 0){
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while(it.hasNext()){
                SelectionKey key = it.next();
                ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
                SocketChannel socketChannel = ssc.accept();
                User user = receive(socketChannel);
                System.out.println("name:"+user.getName());

                user.setName("小强klfdjalllllllllllllllllllllllllllllllllllll");
                send(user,socketChannel);
            }
        }
    }

    //接受数据
    private static User receive(SocketChannel socketChannel) throws Exception{
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        byte [] bytes = null;
        int size = 0;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while((size = socketChannel.read(buffer))>=0){
            buffer.flip();
            bytes = new byte[size];
            buffer.get(bytes);
            baos.write(bytes);
            buffer.clear();
        }
        bytes = baos.toByteArray();
        return ByteUtil.read(bytes);
    }

    //发送数据
    private static void send(User user,SocketChannel socketChannel) throws Exception{
        byte [] bytes = ByteUtil.write(user);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        socketChannel.write(buffer);
        socketChannel.socket().shutdownOutput();
    }
}