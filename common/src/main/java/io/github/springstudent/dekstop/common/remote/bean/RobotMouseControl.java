package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class RobotMouseControl implements Serializable {
    private static final int PRESSED = 1;

    private static final int RELEASED = 1 << 1;

    public static final int BUTTON1 = 1 << 2;

    public static final int BUTTON2 = 1 << 3;

    public static final int BUTTON3 = 1 << 4;

    private static final int WHEEL = 1 << 5;

    private final int x;

    private final int y;

    private final int info;

    private final int rotations;

    public RobotMouseControl(int x, int y, int info, int rotations) {
        this.x = x;
        this.y = y;
        this.info = info;
        this.rotations = rotations;
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
}
