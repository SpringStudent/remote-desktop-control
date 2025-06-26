package io.github.springstudent.dekstop.server.netty;

import io.github.springstudent.dekstop.common.bean.Constants;
import io.github.springstudent.dekstop.common.command.CmdReqCapture;
import io.github.springstudent.dekstop.common.command.CmdResCapture;
import io.github.springstudent.dekstop.common.utils.NettyUtils;
import io.netty.channel.Channel;

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

    public void startControl() {
        NettyUtils.updateControllFlag(controller, Constants.CONTROLLER);
        NettyUtils.updateControllDeviceCode(controller, NettyUtils.getDeviceCode(controlled));
        NettyUtils.updateControllFlag(controlled, Constants.CONTROLLED);
        NettyUtils.updateControllDeviceCode(controlled, NettyUtils.getDeviceCode(controller));
        controller.writeAndFlush(new CmdResCapture(CmdResCapture.START,NettyUtils.getCliInfo(controlled).getScreenNum()));
        controlled.writeAndFlush(new CmdResCapture(CmdResCapture.START_));
    }


    public void stopControl(byte stopType) {
        NettyUtils.updateControllFlag(controller, null);
        NettyUtils.updateControllDeviceCode(controller, null);
        NettyUtils.updateControllFlag(controlled, null);
        NettyUtils.updateControllDeviceCode(controlled, null);
        if(controller.isActive()){
            if (stopType == CmdReqCapture.STOP_CAPTURE_BY_CONTROLLED) {
                controller.writeAndFlush(new CmdResCapture(CmdResCapture.STOP_BYCONTROLLED));
            } else if (stopType == CmdReqCapture.STOP_CAPTURE_CHANNEL_INACTIVE) {
                controller.writeAndFlush(new CmdResCapture(CmdResCapture.STOP_CHANNELINACTIVE));
            } else {
                controller.writeAndFlush(new CmdResCapture(CmdResCapture.STOP));
            }
        }
        if(controlled.isActive()){
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
