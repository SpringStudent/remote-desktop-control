package io.github.springstudent.dekstop.server.netty;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import io.github.springstudent.dekstop.common.bean.Constants;
import io.github.springstudent.dekstop.common.command.CmdReqCapture;
import io.github.springstudent.dekstop.common.command.CmdP2pResult;
import io.github.springstudent.dekstop.common.command.CmdResCapture;
import io.github.springstudent.dekstop.common.utils.NettyUtils;
import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ZhouNing
 * @date 2024/12/10 14:15
 **/
public class NettyChannelBrother {
    /**
     * 控制端
     */
    private Channel controller;
    /**
     * 被控制端
     */
    private Channel controlled;

    public NettyChannelBrother(Channel controller, Channel controlled) {
        this.controller = controller;
        this.controlled = controlled;
    }

    public void startControll() {
        NettyUtils.updateControllFlag(controller, Constants.CONTROLLER);
        NettyUtils.updateControllDeviceCode(controller, NettyUtils.getDeviceCode(controlled));
        NettyUtils.updateControllFlag(controlled, Constants.CONTROLLED);
        NettyUtils.updateControllDeviceCode(controlled, NettyUtils.getDeviceCode(controller));
        String p2pSessionId = RandomUtil.fastUUID();
        String p2pToken = RandomUtil.randomString(32);
        long expireAt = System.currentTimeMillis() + Constants.P2P_SIGNAL_EXPIRE_MILLS;
        Map<String, Object> p2pInfo = new HashMap<>();
        p2pInfo.put(Constants.P2P_SESSION_ID, p2pSessionId);
        p2pInfo.put(Constants.P2P_TOKEN, p2pToken);
        p2pInfo.put(Constants.P2P_EXPIRE_AT, expireAt);
        NettyUtils.updateCliInfo(controller, p2pInfo);
        NettyUtils.updateCliInfo(controlled, p2pInfo);
        controller.writeAndFlush(new CmdResCapture(CmdResCapture.START, MapUtil.getInt(NettyUtils.getCliInfo(controlled), "screenNum", 0)));
        controlled.writeAndFlush(new CmdResCapture(CmdResCapture.START_));
        CmdP2pResult tokenIssued = new CmdP2pResult(p2pSessionId, System.currentTimeMillis(), p2pToken, null,
                CmdP2pResult.TOKEN_ISSUED + "|" + expireAt);
        controller.writeAndFlush(tokenIssued);
        controlled.writeAndFlush(tokenIssued);
    }


    public void stopControll(byte stopType) {
        NettyUtils.updateControllFlag(controller, null);
        NettyUtils.updateControllDeviceCode(controller, null);
        NettyUtils.updateControllFlag(controlled, null);
        NettyUtils.updateControllDeviceCode(controlled, null);
        if (controller.isActive()) {
            if (stopType == CmdReqCapture.STOP_CAPTURE_BY_CONTROLLED) {
                controller.writeAndFlush(new CmdResCapture(CmdResCapture.STOP_BYCONTROLLED));
            } else if (stopType == CmdReqCapture.STOP_CAPTURE_CHANNEL_INACTIVE) {
                controller.writeAndFlush(new CmdResCapture(CmdResCapture.STOP_CHANNELINACTIVE));
            } else {
                controller.writeAndFlush(new CmdResCapture(CmdResCapture.STOP));
            }
        }
        if (controlled.isActive()) {
            controlled.writeAndFlush(new CmdResCapture(CmdResCapture.STOP_));
        }
    }

    public Channel getController() {
        return controller;
    }

    public Channel getControlled() {
        return controlled;
    }

}
