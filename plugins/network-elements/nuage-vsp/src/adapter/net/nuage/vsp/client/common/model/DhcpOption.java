package net.nuage.vsp.client.common.model;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;

import com.google.common.net.InetAddresses;
import com.google.common.primitives.Bytes;

public class DhcpOption {
    String code;
    String length;
    String value;

    public DhcpOption(int code, byte b) {
        setCode(code);
        byte[] value = new byte[1];
        value[0] = b;
        setValue(value);
    }

    public DhcpOption(int code, String value) {
        setCode(code);
        setValue(value.getBytes(Charset.forName("UTF-8")));
    }

    public DhcpOption(int code, List<String> ipAdresses) {
        setCode(code);

        LinkedList<Byte> bytes = new LinkedList<Byte>();

        for (String ip : ipAdresses) {
            bytes.addAll(Arrays.asList(ArrayUtils.toObject(InetAddresses.forString(ip).getAddress())));
        }
        setValue(Bytes.toArray(bytes));
    }

    public String getCode() {
        return code;
    }

    public String getLength() {
        return length;
    }

    public String getValue() {
        return value;
    }

    private void setValue(byte[] value) {
        this.length = String.format("%02x", value.length);
        Formatter formatter = new Formatter();
        for (byte b : value) {
            formatter.format("%02x", b);
        }
        this.value = formatter.toString();
    }

    private void setCode(int code) {
        this.code = String.format("%02x", code);
    }


}