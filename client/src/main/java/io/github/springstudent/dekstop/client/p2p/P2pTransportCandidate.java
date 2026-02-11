package io.github.springstudent.dekstop.client.p2p;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public class P2pTransportCandidate {

    private final String host;
    private final int port;

    public P2pTransportCandidate(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    public static P2pTransportCandidate parse(String value) {
        if (value == null) {
            return null;
        }
        int idx = value.lastIndexOf(':');
        if (idx < 0 || idx == value.length() - 1) {
            return null;
        }
        try {
            return new P2pTransportCandidate(value.substring(0, idx), Integer.parseInt(value.substring(idx + 1)));
        } catch (Exception e) {
            return null;
        }
    }
}
