package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * @author ZhouNing
 * @date 2025/7/9 8:24
 **/
public class CmdResOpen extends Cmd {

    private byte code;

    public static final byte OK = 0x00;
    public static final byte OFFLINE = CmdResCapture.CONTROL;
    public static final byte CONTROL = CmdResCapture.OFFLINE;

    public CmdResOpen(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    @Override
    public CmdType getType() {
        return CmdType.ResOpen;
    }

    @Override
    public int getWireSize() {
        return 1;
    }

    @Override
    public String toString() {
        return String.format("CmdResOpen={code:%d}", code);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeByte(code);
    }

    public static CmdResOpen decode(ByteBuf in) {
        return new CmdResOpen(in.readByte());
    }
}
