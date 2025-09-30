package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class RobotKeyControl implements Serializable {

    private int keyCode;

    private boolean pressed;
    public RobotKeyControl(int keyCode,boolean pressed) {
        this.keyCode = keyCode;
        this.pressed = pressed;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public boolean getPressed() {
        return pressed;
    }
}
