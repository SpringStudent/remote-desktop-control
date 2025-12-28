package io.github.springstudent.dekstop.server.netty;


import cn.hutool.core.util.StrUtil;
import io.github.springstudent.dekstop.common.bean.Constants;
import io.github.springstudent.dekstop.common.command.CmdReqCapture;
import io.github.springstudent.dekstop.common.utils.NettyUtils;
import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ZhouNing
 * @date 2024/12/12 9:18
 **/
public class NettyChannelManager {

    private static Map<String, Channel> deviceCodeChannelMap = new ConcurrentHashMap<>(32);

    private static Map<String, NettyChannelBrother> channelBrotherMap = new ConcurrentHashMap<>(32);

    public static void addChannel(String deviceCode, Channel channel) {
        deviceCodeChannelMap.put(deviceCode, channel);
    }

    public static Channel getChannel(String deviceCode) {
        return deviceCodeChannelMap.get(deviceCode);
    }

    public static synchronized void removeChannelAndBrother(Channel channel) {
        String controllFlag = NettyUtils.getControllFlag(channel);
        deviceCodeChannelMap.remove(NettyUtils.getDeviceCode(channel));
        if (StrUtil.isNotEmpty(controllFlag)) {
            NettyChannelBrother channelBrother = null;
            if (controllFlag.equals(Constants.CONTROLLED)) {
                channelBrother = channelBrotherMap.remove(NettyUtils.getDeviceCode(channel));
            } else if (controllFlag.equals(Constants.CONTROLLER)) {
                channelBrother = channelBrotherMap.remove(NettyUtils.getControllDeviceCode(channel));
            }
            if (channelBrother != null) {
                channelBrother.stopControl(CmdReqCapture.STOP_CAPTURE_CHANNEL_INACTIVE);
            }
        }
    }

    public static void bindChannelBrother(Channel controller, Channel controlled) {
        NettyChannelBrother channelBrother = new NettyChannelBrother(controller, controlled);
        channelBrother.startControl();
        channelBrotherMap.putIfAbsent(NettyUtils.getDeviceCode(controlled), channelBrother);

    }

    public static NettyChannelBrother unbindChannelBrother(byte stopType, Channel controlled) {
        NettyChannelBrother channelBrother = channelBrotherMap.get(NettyUtils.getDeviceCode(controlled));
        if (channelBrother != null) {
            channelBrother.stopControl(stopType);
            channelBrotherMap.remove(NettyUtils.getDeviceCode(controlled));
        }
        return channelBrother;
    }

    public static Channel getControllerChannel(Channel controlled) {
        NettyChannelBrother channelBrother = channelBrotherMap.get(NettyUtils.getDeviceCode(controlled));
        if (channelBrother != null) {
            return channelBrother.getController();
        }
        return null;
    }

    public static Channel getControlledChannel(Channel controller) {
        NettyChannelBrother channelBrother = channelBrotherMap.get(NettyUtils.getControllDeviceCode(controller));
        if (channelBrother != null) {
            return channelBrother.getControlled();
        }
        return null;
    }

    public static Map<String, Channel> getDeviceCodeChannelMap() {
        return deviceCodeChannelMap;
    }

    public static Map<String, NettyChannelBrother> getChannelBrotherMap() {
        return channelBrotherMap;
    }

}
