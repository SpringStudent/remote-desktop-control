package io.github.springstudent.dekstop.server.netty;

import cn.hutool.core.thread.NamedThreadFactory;
import io.github.springstudent.dekstop.common.protocol.NettyDecoder;
import io.github.springstudent.dekstop.common.protocol.NettyEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * @author ZhouNing
 * @date 2024/12/10 14:07
 **/
@Component
public class NettyServer implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    @Value("${netty.server.port}")
    private Integer serverPort;

    @Value("${netty.server.ip}")
    private String serverIp;

    /**
     * 连接通道
     */
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    @Override
    public void afterPropertiesSet() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bossGroup = new NioEventLoopGroup(1, new NamedThreadFactory("netty-boss-", true));
        workerGroup = new NioEventLoopGroup(10, new NamedThreadFactory("netty-worker-", true));
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(new NettyDecoder());
                        socketChannel.pipeline().addLast(new NettyEncoder());
                        socketChannel.pipeline().addLast(new NettyIdleStateHandler());
                        socketChannel.pipeline().addLast(new NettyServerHandler());
                    }
                });
        ChannelFuture channelFuture = bootstrap.bind(new InetSocketAddress(serverIp, serverPort));
        channelFuture.syncUninterruptibly();
        this.channel = channelFuture.channel();
        logger.info("server start success,host={},port={}", serverIp, serverPort);
    }

    @Override
    public void destroy() throws Exception {
        try {
            if (channel != null) {
                channel.close().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while closing channel", e);
        } finally {
            shutdownEventLoopGroup(bossGroup, "bossGroup");
            shutdownEventLoopGroup(workerGroup, "workerGroup");
        }
    }

    private void shutdownEventLoopGroup(EventLoopGroup group, String name) {
        if (group != null) {
            try {
                group.shutdownGracefully().sync();
                logger.info("{} shutdown successfully", name);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while shutting down {}", name, e);
            }
        }
    }


}
