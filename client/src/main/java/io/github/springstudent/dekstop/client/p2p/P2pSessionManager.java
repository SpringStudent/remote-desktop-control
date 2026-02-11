package io.github.springstudent.dekstop.client.p2p;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.common.bean.P2pSessionState;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.utils.P2pSecurityUtils;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public class P2pSessionManager {

    private static final int FALLBACK_TIMEOUT_SECONDS = 8;
    private static final String PING_PREFIX = "RDC_P2P_PING|";
    private static final String ACK_PREFIX = "RDC_P2P_ACK|";
    private static final String ICE4J_AGENT = "org.ice4j.ice.Agent";

    private final RemoteClient remoteClient;
    private final ScheduledExecutorService scheduler;
    private final P2pMetrics metrics;
    private final AtomicBoolean active = new AtomicBoolean(false);

    private volatile P2pSessionState state = P2pSessionState.RELAY;
    private volatile String sessionId;
    private volatile String token;
    private volatile long tokenExpireAt;
    private volatile DatagramSocket directSocket;
    private volatile InetSocketAddress remoteAddress;

    public P2pSessionManager(RemoteClient remoteClient) {
        this.remoteClient = remoteClient;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.metrics = new P2pMetrics();
        detectIce4j();
    }

    private void detectIce4j() {
        try {
            Class.forName(ICE4J_AGENT);
            Log.info("ice4j loaded, p2p candidate discovery enabled");
        } catch (ClassNotFoundException e) {
            Log.warn("ice4j not found, p2p still works with host candidates only");
        }
    }

    public synchronized void onTokenIssued(CmdP2pResult result) {
        sessionId = result.getSessionId();
        token = result.getToken();
        tokenExpireAt = parseExpireAt(result.getPayload());
        state = P2pSessionState.P2P_NEGOTIATING;
        metrics.markNegotiationStart();
        ensureSocket();
        sendSignedSignal(CmdType.P2pOffer, localCandidatePayload());
        scheduler.schedule(this::fallbackIfNeeded, FALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void handleSignal(Cmd cmd) {
        if (!(cmd instanceof CmdP2pSignal)) {
            return;
        }
        CmdP2pSignal signal = (CmdP2pSignal) cmd;
        if (sessionId == null || !sessionId.equals(signal.getSessionId())) {
            return;
        }
        if (cmd.getType().equals(CmdType.P2pOffer)) {
            onRemoteCandidates(signal.getPayload());
            sendSignedSignal(CmdType.P2pAnswer, localCandidatePayload());
            sendSignedSignal(CmdType.P2pResult, CmdP2pResult.NEGOTIATING);
        } else if (cmd.getType().equals(CmdType.P2pAnswer) || cmd.getType().equals(CmdType.P2pCandidate)) {
            onRemoteCandidates(signal.getPayload());
        } else if (cmd.getType().equals(CmdType.P2pResult)) {
            if (CmdP2pResult.FALLBACK.equals(signal.getPayload()) || signal.getPayload().startsWith(CmdP2pResult.FAILED)) {
                switchToFallback();
            } else if (CmdP2pResult.ACTIVE.equals(signal.getPayload())) {
                activateP2p();
            }
        }
    }

    public boolean sendDirectCmd(Cmd cmd) {
        if (!active.get() || remoteAddress == null || directSocket == null) {
            return false;
        }
        try {
            byte[] bytes = serialize(cmd);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, remoteAddress);
            directSocket.send(packet);
            return true;
        } catch (Exception e) {
            Log.warn("send direct cmd failed, fallback to relay: " + e.getMessage());
            switchToFallback();
            return false;
        }
    }

    public boolean isP2pActive() {
        return active.get();
    }

    public P2pSessionState getState() {
        return state;
    }

    public P2pMetrics getMetrics() {
        return metrics;
    }

    public synchronized void stop() {
        switchToFallback();
        if (directSocket != null && !directSocket.isClosed()) {
            directSocket.close();
        }
        scheduler.shutdownNow();
    }

    private synchronized void switchToFallback() {
        active.set(false);
        state = P2pSessionState.RELAY_FALLBACK;
        metrics.markFallback();
    }

    private synchronized void activateP2p() {
        if (remoteAddress == null) {
            return;
        }
        state = P2pSessionState.P2P_ACTIVE;
        active.set(true);
        metrics.markP2pActive();
    }

    private void fallbackIfNeeded() {
        if (!active.get() && state == P2pSessionState.P2P_NEGOTIATING) {
            sendSignedSignal(CmdType.P2pResult, CmdP2pResult.FALLBACK);
            switchToFallback();
        }
    }

    private void ensureSocket() {
        if (directSocket != null && !directSocket.isClosed()) {
            return;
        }
        try {
            directSocket = new DatagramSocket();
            startReceiverLoop();
        } catch (SocketException e) {
            Log.error("create p2p datagram socket failed", e);
        }
    }

    private void startReceiverLoop() {
        scheduler.execute(() -> {
            byte[] buf = new byte[64 * 1024];
            while (directSocket != null && !directSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    directSocket.receive(packet);
                    handleDatagram(packet);
                } catch (Exception e) {
                    if (directSocket == null || directSocket.isClosed()) {
                        return;
                    }
                }
            }
        });
    }

    private void handleDatagram(DatagramPacket packet) {
        byte[] bytes = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), bytes, 0, packet.getLength());
        String text = new String(bytes);
        if (text.startsWith(PING_PREFIX) && text.endsWith(sessionId)) {
            remoteAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
            sendAck();
            activateP2p();
            sendSignedSignal(CmdType.P2pResult, CmdP2pResult.ACTIVE);
            return;
        }
        if (text.startsWith(ACK_PREFIX) && text.endsWith(sessionId)) {
            remoteAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
            activateP2p();
            sendSignedSignal(CmdType.P2pResult, CmdP2pResult.ACTIVE);
            return;
        }
        try {
            Object decoded = deserialize(bytes);
            if (decoded instanceof Cmd) {
                Cmd cmd = (Cmd) decoded;
                if (cmd.getType().equals(CmdType.Capture)) {
                    metrics.markFirstFrame();
                }
                remoteClient.handleP2pCmd(cmd);
            }
        } catch (Exception e) {
            Log.warn("decode p2p packet error: " + e.getMessage());
        }
    }

    private void sendAck() {
        if (remoteAddress == null || directSocket == null || sessionId == null) {
            return;
        }
        try {
            byte[] payload = (ACK_PREFIX + sessionId).getBytes();
            directSocket.send(new DatagramPacket(payload, payload.length, remoteAddress));
        } catch (Exception e) {
            Log.warn("send ack failed: " + e.getMessage());
        }
    }

    private void onRemoteCandidates(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return;
        }
        String[] values = payload.split(",");
        for (String value : values) {
            P2pTransportCandidate candidate = P2pTransportCandidate.parse(value.trim());
            if (candidate == null) {
                continue;
            }
            tryPing(candidate);
        }
        sendSignedSignal(CmdType.P2pCandidate, localCandidatePayload());
    }

    private void tryPing(P2pTransportCandidate candidate) {
        ensureSocket();
        if (directSocket == null || sessionId == null) {
            return;
        }
        try {
            byte[] payload = (PING_PREFIX + sessionId).getBytes();
            InetSocketAddress address = new InetSocketAddress(candidate.getHost(), candidate.getPort());
            directSocket.send(new DatagramPacket(payload, payload.length, address));
        } catch (Exception e) {
            Log.debug("p2p ping candidate fail " + candidate);
        }
    }

    private String localCandidatePayload() {
        ensureSocket();
        int port = directSocket != null ? directSocket.getLocalPort() : 0;
        List<String> values = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        values.add(address.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (Exception e) {
            Log.warn("collect local candidates fail: " + e.getMessage());
        }
        return String.join(",", values);
    }

    private void sendSignedSignal(CmdType type, String payload) {
        if (sessionId == null || token == null || isTokenExpired()) {
            return;
        }
        long ts = System.currentTimeMillis();
        String signContent = sessionId + "|" + ts + "|" + (payload == null ? "" : payload);
        String signature = P2pSecurityUtils.sign(token, signContent);
        if (type.equals(CmdType.P2pOffer)) {
            remoteClient.getController().fireCmd(new CmdP2pOffer(sessionId, ts, token, signature, payload));
        } else if (type.equals(CmdType.P2pAnswer)) {
            remoteClient.getController().fireCmd(new CmdP2pAnswer(sessionId, ts, token, signature, payload));
        } else if (type.equals(CmdType.P2pCandidate)) {
            remoteClient.getController().fireCmd(new CmdP2pCandidate(sessionId, ts, token, signature, payload));
        } else if (type.equals(CmdType.P2pResult)) {
            remoteClient.getController().fireCmd(new CmdP2pResult(sessionId, ts, token, signature, payload));
        }
    }

    private boolean isTokenExpired() {
        return tokenExpireAt > 0 && System.currentTimeMillis() > tokenExpireAt;
    }

    private long parseExpireAt(String payload) {
        if (payload == null) {
            return 0;
        }
        String[] split = payload.split("\\|");
        if (split.length < 2) {
            return 0;
        }
        try {
            return Long.parseLong(split[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private byte[] serialize(Cmd cmd) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(cmd);
        oos.flush();
        return bos.toByteArray();
    }

    private Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return ois.readObject();
    }
}
