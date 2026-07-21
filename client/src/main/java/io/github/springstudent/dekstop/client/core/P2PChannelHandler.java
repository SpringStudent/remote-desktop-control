package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.common.command.Cmd;
import io.github.springstudent.dekstop.common.log.Log;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import static java.lang.String.format;

/**
 * Netty handler for the P2P direct channel.
 * Uses the same message dispatch as the server relay channel, so both
 * RemoteController and RemoteControlled process messages identically.
 *
 * @author ZhouNing
 * @date 2026/07/21
 */
public class P2PChannelHandler extends SimpleChannelInboundHandler<Cmd> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Cmd cmd) {
        try {
            RemoteClient.getRemoteClient().handleCmd(ctx, cmd);
        } catch (Exception e) {
            Log.error("P2P channelRead0 error", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Log.info("P2P channel disconnected");
        RemoteClient.getRemoteClient().onP2PDisconnected();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Log.error(format("P2P channel exception: %s", cause.getMessage()));
        ctx.close();
    }
}
