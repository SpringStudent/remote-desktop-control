package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * Controller peer replies to CmdP2POffer, indicating whether the direct connection succeeded.
 *
 * @author ZhouNing
 * @date 2026/07/21
 */
public class CmdP2PAnswer extends Cmd {

    private boolean success;

    public CmdP2PAnswer() {
    }

    public CmdP2PAnswer(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public CmdType getType() {
        return CmdType.P2PAnswer;
    }

    @Override
    public int getWireSize() {
        return 1;
    }

    @Override
    public String toString() {
        return String.format("CmdP2PAnswer={success:%s}", success);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeByte(success ? 1 : 0);
    }

    public static CmdP2PAnswer decode(ByteBuf in) {
        return new CmdP2PAnswer(in.readByte() == 1);
    }
}
