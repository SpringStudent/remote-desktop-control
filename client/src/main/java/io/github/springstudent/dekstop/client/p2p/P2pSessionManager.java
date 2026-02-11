package io.github.springstudent.dekstop.client.p2p;

import io.github.springstudent.dekstop.common.bean.P2pState;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.utils.P2pSignUtils;
import io.netty.channel.Channel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class P2pSessionManager {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile P2pState state = P2pState.RELAY;
    private volatile String sessionId;
    private volatile String token;
    private volatile long expireAt;
    private volatile long negotiationStartedAt;
    private final AtomicLong p2pSuccess = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();
    private final AtomicLong firstFrameLatencyMs = new AtomicLong(-1);

    public synchronized void handleBootstrap(CmdP2pResult result, Channel channel) {
        this.sessionId = result.getSessionId();
        this.token = result.getToken();
        this.expireAt = result.getExpireAt();
        this.negotiationStartedAt = System.currentTimeMillis();
        this.state = P2pState.P2P_NEGOTIATING;
        sendOffer(channel);
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (state == P2pState.P2P_NEGOTIATING) {
                    state = P2pState.RELAY_FALLBACK;
                    fallbackCount.incrementAndGet();
                }
            }
        }, 8, TimeUnit.SECONDS);
    }

    public synchronized void handleSignal(Cmd cmd, Channel channel) {
        if (cmd instanceof CmdP2pOffer) {
            sendAnswer(channel);
        } else if (cmd instanceof CmdP2pAnswer) {
            markP2pActive();
        } else if (cmd instanceof CmdP2pCandidate) {
            if ("udp-ack".equals(((CmdP2pCandidate) cmd).getCandidate())) {
                markP2pActive();
            }
        }
    }

    private void sendOffer(Channel channel) {
        long ts = System.currentTimeMillis();
        String payload = "offer-host-candidate";
        String sign = sign(CmdType.P2pOffer, ts, payload);
        channel.writeAndFlush(new CmdP2pOffer(sessionId, token, ts, sign, payload));

        long pingTs = System.currentTimeMillis();
        String pingPayload = "udp-ping";
        channel.writeAndFlush(new CmdP2pCandidate(sessionId, token, pingTs, sign(CmdType.P2pCandidate, pingTs, pingPayload), pingPayload));
    }

    private void sendAnswer(Channel channel) {
        long ts = System.currentTimeMillis();
        String payload = "answer-host-candidate";
        channel.writeAndFlush(new CmdP2pAnswer(sessionId, token, ts, sign(CmdType.P2pAnswer, ts, payload), payload));

        long ackTs = System.currentTimeMillis();
        String ackPayload = "udp-ack";
        channel.writeAndFlush(new CmdP2pCandidate(sessionId, token, ackTs, sign(CmdType.P2pCandidate, ackTs, ackPayload), ackPayload));
    }

    private synchronized void markP2pActive() {
        if (state != P2pState.P2P_ACTIVE) {
            state = P2pState.P2P_ACTIVE;
            p2pSuccess.incrementAndGet();
            if (firstFrameLatencyMs.get() < 0) {
                firstFrameLatencyMs.set(System.currentTimeMillis() - negotiationStartedAt);
            }
        }
    }

    public boolean shouldPreferDirect() {
        return state == P2pState.P2P_ACTIVE;
    }

    public boolean sendDirect(Cmd cmd) {
        return false;
    }

    public void onDirectUnavailable() {
        if (state == P2pState.P2P_ACTIVE) {
            state = P2pState.RELAY_FALLBACK;
            fallbackCount.incrementAndGet();
            Log.warn("direct channel unavailable, fallback to relay");
        }
    }

    private String sign(CmdType type, long timestamp, String payload) {
        return P2pSignUtils.sign(token, type.name() + "|" + sessionId + "|" + token + "|" + timestamp + "|" + payload);
    }

    public String metrics() {
        return "p2pSuccess=" + p2pSuccess.get() + ",fallback=" + fallbackCount.get() + ",firstFrameLatencyMs=" + firstFrameLatencyMs.get();
    }

    public P2pState getState() { return state; }

    public boolean isSignalCommand(Cmd cmd) {
        return cmd.getType() == CmdType.P2pOffer || cmd.getType() == CmdType.P2pAnswer || cmd.getType() == CmdType.P2pCandidate || cmd.getType() == CmdType.P2pResult;
    }

    public boolean isExpired() {
        return expireAt > 0 && System.currentTimeMillis() > expireAt;
    }
}
