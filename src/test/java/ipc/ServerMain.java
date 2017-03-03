package ipc;

import com.bird.ipc.Server;

import java.io.IOException;

/**
 * Created by lxq on 12/23/16.
 */
public class ServerMain {
    Server server;
    public ServerMain(){

    }

    public void finished(){
        String bindAddress = "192.168.13.194";
        int port = 1111;
        int handlerCount = 10;
        int readerCount = 10;
        try {
            server = new Server(bindAddress, port, handlerCount, readerCount);
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.start();

    }

    //
    public static void main(String[] args) {
        ServerMain main = new ServerMain();
        main.finished();
        System.out.println("**************************");
        System.out.println("**************************");
        System.out.println("**************************");
    }
}
