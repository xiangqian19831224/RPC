package net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ByteUtil {

    public static byte [] write(User user) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        user.write(dos);
        return baos.toByteArray();
    }


    public static User read(byte [] bytes) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        User u = new User();
        return u.read(dis);
    }
}