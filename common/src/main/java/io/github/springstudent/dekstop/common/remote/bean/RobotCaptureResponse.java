package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

/**
 * @author ZhouNing
 * @date 2025/9/30 9:06
 **/
public class RobotCaptureResponse implements Serializable {
    private byte[] screenBytes;

    private Long id;

    public RobotCaptureResponse(byte[] screenBytes, Long id) {
        this.screenBytes = screenBytes;
        this.id = id;
    }

    public byte[] getScreenBytes() {
        return screenBytes;
    }

    public Long getId() {
        return id;
    }
}
