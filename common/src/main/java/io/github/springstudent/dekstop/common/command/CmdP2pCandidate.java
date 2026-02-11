package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

public class CmdP2pCandidate extends AbstractCmdP2pSignal {
    private final String candidate;

    public CmdP2pCandidate(String sessionId, String token, long timestamp, String signature, String candidate) {
        super(sessionId, token, timestamp, signature);
        this.candidate = candidate;
    }

    @Override
    public CmdType getType() { return CmdType.P2pCandidate; }

    @Override
    public int getWireSize() {
        return wireSizeOf(sessionId) + wireSizeOf(token) + 8 + wireSizeOf(signature) + wireSizeOf(candidate);
    }

    @Override
    public void encode(ByteBuf out) {
        writeString(out, sessionId);
        writeString(out, token);
        out.writeLong(timestamp);
        writeString(out, signature);
        writeString(out, candidate);
    }

    public String getCandidate() { return candidate; }

    public static CmdP2pCandidate decode(ByteBuf in) {
        return new CmdP2pCandidate(readString(in), readString(in), in.readLong(), readString(in), readString(in));
    }

    @Override
    public String toString() { return "CmdP2pCandidate{" + sessionId + "}"; }
}
