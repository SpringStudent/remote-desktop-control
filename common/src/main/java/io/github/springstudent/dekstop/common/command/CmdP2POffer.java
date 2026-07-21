package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlled peer sends its LAN IP addresses and listener port to the controller peer.
 * Sent via server relay after the P2P listener is started.
 *
 * @author ZhouNing
 * @date 2026/07/21
 */
public class CmdP2POffer extends Cmd {

    private List<String> addresses;
    private int port;

    public CmdP2POffer() {
    }

    public CmdP2POffer(List<String> addresses, int port) {
        this.addresses = addresses;
        this.port = port;
    }

    public List<String> getAddresses() {
        return addresses;
    }

    public int getPort() {
        return port;
    }

    @Override
    public CmdType getType() {
        return CmdType.P2P_OFFER;
    }

    @Override
    public int getWireSize() {
        int size = 4 + 4; // address count (int) + port (int)
        if (addresses != null) {
            for (String addr : addresses) {
                size += 4 + addr.length(); // length prefix (int) + UTF-8 bytes
            }
        }
        return size;
    }

    @Override
    public String toString() {
        return String.format("CmdP2POffer={addresses:%s, port:%d}", addresses, port);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        int count = addresses != null ? addresses.size() : 0;
        out.writeInt(count);
        if (addresses != null) {
            for (String addr : addresses) {
                out.writeInt(addr.length());
                out.writeCharSequence(addr, StandardCharsets.UTF_8);
            }
        }
        out.writeInt(port);
    }

    public static CmdP2POffer decode(ByteBuf in) {
        int count = in.readInt();
        List<String> addresses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int len = in.readInt();
            addresses.add(in.readCharSequence(len, StandardCharsets.UTF_8).toString());
        }
        int port = in.readInt();
        return new CmdP2POffer(addresses, port);
    }
}
