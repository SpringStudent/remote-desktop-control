package io.github.springstudent.dekstop.client.netty;

import io.github.springstudent.dekstop.common.bean.Constants;
import io.github.springstudent.dekstop.common.command.CmdReqPing;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author ZhouNing
 * @date 2024/12/11 16:50
 **/
public class RemoteStateIdleHandler extends IdleStateHandler {

    private static final Logger log = LoggerFactory.getLogger(RemoteStateIdleHandler.class);

    public RemoteStateIdleHandler() {
        super(0, Constants.HEARTBEAT_DURATION_SECONDS, 0);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        ctx.writeAndFlush(new CmdReqPing()).addListeners((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.error("send ping error,close Channel");
                future.channel().close();
            }
        });
        super.userEventTriggered(ctx, evt);
    }
}

