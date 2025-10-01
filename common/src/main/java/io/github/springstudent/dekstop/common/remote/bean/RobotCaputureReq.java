package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

/**
 * @author ZhouNing
 * @date 2025/9/30 9:06
 **/
public class RobotCaputureReq implements Serializable {

    private Long id;

    public RobotCaputureReq() {
        this.id = System.currentTimeMillis();
    }

    public Long getId() {
        return id;
    }
}
