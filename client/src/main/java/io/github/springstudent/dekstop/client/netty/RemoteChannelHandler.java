package io.github.springstudent.dekstop.client.netty;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.client.utils.ScreenUtilities;
import io.github.springstudent.dekstop.common.command.Cmd;
import io.github.springstudent.dekstop.common.command.CmdReqCliInfo;
import io.github.springstudent.dekstop.common.log.Log;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author ZhouNing
 * @date 2024/12/11 13:17
 **/
public class RemoteChannelHandler extends SimpleChannelInboundHandler<Cmd> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Cmd cmd) throws Exception {
        try {
            RemoteClient.getRemoteClient().handleCmd(ctx, cmd);
        } catch (Exception e) {
            Log.error("client channelRead0 error", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RemoteClient.getRemoteClient().stopClient();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        RemoteClient.getRemoteClient().setChannel(ctx.channel());
        ctx.channel().writeAndFlush(new CmdReqCliInfo(ScreenUtilities.getNumberOfScreens(), System.getProperty("os.name")));
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Log.error("client exceptionCaught error", cause);
        super.exceptionCaught(ctx, cause);
    }

}
