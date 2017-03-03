package com.bird.Serialization;

import org.apache.log4j.Logger;

import java.io.*;

/**
 * Created by lxq on 2016/12/12.
 */
public class RpcResponse implements Writable {
    private static final Logger LOG = Logger.getLogger(RpcResponse.class);
    private byte[] responseJSON;//命令json串

    public void setResponse(String response) {
        try {
            if (response == null) {
                responseJSON = null;
                return;
            }

            responseJSON = response.getBytes("utf-8");

            return;
        } catch (UnsupportedEncodingException e) {
            LOG.error("failed convert String to byte Array", e);
        }

        responseJSON = null;
    }

    public String getResponse() {
        try {
            if (responseJSON == null) {
                return null;
            }
            return new String(responseJSON, "utf-8");
        } catch (UnsupportedEncodingException e) {
            LOG.error("failed to convert byteArray to String", e);
        }

        return null;
    }

    public int getResponseLen() {
        return responseJSON.length;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(getResponseLen());
        out.write(responseJSON);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        int len = in.readInt();
        responseJSON = new byte[len];
        in.readFully(responseJSON);
    }
}
