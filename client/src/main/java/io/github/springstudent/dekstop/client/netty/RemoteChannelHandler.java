package io.github.springstudent.dekstop.client.netty;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.common.command.Cmd;
import io.github.springstudent.dekstop.common.command.CmdResCliInfo;
import io.github.springstudent.dekstop.common.command.CmdType;
import io.github.springstudent.dekstop.common.log.Log;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import javax.swing.*;

import static java.lang.String.format;

/**
 * @author ZhouNing
 * @date 2024/12/11 13:17
 **/
public class RemoteChannelHandler extends SimpleChannelInboundHandler<Cmd> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Cmd cmd) throws Exception {
        try {
            Log.info(format("client recieved msg=%s", cmd));
            if (cmd.getType().equals(CmdType.ResCliInfo)) {
                CmdResCliInfo clientInfo = (CmdResCliInfo) cmd;
                RemoteClient.getRemoteClient().setDeviceCodeAndPassword(clientInfo.getDeviceCode(), clientInfo.getPassword());
                RemoteClient.getRemoteClient().updateConnectionStatus(true);
            }
            RemoteClient.getRemoteClient().handleCmd(cmd);
        }catch (Exception e){
            Log.info("client channelRead0 errro");
            e.printStackTrace();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RemoteClient.getRemoteClient().showMessageDialog("连接异常", JOptionPane.ERROR_MESSAGE);
        RemoteClient.getRemoteClient().getRemoteScreen().close();
        RemoteClient.getRemoteClient().updateConnectionStatus(false);
        RemoteClient.getRemoteClient().setControllChannel(null);
        RemoteClient.getRemoteClient().connectServer();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        RemoteClient.getRemoteClient().setControllChannel(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        super.exceptionCaught(ctx, cause);
    }
}
