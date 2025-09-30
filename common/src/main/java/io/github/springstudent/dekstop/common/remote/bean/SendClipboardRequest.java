package io.github.springstudent.dekstop.common.remote.bean;

import java.io.Serializable;

public class SendClipboardRequest extends Identify implements Serializable {

    protected Integer id;
    private final String deviceCode;

    private final String clipboardServer;

    public SendClipboardRequest(String deviceCode, String clipboardServer) {
        this.deviceCode = deviceCode;
        this.clipboardServer = clipboardServer;
        this.id = genId();
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getClipboardServer() {
        return clipboardServer;
    }

    public Integer getId() {
        return id;
    }
}
