package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class SetClipboardRequest extends Identify implements Serializable {
    private String clipboardType;
    private String content;
    private String clipboardServer;

    public SetClipboardRequest(String clipboardType, String content, String clipboardServer) {
        this.clipboardType = clipboardType;
        this.content = content;
        this.clipboardServer = clipboardServer;
        this.id = genId();
    }

    public String getClipboardType() {
        return clipboardType;
    }

    public String getContent() {
        return content;
    }

    public String getClipboardServer() {
        return clipboardServer;
    }
}
