package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public class CmdP2pResult extends CmdP2pSignal {

    public static final String TOKEN_ISSUED = "TOKEN_ISSUED";
    public static final String NEGOTIATING = "NEGOTIATING";
    public static final String ACTIVE = "ACTIVE";
    public static final String FALLBACK = "FALLBACK";
    public static final String FAILED = "FAILED";

    public CmdP2pResult(String sessionId, long timestamp, String token, String signature, String payload) {
        super(sessionId, timestamp, token, signature, payload);
    }

    @Override
    public CmdType getType() {
        return CmdType.P2pResult;
    }

    public static CmdP2pResult decode(ByteBuf in) {
        SignalFields fields = decodeSignal(in);
        return new CmdP2pResult(fields.sessionId, fields.timestamp, fields.token, fields.signature, fields.payload);
    }
}
