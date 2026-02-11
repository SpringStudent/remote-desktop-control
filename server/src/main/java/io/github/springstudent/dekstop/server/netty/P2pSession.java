package io.github.springstudent.dekstop.server.netty;

import io.netty.channel.Channel;

public class P2pSession {
    private final String sessionId;
    private final String token;
    private final long expireAt;
    private final Channel controller;
    private final Channel controlled;

    public P2pSession(String sessionId, String token, long expireAt, Channel controller, Channel controlled) {
        this.sessionId = sessionId;
        this.token = token;
        this.expireAt = expireAt;
        this.controller = controller;
        this.controlled = controlled;
    }

    public String getSessionId() { return sessionId; }
    public String getToken() { return token; }
    public long getExpireAt() { return expireAt; }
    public Channel getController() { return controller; }
    public Channel getControlled() { return controlled; }
}
