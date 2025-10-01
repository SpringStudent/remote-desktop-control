package io.github.springstudent.dekstop.client;

import io.github.springstudent.dekstop.client.core.*;
import io.github.springstudent.dekstop.client.netty.RemoteChannelHandler;
import io.github.springstudent.dekstop.client.netty.RemoteStateIdleHandler;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.protocol.NettyDecoder;
import io.github.springstudent.dekstop.common.protocol.NettyEncoder;
import io.github.springstudent.dekstop.common.utils.NettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import javax.swing.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * @author ZhouNing
 * @date 2024/12/6
 */
public class RemoteClient extends RemoteFrame {
    private static RemoteClient remoteClient;

    private String serverIp;

    private Integer serverPort;

    private String clipboardServer;

    private boolean connectStatus;

    private RemoteScreen remoteScreen;

    private RemoteControlled controlled;

    private RemoteController controller;

    private RobotsClient robotsClient;

    public RemoteClient(String serverIp, Integer serverPort, String clipboardServer, int robotsClientPort) {
        remoteClient = this;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.clipboardServer = clipboardServer;
        this.robotsClient = new RobotsClient(robotsClientPort);
        this.controlled = new RemoteControlled(robotsClient);
        this.controller = new RemoteController(robotsClient);
        this.remoteScreen = new RemoteScreen();
        this.connectServer();
    }

    @Override
    public void changePassword(String deviceCode, String password) {
        CmdChangePwd cmd = new CmdChangePwd(password);
        controller.fireCmd(cmd);
    }

    @Override
    public boolean isConnect() {
        return connectStatus;
    }

    @Override
    protected void beforeOpenRemoteScreen(String text) {
        controller.fireCmd(new CmdReqOpen(text));
    }

    @Override
    public void openRemoteScreen(String deviceCode, String password) {
        controller.openSession(deviceCode, password);
    }

    @Override
    public void closeRemoteScreen(String deviceCode) {
        controlled.closeSession(deviceCode);
    }

    @Override
    public void closeRemoteScreen() {
        controller.closeSession();
    }

    public RemoteScreen getRemoteScreen() {
        return remoteScreen;
    }

    /**
     * 连接至server
     */
    public void connectServer() {
        final Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(new NettyDecoder());
                        socketChannel.pipeline().addLast(new NettyEncoder());
                        socketChannel.pipeline().addLast(new RemoteStateIdleHandler());
                        socketChannel.pipeline().addLast(new RemoteChannelHandler());
                    }
                });
        //连接至远程客户端
        connect(bootstrap, 0);
    }

    private void connect(Bootstrap bootstrap, int retry) {
        bootstrap.connect(serverIp, serverPort).addListener(future -> {
            if (future.isSuccess()) {
                Log.info("connect to remote server success");
                this.connectStatus = true;
            } else {
                this.connectStatus = false;
                Integer order = retry + 1;
                Log.info(format("reconnect to remote server serverIp=%s ,serverPort=%d,retry times =%d", serverIp, serverPort, order));
                bootstrap.config().group().schedule(() -> connect(bootstrap, order), 5, TimeUnit
                        .SECONDS);
            }
        });
    }

    public RemoteController getController() {
        return controller;
    }

    public RemoteControlled getControlled() {
        return controlled;
    }

    public void handleCmd(ChannelHandlerContext ctx, Cmd cmd) {
        if (cmd.getType().equals(CmdType.ResCliInfo)) {
            CmdResCliInfo clientInfo = (CmdResCliInfo) cmd;
            setDeviceCodeAndPassword(clientInfo.getDeviceCode(), clientInfo.getPassword());
            NettyUtils.updateDeviceCode(ctx.channel(), clientInfo.getDeviceCode());
            updateConnectionStatus(true);
        } else {
            controller.handleCmd(cmd);
            controlled.handleCmd(cmd);
        }
    }

    public void setChannel(Channel channel) {
        controller.setChannel(channel);
        controlled.setChannel(channel);
    }

    public void stopClient() {
        showMessageDialog("连接异常", JOptionPane.ERROR_MESSAGE);
        remoteScreen.close();
        controller.stop();
        controlled.stop();
        connectStatus = false;
        updateConnectionStatus(false);
        setControlledAndCloseSessionLabelVisible(false);
        setChannel(null);
        connectServer();
    }

    public String getClipboardServer() {
        return clipboardServer;
    }

    public static RemoteClient getRemoteClient() {
        return remoteClient;
    }


    public static void main(String[] args) throws Exception {
        int robotPort = 49152;
        if (System.getProperty("robotPort") != null) {
            robotPort = Integer.parseInt(System.getProperty("robotPort"));
        }
        RemoteClient remoteClient = new RemoteClient("192.168.0.110", 54321, "http://192.168.0.110:12345/remote-desktop-control", robotPort);
    }

}