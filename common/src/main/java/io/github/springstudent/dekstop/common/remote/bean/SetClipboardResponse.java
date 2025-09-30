package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class SetClipboardResponse extends Identify implements Serializable {
    private Boolean result;

    public SetClipboardResponse(Boolean result, Integer id) {
        this.result = result;
        this.id = id;
    }

    public Boolean getResult() {
        return result;
    }
}
