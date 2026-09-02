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

    /**
     * 按客户端上报的持久化 deviceCode 注册连接。
     *
     * @return 同码下先前已存在的 channel(可能为 null),由调用方决定是否顶替/关闭
     */
    public static synchronized Channel registerChannel(String deviceCode, Channel channel) {
        return deviceCodeChannelMap.put(deviceCode, channel);
    }

    public static Channel getChannel(String deviceCode) {
        return deviceCodeChannelMap.get(deviceCode);
    }

    public static synchronized void removeChannelAndBrother(Channel channel) {
        String deviceCode = NettyUtils.getDeviceCode(channel);
        // 只有映射仍指向本 channel 时才移除:同码设备重连后可能已把映射换到新 channel,
        // 若无条件按 code 删除会把新连接误删。
        if (deviceCode != null && deviceCodeChannelMap.get(deviceCode) == channel) {
            deviceCodeChannelMap.remove(deviceCode);
        }
        String controllFlag = NettyUtils.getControllFlag(channel);
        if (StrUtil.isNotEmpty(controllFlag)) {
            NettyChannelBrother channelBrother = null;
            if (controllFlag.equals(Constants.CONTROLLED)) {
                channelBrother = channelBrotherMap.get(deviceCode);
                if (channelBrother != null && channelBrother.getControlled() == channel) {
                    channelBrotherMap.remove(deviceCode);
                } else {
                    channelBrother = null;
                }
            } else if (controllFlag.equals(Constants.CONTROLLER)) {
                String controlledCode = NettyUtils.getControllDeviceCode(channel);
                channelBrother = channelBrotherMap.get(controlledCode);
                if (channelBrother != null && channelBrother.getController() == channel) {
                    channelBrotherMap.remove(controlledCode);
                } else {
                    channelBrother = null;
                }
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
