package io.github.springstudent.dekstop.client.core;


import io.github.springstudent.dekstop.common.command.Cmd;
import io.github.springstudent.dekstop.common.command.CmdType;
import io.netty.channel.Channel;

import java.util.List;

/**
 * @author ZhouNing
 * @date 2024/12/10 14:20
 **/
public abstract class RemoteControll {

    protected Channel channel;

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    protected void fireCmd(Cmd cmd) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(cmd);
        } else {

        }
    }

    public abstract void handleCmd(Cmd cmd);


    public abstract void stop();

    public abstract void start();
}