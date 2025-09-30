package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class SetClipboardResponse extends Identify implements Serializable {
    private Boolean result;

    private Integer id;

    public SetClipboardResponse(Boolean result, Integer id) {
        this.result = result;
        this.id = id;
    }

    public Boolean getResult() {
        return result;
    }

    public Integer getId() {
        return id;
    }
}
