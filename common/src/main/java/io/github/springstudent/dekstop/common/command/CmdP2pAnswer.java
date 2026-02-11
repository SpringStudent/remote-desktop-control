package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

public class CmdP2pAnswer extends AbstractCmdP2pSignal {
    private final String sdp;

    public CmdP2pAnswer(String sessionId, String token, long timestamp, String signature, String sdp) {
        super(sessionId, token, timestamp, signature);
        this.sdp = sdp;
    }

    @Override
    public CmdType getType() { return CmdType.P2pAnswer; }

    @Override
    public int getWireSize() {
        return wireSizeOf(sessionId) + wireSizeOf(token) + 8 + wireSizeOf(signature) + wireSizeOf(sdp);
    }

    @Override
    public void encode(ByteBuf out) {
        writeString(out, sessionId);
        writeString(out, token);
        out.writeLong(timestamp);
        writeString(out, signature);
        writeString(out, sdp);
    }

    public String getSdp() { return sdp; }

    public static CmdP2pAnswer decode(ByteBuf in) {
        return new CmdP2pAnswer(readString(in), readString(in), in.readLong(), readString(in), readString(in));
    }

    @Override
    public String toString() { return "CmdP2pAnswer{" + sessionId + "}"; }
}
