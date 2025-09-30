package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class RobotCaputureReq implements Serializable {

    private Long id;

    public RobotCaputureReq() {
        this.id = System.currentTimeMillis();
    }

    public Long getId() {
        return id;
    }
}
