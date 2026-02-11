package io.github.springstudent.dekstop.server.netty;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import io.github.springstudent.dekstop.common.command.AbstractCmdP2pSignal;
import io.github.springstudent.dekstop.common.command.Cmd;
import io.github.springstudent.dekstop.common.utils.P2pSignUtils;
import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class P2pSessionManager {
    private static final long EXPIRE_MS = 5 * 60 * 1000;
    private static final long MAX_SKEW_MS = 15 * 1000;

    private static final Map<String, P2pSession> sessionMap = new ConcurrentHashMap<String, P2pSession>(64);

    private P2pSessionManager() {
    }

    public static P2pSession create(Channel controller, Channel controlled) {
        String sessionId = IdUtil.fastSimpleUUID();
        String token = RandomUtil.randomString(48);
        long expireAt = System.currentTimeMillis() + EXPIRE_MS;
        P2pSession session = new P2pSession(sessionId, token, expireAt, controller, controlled);
        sessionMap.put(sessionId, session);
        return session;
    }

    public static void removeByChannel(Channel channel) {
        sessionMap.entrySet().removeIf(entry -> entry.getValue().getController() == channel || entry.getValue().getControlled() == channel);
    }

    public static P2pSession get(String sessionId) {
        return sessionMap.get(sessionId);
    }

    public static boolean validate(AbstractCmdP2pSignal cmd) {
        P2pSession session = get(cmd.getSessionId());
        if (session == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now > session.getExpireAt()) {
            return false;
        }
        if (Math.abs(now - cmd.getTimestamp()) > MAX_SKEW_MS) {
            return false;
        }
        if (!session.getToken().equals(cmd.getToken())) {
            return false;
        }
        return P2pSignUtils.verify(cmd.getToken(), signPayload(cmd), cmd.getSignature());
    }

    public static String signPayload(AbstractCmdP2pSignal cmd) {
        String payload = "";
        if (cmd.getType() == io.github.springstudent.dekstop.common.command.CmdType.P2pOffer) {
            payload = ((io.github.springstudent.dekstop.common.command.CmdP2pOffer) cmd).getSdp();
        } else if (cmd.getType() == io.github.springstudent.dekstop.common.command.CmdType.P2pAnswer) {
            payload = ((io.github.springstudent.dekstop.common.command.CmdP2pAnswer) cmd).getSdp();
        } else if (cmd.getType() == io.github.springstudent.dekstop.common.command.CmdType.P2pCandidate) {
            payload = ((io.github.springstudent.dekstop.common.command.CmdP2pCandidate) cmd).getCandidate();
        }
        return cmd.getType().name() + "|" + cmd.getSessionId() + "|" + cmd.getToken() + "|" + cmd.getTimestamp() + "|" + payload;
    }

    public static Channel peerChannel(Cmd cmd, Channel source) {
        if (!(cmd instanceof AbstractCmdP2pSignal)) {
            return null;
        }
        P2pSession session = get(((AbstractCmdP2pSignal) cmd).getSessionId());
        if (session == null) {
            return null;
        }
        if (session.getController() == source) {
            return session.getControlled();
        }
        if (session.getControlled() == source) {
            return session.getController();
        }
        return null;
    }
}
