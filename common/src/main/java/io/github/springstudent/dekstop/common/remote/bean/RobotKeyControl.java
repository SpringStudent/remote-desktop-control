package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class RobotKeyControl implements Serializable {

    private int keyCode;

    private Integer pressed;

    public RobotKeyControl(int keyCode, Integer pressed) {
        this.keyCode = keyCode;
        this.pressed = pressed;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public Integer getPressed() {
        return pressed;
    }
}
