package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class SendClipboardResponse extends Identify implements Serializable {

    private Byte code;

    private String clipboarType;

    private String content;

    public SendClipboardResponse(Byte code, String clipboarType, String content, Integer id) {
        this.code = code;
        this.clipboarType = clipboarType;
        this.content = content;
        this.id = id;
    }

    public byte getCode() {
        return code;
    }

    public String getClipboarType() {
        return clipboarType;
    }

    public String getContent() {
        return content;
    }
}
