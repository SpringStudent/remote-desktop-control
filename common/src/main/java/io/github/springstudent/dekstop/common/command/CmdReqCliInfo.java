package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author ZhouNing
 * @date 2025/4/2 9:16
 **/
public class CmdReqCliInfo extends Cmd {

    private int screenNum;

    private String osName;

    /**
     * 客户端持久化的本机 deviceCode(可为空:服务端将回退为随机生成)
     */
    private String deviceCode;

    /**
     * 客户端持久化的本机访问密码(可为空:服务端将回退为随机生成)
     */
    private String password;

    public CmdReqCliInfo(int screenNum, String osName, String deviceCode, String password) {
        this.screenNum = screenNum;
        this.osName = osName;
        this.deviceCode = deviceCode;
        this.password = password;
    }

    @Override
    public CmdType getType() {
        return CmdType.ReqCliInfo;
    }

    @Override
    public int getWireSize() {
        return 8 + osName.getBytes(StandardCharsets.UTF_8).length
                + 8 + deviceCode.getBytes(StandardCharsets.UTF_8).length
                + 8 + password.getBytes(StandardCharsets.UTF_8).length;
    }

    public int getScreenNum() {
        return screenNum;
    }

    public String getOsName() {
        return osName;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return String.format("CmdClientInfo={screenNum:%d,osName:%s,deviceCode:%s,password:%s}", screenNum, osName, deviceCode, password);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeInt(screenNum);
        byte[] osNameBytes = osName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(osNameBytes.length);
        out.writeBytes(osNameBytes);
        byte[] deviceCodeBytes = deviceCode.getBytes(StandardCharsets.UTF_8);
        out.writeInt(deviceCodeBytes.length);
        out.writeBytes(deviceCodeBytes);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        out.writeInt(passwordBytes.length);
        out.writeBytes(passwordBytes);
    }

    public static CmdReqCliInfo decode(ByteBuf in) {
        int screenNum = in.readInt();
        int osNameLength = in.readInt();
        byte[] osNameBytes = new byte[osNameLength];
        in.readBytes(osNameBytes);
        String osName = new String(osNameBytes, StandardCharsets.UTF_8);
        int deviceCodeLength = in.readInt();
        byte[] deviceCodeBytes = new byte[deviceCodeLength];
        in.readBytes(deviceCodeBytes);
        String deviceCode = new String(deviceCodeBytes, StandardCharsets.UTF_8);
        int passwordLength = in.readInt();
        byte[] passwordBytes = new byte[passwordLength];
        in.readBytes(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.UTF_8);
        return new CmdReqCliInfo(screenNum, osName, deviceCode, password);
    }
}
