package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public class CmdP2pAnswer extends CmdP2pSignal {

    public CmdP2pAnswer(String sessionId, long timestamp, String token, String signature, String payload) {
        super(sessionId, timestamp, token, signature, payload);
    }

    @Override
    public CmdType getType() {
        return CmdType.P2pAnswer;
    }

    public static CmdP2pAnswer decode(ByteBuf in) {
        SignalFields fields = decodeSignal(in);
        return new CmdP2pAnswer(fields.sessionId, fields.timestamp, fields.token, fields.signature, fields.payload);
    }
}
