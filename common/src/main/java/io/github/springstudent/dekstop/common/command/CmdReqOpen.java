package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author ZhouNing
 * @date 2025/7/9 8:19
 **/
public class CmdReqOpen extends Cmd {
    private String deviceCode;

    public CmdReqOpen(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    @Override
    public CmdType getType() {
        return CmdType.ReqOpen;
    }

    @Override
    public int getWireSize() {
        return 4 + deviceCode.length();
    }

    @Override
    public String toString() {
        return String.format("CmdReqOpen={deviceCode:%s}", deviceCode);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeInt(deviceCode.length());
        out.writeCharSequence(deviceCode, StandardCharsets.UTF_8);
    }

    public static CmdReqOpen decode(ByteBuf in) {
        int deviceCodeLength = in.readInt();
        String deviceCode = in.readCharSequence(deviceCodeLength, StandardCharsets.UTF_8).toString();
        return new CmdReqOpen(deviceCode);
    }
}