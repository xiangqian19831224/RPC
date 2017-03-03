package ipc;

import com.bird.Serialization.RpcResponse;

import java.io.*;

/**
 * Created by lxq on 2016/12/12.
 */
public class TestByteArrayOutputStream {

    public static void main(String[] args) throws IOException {
        int len = 4;
        ByteArrayOutputStream buf = new ByteArrayOutputStream(len);
        DataOutputStream out = new DataOutputStream(buf);
        out.writeInt(128);
        out.writeInt(129);

        byte[] bytes = buf.toByteArray();
        for(int i=0; i<8; i++) {
            System.out.println(bytes[i]);
        }
    }
}
