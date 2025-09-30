package io.github.springstudent.dekstop.common.command;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * @author ZhouNing
 * @date 2024/12/13 23:18
 **/
public class CmdMouseControl extends Cmd {
    public enum ButtonState {
        PRESSED, RELEASED,
    }

    private static final int PRESSED = 1;

    private static final int RELEASED = 1 << 1;

    public static final int BUTTON1 = 1 << 2;

    public static final int BUTTON2 = 1 << 3;

    public static final int BUTTON3 = 1 << 4;

    public static final int UNDEFINED = -1;

    private static final int WHEEL = 1 << 5;

    private final int x;

    private final int y;

    private final int info;

    private final int rotations;

    public CmdMouseControl(int x, int y) {
        this(x, y, 0, 0);
    }

    public CmdMouseControl(int x, int y, ButtonState buttonState, int button) {
        this(x, y, (buttonState == ButtonState.PRESSED ? PRESSED : RELEASED) | button, 0);
    }

    public CmdMouseControl(int x, int y, int rotations) {
        this(x, y, WHEEL, rotations);
    }

    private CmdMouseControl(int x, int y, int info, int rotations) {
        this.x = x;
        this.y = y;
        this.info = info;
        this.rotations = rotations;
    }

    @Override
    public CmdType getType() {
        return CmdType.MouseControl;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getInfo() {
        return info;
    }

    public int getRotations() {
        return rotations;
    }

    public boolean isPressed() {
        return (info & PRESSED) == PRESSED;
    }

    public boolean isReleased() {
        return (info & RELEASED) == RELEASED;
    }

    public boolean isButton1() {
        return (info & BUTTON1) == BUTTON1;
    }

    public boolean isButton2() {
        return (info & BUTTON2) == BUTTON2;
    }

    public boolean isButton3() {
        return (info & BUTTON3) == BUTTON3;
    }

    public boolean isWheel() {
        return (info & WHEEL) == WHEEL;
    }

    @Override
    public int getWireSize() {
        if (isWheel()) {
            return 12;
        }
        return 8;
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeShort(x);
        out.writeShort(y);
        out.writeInt(info);
        if (isWheel()) {
            out.writeInt(rotations);
        }
    }

    public static CmdMouseControl decode(ByteBuf in) throws IOException {
        final int x = in.readShort();
        final int y = in.readShort();
        final int info = in.readInt();
        final int rotations;
        if ((info & WHEEL) == WHEEL) {
            rotations = in.readInt();
        } else {
            rotations = 0;
        }
        return new CmdMouseControl(x, y, info, rotations);
    }

    public String toString() {
        if (isWheel()) {
            return String.format("CmdMouseControl={x:%d,y:%d,WHEEL:%d}", x, y, rotations);
        }
        return String.format("CmdMouseControl={x:%d,y:%d,%s %s}", x, y, toStringPressed(), toStringButton());
    }

    private String toStringPressed() {
        if (isPressed()) {
            return "PRESSED";
        }
        if (isReleased()) {
            return "RELEASED";
        }
        return "";
    }

    private String toStringButton() {
        if (isButton1()) {
            return "BUTTON1";
        }
        if (isButton2()) {
            return "BUTTON2";
        }
        if (isButton3()) {
            return "BUTTON3";
        }
        return "";
    }
}
