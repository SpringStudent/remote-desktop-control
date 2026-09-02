package io.github.springstudent.dekstop.client.config;

/**
 * 一条本地连接历史:被控制(或曾经尝试控制)的设备 code 与用户备注。
 *
 * @author ZhouNing
 * @date 2026/09/02
 **/
public class ConnectionHistory {

    private String deviceCode;

    /**
     * 用户备注,如「电脑2」,可为空
     */
    private String note;

    /**
     * 该设备最近一次连接成功时使用并校验通过的密码,用于下次连接时预填,可为空
     */
    private String password;

    /**
     * 最近一次连接时间戳(ms)
     */
    private long lastConnectedAt;

    public ConnectionHistory() {
    }

    public ConnectionHistory(String deviceCode, String note, long lastConnectedAt) {
        this.deviceCode = deviceCode;
        this.note = note;
        this.lastConnectedAt = lastConnectedAt;
    }

    public ConnectionHistory(String deviceCode, String note, String password, long lastConnectedAt) {
        this.deviceCode = deviceCode;
        this.note = note;
        this.password = password;
        this.lastConnectedAt = lastConnectedAt;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getLastConnectedAt() {
        return lastConnectedAt;
    }

    public void setLastConnectedAt(long lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
    }
}
