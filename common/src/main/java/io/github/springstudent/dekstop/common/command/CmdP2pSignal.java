package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public abstract class CmdP2pSignal extends Cmd {

    private String sessionId;
    private long timestamp;
    private String token;
    private String signature;
    private String payload;

    protected CmdP2pSignal(String sessionId, long timestamp, String token, String signature, String payload) {
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.token = token;
        this.signature = signature;
        this.payload = payload;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getToken() {
        return token;
    }

    public String getSignature() {
        return signature;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public int getWireSize() {
        return 8 +
                4 + strLength(sessionId) +
                4 + strLength(token) +
                4 + strLength(signature) +
                4 + strLength(payload);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeLong(timestamp);
        writeString(out, sessionId);
        writeString(out, token);
        writeString(out, signature);
        writeString(out, payload);
    }

    protected static SignalFields decodeSignal(ByteBuf in) {
        long timestamp = in.readLong();
        String sessionId = readString(in);
        String token = readString(in);
        String signature = readString(in);
        String payload = readString(in);
        return new SignalFields(sessionId, timestamp, token, signature, payload);
    }

    protected static void writeString(ByteBuf out, String value) {
        if (value == null) {
            out.writeInt(0);
            return;
        }
        out.writeInt(value.length());
        out.writeCharSequence(value, StandardCharsets.UTF_8);
    }

    protected static String readString(ByteBuf in) {
        int length = in.readInt();
        if (length <= 0) {
            return null;
        }
        return in.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    protected static int strLength(String value) {
        return value == null ? 0 : value.length();
    }

    @Override
    public String toString() {
        return String.format("%s{sessionId:%s,timestamp:%s,payload:%s}", getClass().getSimpleName(), sessionId, timestamp, payload);
    }

    protected static class SignalFields {
        protected final String sessionId;
        protected final long timestamp;
        protected final String token;
        protected final String signature;
        protected final String payload;

        protected SignalFields(String sessionId, long timestamp, String token, String signature, String payload) {
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            this.token = token;
            this.signature = signature;
            this.payload = payload;
        }
    }
}
