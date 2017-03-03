package com.bird.Serialization;

import com.bird.ipc.Client;
import org.apache.log4j.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Created by lxq on 2016/12/9.
 */
public class RpcRequest implements Writable {
    private static final Logger LOG = Logger.getLogger(RpcRequest.class);
    private byte[] cmdJSON;//命令json串

    public void setRequest(String request) {
        try {
            cmdJSON = request.getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public String getRequest() {
        try {
            return new String(cmdJSON,"utf-8");
        } catch (UnsupportedEncodingException e) {
            LOG.error("failed to convert byte array to string",e);
        }

        return null;
    }

    public int getRequestLen() {
        return cmdJSON.length;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(getRequestLen());
        out.write(cmdJSON);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        int len = in.readInt();
        cmdJSON = new byte[len];
        in.readFully(cmdJSON);
    }
}
