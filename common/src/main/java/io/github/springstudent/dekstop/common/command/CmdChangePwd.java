package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


/**
 * @author ZhouNing
 * @date 2025/7/8 9:47
 **/
public class CmdChangePwd extends Cmd {

    private String password;

    public CmdChangePwd(String password) {
        this.password = password;
    }


    public String getPassword() {
        return password;
    }

    @Override
    public CmdType getType() {
        return CmdType.ChangePwd;
    }

    @Override
    public int getWireSize() {
        return 4 + password.length();
    }

    @Override
    public String toString() {
        return String.format("CmdChangePwd={password:%s}", password);
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeInt(password.length());
        out.writeCharSequence(password, StandardCharsets.UTF_8);
    }

    public static CmdChangePwd decode(ByteBuf in) {
        int passwordLength = in.readInt();
        String password = in.readCharSequence(passwordLength, StandardCharsets.UTF_8).toString();
        return new CmdChangePwd(password);
    }
}
