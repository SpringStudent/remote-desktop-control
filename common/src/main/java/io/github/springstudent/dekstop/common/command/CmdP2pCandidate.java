package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public class CmdP2pCandidate extends CmdP2pSignal {

    public CmdP2pCandidate(String sessionId, long timestamp, String token, String signature, String payload) {
        super(sessionId, timestamp, token, signature, payload);
    }

    @Override
    public CmdType getType() {
        return CmdType.P2pCandidate;
    }

    public static CmdP2pCandidate decode(ByteBuf in) {
        SignalFields fields = decodeSignal(in);
        return new CmdP2pCandidate(fields.sessionId, fields.timestamp, fields.token, fields.signature, fields.payload);
    }
}
