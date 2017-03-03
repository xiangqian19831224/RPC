package net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class User {
    private int callId;
    private String name;

    public User read(DataInputStream dis) throws IOException {
        this.callId = dis.readInt();
        this.name = dis.readUTF();
        return this;
    }

    public void write(DataOutputStream dos) throws IOException {
        dos.writeInt(8888);
        dos.writeUTF(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}