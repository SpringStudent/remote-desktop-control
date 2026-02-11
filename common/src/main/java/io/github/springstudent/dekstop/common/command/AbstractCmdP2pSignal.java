package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public abstract class AbstractCmdP2pSignal extends Cmd {
    protected final String sessionId;
    protected final String token;
    protected final long timestamp;
    protected final String signature;

    protected AbstractCmdP2pSignal(String sessionId, String token, long timestamp, String signature) {
        this.sessionId = sessionId;
        this.token = token;
        this.timestamp = timestamp;
        this.signature = signature;
    }

    protected static void writeString(ByteBuf out, String value) {
        String safe = value == null ? "" : value;
        out.writeInt(safe.getBytes(StandardCharsets.UTF_8).length);
        out.writeCharSequence(safe, StandardCharsets.UTF_8);
    }

    protected static String readString(ByteBuf in) {
        int len = in.readInt();
        return in.readCharSequence(len, StandardCharsets.UTF_8).toString();
    }

    protected static int wireSizeOf(String value) {
        String safe = value == null ? "" : value;
        return 4 + safe.getBytes(StandardCharsets.UTF_8).length;
    }

    public String getSessionId() { return sessionId; }
    public String getToken() { return token; }
    public long getTimestamp() { return timestamp; }
    public String getSignature() { return signature; }
}
