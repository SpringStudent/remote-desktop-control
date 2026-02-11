package io.github.springstudent.dekstop.client.p2p;

import io.github.springstudent.dekstop.common.bean.P2pState;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.utils.P2pSignUtils;
import io.netty.channel.Channel;

import java.io.*;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class P2pSessionManager {
    private static final int MAX_UDP_PACKET_SIZE = 1200;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Consumer<Cmd> directCmdConsumer;

    private volatile P2pState state = P2pState.RELAY;
    private volatile String sessionId;
    private volatile String token;
    private volatile long expireAt;
    private volatile long negotiationStartedAt;
    private volatile ScheduledFuture<?> readyProbeFuture;
    private final AtomicLong p2pSuccess = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();
    private final AtomicLong firstFrameLatencyMs = new AtomicLong(-1);
    private volatile Ice4jTransport ice4jTransport;

    public P2pSessionManager(Consumer<Cmd> directCmdConsumer) {
        this.directCmdConsumer = directCmdConsumer;
    }

    public synchronized void handleBootstrap(CmdP2pResult result, Channel channel) {
        this.sessionId = result.getSessionId();
        this.token = result.getToken();
        this.expireAt = result.getExpireAt();
        this.negotiationStartedAt = System.currentTimeMillis();
        cancelReadyProbe();
        this.state = P2pState.P2P_NEGOTIATING;
        this.ice4jTransport = Ice4jTransport.create(this::handleDirectInboundCmd);
        if (this.ice4jTransport == null) {
            fallback("ice4j bootstrap init failed");
            return;
        }
        sendOffer(channel);
        scheduleReadyProbe();
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (state == P2pState.P2P_NEGOTIATING) {
                    fallback("ice negotiation timeout");
                }
            }
        }, 10, TimeUnit.SECONDS);
    }

    public synchronized void handleSignal(Cmd cmd, Channel channel) {
        if (ice4jTransport == null) {
            return;
        }
        if (cmd instanceof CmdP2pOffer) {
            CmdP2pOffer offer = (CmdP2pOffer) cmd;
            if (ice4jTransport.applyRemoteDescription(offer.getSdp())) {
                sendAnswer(channel);
                if (ice4jTransport.startConnectivityChecks()) {
                    markP2pActiveIfReady();
                    scheduleReadyProbe();
                }
            } else {
                fallback("apply remote offer failed");
            }
        } else if (cmd instanceof CmdP2pAnswer) {
            CmdP2pAnswer answer = (CmdP2pAnswer) cmd;
            if (ice4jTransport.applyRemoteDescription(answer.getSdp()) && ice4jTransport.startConnectivityChecks()) {
                markP2pActiveIfReady();
                scheduleReadyProbe();
            } else {
                fallback("apply remote answer failed");
            }
        } else if (cmd instanceof CmdP2pCandidate) {
            CmdP2pCandidate candidate = (CmdP2pCandidate) cmd;
            if (!ice4jTransport.addRemoteCandidate(candidate.getCandidate())) {
                Log.warn("ignore invalid remote candidate line");
            }
        }
    }

    private void sendOffer(Channel channel) {
        long ts = System.currentTimeMillis();
        String payload = ice4jTransport.localDescription();
        String sign = sign(CmdType.P2pOffer, ts, payload);
        channel.writeAndFlush(new CmdP2pOffer(sessionId, token, ts, sign, payload));
        publishTrickleCandidates(channel);
    }

    private void sendAnswer(Channel channel) {
        long ts = System.currentTimeMillis();
        String payload = ice4jTransport.localDescription();
        channel.writeAndFlush(new CmdP2pAnswer(sessionId, token, ts, sign(CmdType.P2pAnswer, ts, payload), payload));
        publishTrickleCandidates(channel);
    }

    private void publishTrickleCandidates(Channel channel) {
        List<String> lines = ice4jTransport.localCandidateLines();
        for (String line : lines) {
            long ts = System.currentTimeMillis();
            channel.writeAndFlush(new CmdP2pCandidate(sessionId, token, ts, sign(CmdType.P2pCandidate, ts, line), line));
        }
    }

    private void markP2pActiveIfReady() {
        if (ice4jTransport != null && ice4jTransport.isReady()) {
            markP2pActive();
        }
    }

    private synchronized void scheduleReadyProbe() {
        if (readyProbeFuture != null && !readyProbeFuture.isCancelled()) {
            return;
        }
        readyProbeFuture = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (state != P2pState.P2P_NEGOTIATING) {
                    cancelReadyProbe();
                    return;
                }
                markP2pActiveIfReady();
            }
        }, 100, 200, TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelReadyProbe() {
        if (readyProbeFuture != null) {
            readyProbeFuture.cancel(false);
            readyProbeFuture = null;
        }
    }

    private void handleDirectInboundCmd(Cmd cmd) {
        markP2pActive();
        if (directCmdConsumer != null) {
            directCmdConsumer.accept(cmd);
        }
    }

    private synchronized void markP2pActive() {
        if (state != P2pState.P2P_ACTIVE) {
            state = P2pState.P2P_ACTIVE;
            cancelReadyProbe();
            p2pSuccess.incrementAndGet();
            if (firstFrameLatencyMs.get() < 0) {
                firstFrameLatencyMs.set(System.currentTimeMillis() - negotiationStartedAt);
            }
        }
    }

    private void fallback(String reason) {
        if (state == P2pState.RELAY_FALLBACK) {
            return;
        }
        state = P2pState.RELAY_FALLBACK;
        cancelReadyProbe();
        fallbackCount.incrementAndGet();
        Log.warn("fallback to relay: " + reason);
    }

    public boolean shouldPreferDirect() {
        return state == P2pState.P2P_ACTIVE;
    }

    public boolean sendDirect(Cmd cmd) {
        if (state != P2pState.P2P_ACTIVE || ice4jTransport == null) {
            return false;
        }
        try {
            byte[] payload = serialize(cmd);
            if (payload.length > MAX_UDP_PACKET_SIZE) {
                return false;
            }
            return ice4jTransport.send(payload);
        } catch (Exception e) {
            Log.warn("send direct data failed", e);
            return false;
        }
    }

    public void onDirectUnavailable() {
        if (state == P2pState.P2P_ACTIVE) {
            fallback("direct channel unavailable");
        }
    }

    private String sign(CmdType type, long timestamp, String payload) {
        return P2pSignUtils.sign(token, type.name() + "|" + sessionId + "|" + token + "|" + timestamp + "|" + payload);
    }

    public String metrics() {
        return "p2pSuccess=" + p2pSuccess.get() + ",fallback=" + fallbackCount.get() + ",firstFrameLatencyMs=" + firstFrameLatencyMs.get();
    }

    public P2pState getState() {
        return state;
    }

    public boolean isSignalCommand(Cmd cmd) {
        return cmd.getType() == CmdType.P2pOffer || cmd.getType() == CmdType.P2pAnswer || cmd.getType() == CmdType.P2pCandidate || cmd.getType() == CmdType.P2pResult;
    }

    public boolean isExpired() {
        return expireAt > 0 && System.currentTimeMillis() > expireAt;
    }

    private static byte[] serialize(Cmd cmd) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(cmd);
        oos.flush();
        return bos.toByteArray();
    }

    private static Cmd deserialize(byte[] payload) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(payload));
        Object obj = ois.readObject();
        if (obj instanceof Cmd) {
            return (Cmd) obj;
        }
        return null;
    }

    /**
     * Reflection-based ice4j adapter to keep this module compatible while using true ICE primitives.
     */
    static class Ice4jTransport {
        private final Object agent;
        private final Object stream;
        private final Object component;
        private final Consumer<Cmd> inboundConsumer;
        private volatile DatagramSocket selectedSocket;

        private Ice4jTransport(Object agent, Object stream, Object component, Consumer<Cmd> inboundConsumer) {
            this.agent = agent;
            this.stream = stream;
            this.component = component;
            this.inboundConsumer = inboundConsumer;
        }

        static Ice4jTransport create(Consumer<Cmd> inboundConsumer) {
            try {
                Class<?> agentClass = Class.forName("org.ice4j.ice.Agent");
                Object agent = agentClass.newInstance();

                Object stream = invoke(agent, "createMediaStream", new Class[]{String.class}, "desktop-data");
                Class<?> transportClass = Class.forName("org.ice4j.Transport");
                Object udp = Enum.valueOf((Class<Enum>) transportClass, "UDP");

                // ice4j expects (preferredPort, minPort, maxPort). Keep preferred within range for 1.x compatibility.
                Object component = invoke(agent, "createComponent", new Class[]{Class.forName("org.ice4j.ice.IceMediaStream"), transportClass, int.class, int.class, int.class}, stream, udp, 45000, 40000, 50000);

                Ice4jTransport transport = new Ice4jTransport(agent, stream, component, inboundConsumer);
                transport.attachStateListener();
                return transport;
            } catch (Exception e) {
                Log.error("ice4j create failed", e);
                return null;
            }
        }

        String localDescription() {
            try {
                String ufrag = String.valueOf(invokeNoArgAny("getLocalUfrag"));
                String pwd = String.valueOf(invokeNoArgAny("getLocalPassword"));
                StringBuilder sb = new StringBuilder();
                sb.append("ufrag=").append(ufrag).append("\n");
                sb.append("pwd=").append(pwd).append("\n");
                for (String line : localCandidateLines()) {
                    sb.append("candidate=").append(line).append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                Log.error("build local description failed", e);
                return "";
            }
        }

        List<String> localCandidateLines() {
            List<String> lines = new ArrayList<String>();
            try {
                Object locals = invoke(component, "getLocalCandidates", new Class[]{});
                if (locals instanceof Iterable) {
                    for (Object c : (Iterable<?>) locals) {
                        lines.add(String.valueOf(c));
                    }
                }
            } catch (Exception e) {
                Log.warn("read local candidates failed", e);
            }
            return lines;
        }

        boolean applyRemoteDescription(String desc) {
            try {
                String remoteUfrag = null;
                String remotePwd = null;
                List<String> candidateLines = new ArrayList<String>();
                for (String line : desc.split("\\n")) {
                    if (line.startsWith("ufrag=")) {
                        remoteUfrag = line.substring("ufrag=".length()).trim();
                    } else if (line.startsWith("pwd=")) {
                        remotePwd = line.substring("pwd=".length()).trim();
                    } else if (line.startsWith("candidate=")) {
                        candidateLines.add(line.substring("candidate=".length()).trim());
                    }
                }
                if (remoteUfrag == null || remotePwd == null) {
                    return false;
                }
                if (!setRemoteCredential("setRemoteUfrag", remoteUfrag) || !setRemoteCredential("setRemotePassword", remotePwd)) {
                    return false;
                }
                for (String c : candidateLines) {
                    addRemoteCandidate(c);
                }
                return true;
            } catch (Exception e) {
                Log.error("apply remote description failed", e);
                return false;
            }
        }

        boolean addRemoteCandidate(String candidateLine) {
            if (candidateLine == null || candidateLine.isEmpty()) {
                return false;
            }
            try {
                // ice4j candidate parsing APIs differ by versions; best-effort use parser when available.
                Class<?> parser = Class.forName("org.ice4j.ice.sdp.CandidateAttribute");
                Object parsed = invokeStatic(parser, "parse", new Class[]{String.class}, candidateLine);
                if (parsed != null) {
                    invoke(component, "addRemoteCandidate", new Class[]{Class.forName("org.ice4j.ice.RemoteCandidate")}, parsed);
                    return true;
                }
            } catch (Exception ignored) {
                // fall through for compatibility
            }
            return false;
        }

        boolean startConnectivityChecks() {
            try {
                invoke(agent, "startConnectivityEstablishment", new Class[]{});
                return true;
            } catch (Exception e) {
                Log.error("start connectivity failed", e);
                return false;
            }
        }

        boolean isReady() {
            ensureSelectedSocket();
            return selectedSocket != null;
        }

        boolean send(byte[] payload) {
            ensureSelectedSocket();
            if (selectedSocket == null) {
                return false;
            }
            try {
                java.net.SocketAddress remote = selectedSocket.getRemoteSocketAddress();
                if (!(remote instanceof java.net.InetSocketAddress)) {
                    return false;
                }
                selectedSocket.send(new DatagramPacket(payload, payload.length, (java.net.InetSocketAddress) remote));
                return true;
            } catch (Exception e) {
                Log.warn("ice send failed", e);
                return false;
            }
        }

        private void attachStateListener() {
            try {
                java.beans.PropertyChangeListener listener = new java.beans.PropertyChangeListener() {
                    @Override
                    public void propertyChange(java.beans.PropertyChangeEvent evt) {
                        ensureSelectedSocket();
                        if (selectedSocket != null) {
                            startReceiverIfNeeded();
                        }
                    }
                };
                invoke(agent, "addStateChangeListener", new Class[]{java.beans.PropertyChangeListener.class}, listener);
            } catch (Exception e) {
                Log.warn("attach state listener failed", e);
            }
        }

        private volatile boolean receiverStarted = false;

        private synchronized void startReceiverIfNeeded() {
            if (receiverStarted || selectedSocket == null) {
                return;
            }
            receiverStarted = true;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveLoop();
                }
            }, "ice4j-direct-recv");
            t.setDaemon(true);
            t.start();
        }

        private void receiveLoop() {
            byte[] buf = new byte[65535];
            while (selectedSocket != null && !selectedSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    selectedSocket.receive(packet);
                    byte[] payload = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), payload, 0, packet.getLength());
                    Cmd cmd = deserialize(payload);
                    if (cmd != null && inboundConsumer != null) {
                        inboundConsumer.accept(cmd);
                    }
                } catch (Exception e) {
                    Log.warn("ice receive failed", e);
                    break;
                }
            }
        }

        private void ensureSelectedSocket() {
            if (selectedSocket != null) {
                return;
            }
            try {
                Object pair = invoke(component, "getSelectedPair", new Class[]{});
                if (pair == null) {
                    return;
                }
                Object wrapper = invoke(pair, "getIceSocketWrapper", new Class[]{});
                Object socket = invoke(wrapper, "getUDPSocket", new Class[]{});
                if (socket instanceof DatagramSocket) {
                    selectedSocket = (DatagramSocket) socket;
                }
            } catch (Exception e) {
                Log.debug("selected socket not ready yet");
            }
        }

        private Object invokeNoArgAny(String methodName) throws Exception {
            if (hasMethod(agent, methodName, new Class[]{})) {
                return invoke(agent, methodName, new Class[]{});
            }
            if (hasMethod(stream, methodName, new Class[]{})) {
                return invoke(stream, methodName, new Class[]{});
            }
            throw new NoSuchMethodException(methodName);
        }

        private boolean setRemoteCredential(String methodName, String value) {
            try {
                Class<?> iceStreamClass = Class.forName("org.ice4j.ice.IceMediaStream");
                if (invokeIfExists(agent, methodName, new Class[]{iceStreamClass, String.class}, stream, value)) {
                    return true;
                }
                if (invokeIfExists(agent, methodName, new Class[]{String.class}, value)) {
                    return true;
                }
                if (invokeIfExists(stream, methodName, new Class[]{String.class}, value)) {
                    return true;
                }
            } catch (Exception e) {
                Log.warn("set remote credential failed: " + methodName, e);
                return false;
            }
            Log.warn("set remote credential method missing: " + methodName);
            return false;
        }

        private static boolean hasMethod(Object target, String name, Class<?>[] paramTypes) {
            try {
                target.getClass().getMethod(name, paramTypes);
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        private static boolean invokeIfExists(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
            try {
                Method m = target.getClass().getMethod(name, paramTypes);
                m.setAccessible(true);
                m.invoke(target, args);
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        private static Object invoke(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
            Method m = target.getClass().getMethod(name, paramTypes);
            m.setAccessible(true);
            return m.invoke(target, args);
        }

        private static Object invokeStatic(Class<?> type, String name, Class<?>[] paramTypes, Object... args) throws Exception {
            Method m = type.getMethod(name, paramTypes);
            m.setAccessible(true);
            return m.invoke(null, args);
        }

        private Object invokeNoArgAny(String methodName) throws Exception {
            if (hasMethod(agent, methodName, new Class[]{})) {
                return invoke(agent, methodName, new Class[]{});
            }
            if (hasMethod(stream, methodName, new Class[]{})) {
                return invoke(stream, methodName, new Class[]{});
            }
            throw new NoSuchMethodException(methodName);
        }

        private boolean setRemoteCredential(String methodName, String value) {
            try {
                Class<?> iceStreamClass = Class.forName("org.ice4j.ice.IceMediaStream");
                if (invokeIfExists(agent, methodName, new Class[]{iceStreamClass, String.class}, stream, value)) {
                    return true;
                }
                if (invokeIfExists(agent, methodName, new Class[]{String.class}, value)) {
                    return true;
                }
                if (invokeIfExists(stream, methodName, new Class[]{String.class}, value)) {
                    return true;
                }
            } catch (Exception e) {
                Log.warn("set remote credential failed: " + methodName, e);
                return false;
            }
            Log.warn("set remote credential method missing: " + methodName);
            return false;
        }

        private static boolean hasMethod(Object target, String name, Class<?>[] paramTypes) {
            try {
                target.getClass().getMethod(name, paramTypes);
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        private static boolean invokeIfExists(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
            try {
                Method m = target.getClass().getMethod(name, paramTypes);
                m.setAccessible(true);
                m.invoke(target, args);
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

    }
}
