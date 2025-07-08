package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author ZhouNing
 * @date 2024/12/12 9:03
 **/
public class CmdReqCapture extends Cmd {

    public static final byte START_CAPTURE = 0x00;

    public static final byte STOP_CAPTURE = 0x01;

    public static final byte STOP_CAPTURE_BY_CONTROLLED = 0x02;

    public static final byte STOP_CAPTURE_CHANNEL_INACTIVE = 0x03;

    private String deviceCode;

    private byte caputureOp;

    private String password;

    public CmdReqCapture(String deviceCode, byte caputureOp) {
        this.deviceCode = deviceCode;
        this.caputureOp = caputureOp;
    }

    public CmdReqCapture(String deviceCode, byte caputureOp, String pasword) {
        this.deviceCode = deviceCode;
        this.caputureOp = caputureOp;
        this.password = pasword;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public byte getCaputureOp() {
        return caputureOp;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public CmdType getType() {
        return CmdType.ReqCapture;
    }

    @Override
    public int getWireSize() {
        return deviceCode.length() + 4 + 1 + (password != null ? password.length() : 0) + 4;
    }

    @Override
    public String toString() {
        return String.format("CmdReqCapture={deviceCode:%s,captureOp:%s,deviceCode:%s}", deviceCode, caputureOp, password);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeInt(deviceCode.length());
        out.writeCharSequence(deviceCode, StandardCharsets.UTF_8);
        out.writeByte(caputureOp);
        out.writeInt(password != null ? password.length() : 0);
        if (password != null) {
            out.writeCharSequence(password, StandardCharsets.UTF_8);
        }
    }

    public static CmdReqCapture decode(ByteBuf in) {
        int deviceCodeLength = in.readInt();
        String deviceCode = in.readCharSequence(deviceCodeLength, StandardCharsets.UTF_8).toString();
        byte caputureOp = in.readByte();
        int passwordLength = in.readInt();
        String password = null;
        if (passwordLength > 0) {
            password = in.readCharSequence(passwordLength, StandardCharsets.UTF_8).toString();
        }
        return new CmdReqCapture(deviceCode, caputureOp, password);
    }
}
