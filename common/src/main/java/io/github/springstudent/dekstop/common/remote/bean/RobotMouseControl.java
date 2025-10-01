package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

/**
 * @author ZhouNing
 * @date 2025/9/30 9:06
 **/
public class RobotMouseControl implements Serializable {

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
}
