package io.github.springstudent.dekstop.client.config;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import io.github.springstudent.dekstop.common.configuration.Preferences;
import io.github.springstudent.dekstop.common.log.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 客户端本地身份与连接历史的持久化,复用 {@link Preferences}(.properties)体系。
 *
 * <p>deviceCode / password 由客户端自持:首启随机生成后不再变化(除非 UI 手动改密码),
 * 建连握手时通过 CmdReqCliInfo 上报给服务端注册。</p>
 *
 * @author ZhouNing
 * @date 2026/09/02
 **/
public class ClientConfigStore {
    public static final String KEY_DEVICE_CODE = "device.code";
    public static final String KEY_DEVICE_PASSWORD = "device.password";
    public static final String KEY_CONNECTION_HISTORY = "connection.history";

    private static final int CODE_LENGTH = 8;
    private static final int PASSWORD_LENGTH = 6;

    private final Preferences preferences;
    private final List<ConnectionHistory> history = new ArrayList<>();

    public ClientConfigStore(Preferences preferences) {
        this.preferences = preferences;
        loadHistory();
        ensureIdentity();
    }

    /**
     * 首启为无身份的实例生成稳定的 deviceCode / password,并立即落盘,
     * 避免进程在 Preferences 异步落盘(2s 轮询)之前退出导致下次重新生成。
     */
    private void ensureIdentity() {
        boolean changed = false;
        String code = preferences.getStringPreference(KEY_DEVICE_CODE, null);
        if (code == null || code.isEmpty()) {
            preferences.setPreference(KEY_DEVICE_CODE, RandomUtil.randomString(CODE_LENGTH));
            changed = true;
        }
        String password = preferences.getStringPreference(KEY_DEVICE_PASSWORD, null);
        if (password == null || password.isEmpty()) {
            preferences.setPreference(KEY_DEVICE_PASSWORD, RandomUtil.randomString(PASSWORD_LENGTH));
            changed = true;
        }
        if (changed) {
            preferences.flush();
        }
    }

    public synchronized String getDeviceCode() {
        return preferences.getStringPreference(KEY_DEVICE_CODE, "");
    }

    public synchronized String getPassword() {
        return preferences.getStringPreference(KEY_DEVICE_PASSWORD, "");
    }

    public synchronized void setPassword(String password) {
        if (password != null && !password.isEmpty()) {
            preferences.setPreference(KEY_DEVICE_PASSWORD, password);
        }
    }

    private void loadHistory() {
        String json = preferences.getStringPreference(KEY_CONNECTION_HISTORY, null);
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            List<ConnectionHistory> loaded = JSONUtil.toList(json, ConnectionHistory.class);
            if (loaded != null) {
                history.addAll(loaded);
            }
        } catch (Exception e) {
            Log.warn("load connection history error,json=" + json, e);
        }
    }

    private void persistHistory() {
        preferences.setPreference(KEY_CONNECTION_HISTORY, JSONUtil.toJsonStr(history));
        // 历史变更属低频操作,同步落盘避免进程退出丢失
        preferences.flush();
    }

    /**
     * 历史列表(按最近连接时间倒序)。返回副本,外部修改不影响存储。
     */
    public synchronized List<ConnectionHistory> listHistory() {
        List<ConnectionHistory> sorted = new ArrayList<>(history);
        sorted.sort(Comparator.comparingLong(ConnectionHistory::getLastConnectedAt).reversed());
        return sorted;
    }

    /**
     * 记录一次连接:已存在则仅刷新时间与「校验通过的密码」(不覆盖备注),否则新增一条(备注为空)。
     *
     * @param password 该次连接校验通过的设备密码,用于下次连接自动预填;为空则沿用已有值/不设置
     */
    public synchronized void addOrUpdateHistory(String deviceCode, String password) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ConnectionHistory entry : history) {
            if (deviceCode.equals(entry.getDeviceCode())) {
                entry.setLastConnectedAt(now);
                if (password != null && !password.isEmpty()) {
                    entry.setPassword(password);
                }
                persistHistory();
                return;
            }
        }
        history.add(new ConnectionHistory(deviceCode, null, password, now));
        persistHistory();
    }

    /**
     * 查询某条历史;不存在返回 null。
     */
    public synchronized ConnectionHistory findHistory(String deviceCode) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return null;
        }
        for (ConnectionHistory entry : history) {
            if (deviceCode.equals(entry.getDeviceCode())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 设置/更新某条历史的备注;若该 code 尚未记录则顺带新增一条。
     */
    public synchronized void updateNote(String deviceCode, String note) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return;
        }
        for (ConnectionHistory entry : history) {
            if (deviceCode.equals(entry.getDeviceCode())) {
                entry.setNote(note);
                persistHistory();
                return;
            }
        }
        history.add(new ConnectionHistory(deviceCode, note, System.currentTimeMillis()));
        persistHistory();
    }

    public synchronized void removeHistory(String deviceCode) {
        history.removeIf(entry -> deviceCode.equals(entry.getDeviceCode()));
        persistHistory();
    }
}
