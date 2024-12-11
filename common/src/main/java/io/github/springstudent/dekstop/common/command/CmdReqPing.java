package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * @author ZhouNing
 * @date 2024/12/11 16:08
 **/
public class CmdReqPing extends Cmd{
    @Override
    public CmdType getType() {
        return CmdType.ReqPing;
    }

    @Override
    public int getWireSize() {
        return 0;
    }

    @Override
    public String toString() {
        return CmdReqPing.class.getName();
    }

    @Override
    public void encode(ByteBuf out) throws IOException {

    }
}
