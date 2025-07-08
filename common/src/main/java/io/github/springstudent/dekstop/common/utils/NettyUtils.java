package io.github.springstudent.dekstop.common.utils;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ZhouNing
 * @date 2024/12/11 16:37
 **/
public class NettyUtils {
    private static final AttributeKey<String> ATTR_KEY_READER_TIME = AttributeKey.valueOf("readerTime");
    private static final AttributeKey<String> ATTR_KEY_DEVICE_CODE = AttributeKey.valueOf("deviceCode");
    private static final AttributeKey<String> ATTR_KEY_CONTROLL_FLAG = AttributeKey.valueOf("controllFlag");
    private static final AttributeKey<String> ATTR_KEY_CONTROLL_DEVICECODE = AttributeKey.valueOf("contollDeviceCode");
    private static final AttributeKey<Map<String, Object>> ATTR_KEY_CLI_INFO = AttributeKey.valueOf("cliInfo");

    public static void updateReaderTime(Channel channel, Long time) {
        channel.attr(ATTR_KEY_READER_TIME).set(time.toString());
    }

    public static Long getReaderTime(Channel channel) {
        String value = channel.attr(ATTR_KEY_READER_TIME).get();
        if (value != null) {
            return Long.valueOf(value);
        }
        return null;
    }

    public static void updateDeviceCode(Channel channel, String deviceCode) {
        channel.attr(ATTR_KEY_DEVICE_CODE).set(deviceCode);
    }

    public static String getDeviceCode(Channel channel) {
        return channel.attr(ATTR_KEY_DEVICE_CODE).get();
    }

    public static void updateControllFlag(Channel channel, String controllFlag) {
        channel.attr(ATTR_KEY_CONTROLL_FLAG).set(controllFlag);
    }

    public static String getControllFlag(Channel channel) {
        return channel.attr(ATTR_KEY_CONTROLL_FLAG).get();
    }

    public static void updateControllDeviceCode(Channel channel, String deviceCode) {
        channel.attr(ATTR_KEY_CONTROLL_DEVICECODE).set(deviceCode);
    }

    public static String getControllDeviceCode(Channel channel) {
        return channel.attr(ATTR_KEY_CONTROLL_DEVICECODE).get();
    }

    public static void updateCliInfo(Channel channel, Map<String, Object> cliInfo) {
        Map<String, Object> map = getCliInfo(channel);
        if (map == null) {
            map = new HashMap<>();
        }
        map.putAll(cliInfo);
        channel.attr(ATTR_KEY_CLI_INFO).set(map);
    }

    public static Map<String, Object> getCliInfo(Channel channel) {
        return channel.attr(ATTR_KEY_CLI_INFO).get();
    }
}

