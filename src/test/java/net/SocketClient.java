package net;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketClient {

    public static void main(String...args)throws Exception{
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost",8080));
        User user = new User();
        user.setName("小明");
        ByteBuffer buffer = ByteBuffer.wrap(ByteUtil.write(user));
        socketChannel.write(buffer);
        socketChannel.socket().shutdownOutput();

        User obj = receive(socketChannel);
        System.out.println(obj.getName());
    }

    private static User receive(SocketChannel socketChannel)throws Exception{
        ByteBuffer buffer = ByteBuffer.allocate(1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int size = 0;
        byte [] bytes = null;
        while((size = socketChannel.read(buffer))>=0){
            buffer.flip();
            bytes = new byte[size];
            buffer.get(bytes);
            baos.write(bytes);
            buffer.clear();
        }
        bytes = baos.toByteArray();
        baos.close();
        return ByteUtil.read(bytes);
    }
}