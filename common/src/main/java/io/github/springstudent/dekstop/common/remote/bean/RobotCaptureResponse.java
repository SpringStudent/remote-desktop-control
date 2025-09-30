package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

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
