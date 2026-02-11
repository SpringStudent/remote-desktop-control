package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

public class CmdP2pResult extends Cmd {
    private final String sessionId;
    private final String token;
    private final long expireAt;
    private final byte code;
    private final String message;

    public static final byte OK = 0x00;
    public static final byte INVALID = 0x01;

    public CmdP2pResult(String sessionId, String token, long expireAt, byte code, String message) {
        this.sessionId = sessionId;
        this.token = token;
        this.expireAt = expireAt;
        this.code = code;
        this.message = message;
    }

    @Override
    public CmdType getType() { return CmdType.P2pResult; }

    @Override
    public int getWireSize() {
        return AbstractCmdP2pSignal.wireSizeOf(sessionId) + AbstractCmdP2pSignal.wireSizeOf(token) + 8 + 1 + AbstractCmdP2pSignal.wireSizeOf(message);
    }

    @Override
    public void encode(ByteBuf out) {
        AbstractCmdP2pSignal.writeString(out, sessionId);
        AbstractCmdP2pSignal.writeString(out, token);
        out.writeLong(expireAt);
        out.writeByte(code);
        AbstractCmdP2pSignal.writeString(out, message);
    }

    public static CmdP2pResult decode(ByteBuf in) {
        return new CmdP2pResult(AbstractCmdP2pSignal.readString(in), AbstractCmdP2pSignal.readString(in), in.readLong(), in.readByte(), AbstractCmdP2pSignal.readString(in));
    }

    public String getSessionId() { return sessionId; }
    public String getToken() { return token; }
    public long getExpireAt() { return expireAt; }
    public byte getCode() { return code; }
    public String getMessage() { return message; }

    @Override
    public String toString() { return "CmdP2pResult{" + sessionId + "," + code + "}"; }
}
