package io.github.springstudent.dekstop.client;

import io.github.springstudent.dekstop.client.config.ClientConfigStore;
import io.github.springstudent.dekstop.client.core.*;
import io.github.springstudent.dekstop.client.netty.RemoteChannelHandler;
import io.github.springstudent.dekstop.client.netty.RemoteStateIdleHandler;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.configuration.Preferences;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.protocol.NettyDecoder;
import io.github.springstudent.dekstop.common.protocol.NettyEncoder;
import io.github.springstudent.dekstop.common.remote.bean.RobotCaptureResponse;
import io.github.springstudent.dekstop.common.remote.bean.RobotCaputureReq;
import io.github.springstudent.dekstop.common.remote.bean.RobotKeyControl;
import io.github.springstudent.dekstop.common.remote.bean.RobotMouseControl;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
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

    private ClientConfigStore configStore;

    public RemoteClient(String serverIp, Integer serverPort, String clipboardServer, int robotPort, boolean robotEnabled, String p2pBindAddress, int p2pBindPort) {
        remoteClient = this;
        this.configStore = new ClientConfigStore(Preferences.getPreferences());
        // 先展示持久化的稳定身份(未连上服务端也可读);连上后服务端回显同值,不会跳动
        setDeviceCodeAndPassword(configStore.getDeviceCode(), configStore.getPassword());
        reloadRemoteDeviceHistory();
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.clipboardServer = clipboardServer;
        this.robotsClient = new RobotsClient(robotPort, robotEnabled);
        this.controlled = new RemoteControlled(p2pBindAddress, p2pBindPort);
        this.controller = new RemoteController();
        this.remoteScreen = new RemoteScreen();
        this.connectServer();
    }

    @Override
    public void changePassword(String deviceCode, String password) {
        // 同步持久化新密码,保证重连/重启后仍然生效
        configStore.setPassword(password);
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
        bootstrap.group(group).channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
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
                bootstrap.config().group().schedule(() -> connect(bootstrap, order), 5, TimeUnit.SECONDS);
            }
        });
    }

    public ClientConfigStore getConfigStore() {
        return configStore;
    }

    /**
     * 一次远程控制真正建立(控制端收到 ResCapture.START)后回调:
     * 将该设备 code 及其校验通过的密码记入本地连接历史,并在 UI 线程刷新下拉。
     */
    public void onRemoteConnected(String deviceCode, String password) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return;
        }
        configStore.addOrUpdateHistory(deviceCode, password);
        SwingUtilities.invokeLater(this::reloadRemoteDeviceHistory);
    }

    /**
     * 本机持久化的 deviceCode / password(客户端自持,重连与重启保持稳定)。
     */
    public String getDeviceCode() {
        return configStore.getDeviceCode();
    }

    public String getPassword() {
        return configStore.getPassword();
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
            controller.handleCmd(ctx.channel(), cmd);
            controlled.handleCmd(ctx.channel(), cmd);
        }
    }

    public void setChannel(Channel channel) {
        controller.setChannel(channel);
        controlled.setChannel(channel);
    }

    /**
     * Called by P2PChannelHandler when the P2P direct connection drops.
     * Clears the P2P channel so subsequent messages fall back to server relay.
     */
    public void onP2PDisconnected() {
        controller.setP2PChannel(null);
        controlled.setP2PChannel(null);
        Log.info("P2P channel cleared, falling back to server relay");
    }

    public void stopClient() {
        Log.info("Remote client disconnected from server...");
//        showMessageDialog("连接异常", JOptionPane.ERROR_MESSAGE);
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


    public void sendMouseControl(RobotMouseControl message) {
        try {
            robotsClient.send(message);
        } catch (Exception e) {
            Log.error("Failed to send mouse control message: " + e.getMessage());
        }
    }

    public void sendKeyControl(RobotKeyControl message) {
        try {
            robotsClient.send(message);
        } catch (Exception e) {
            Log.error("Failed to send key control message: " + e.getMessage());
        }
    }

    public CompletableFuture<RobotCaptureResponse> sendRobotCapture() {
        try {
            CompletableFuture<RobotCaptureResponse> future = new CompletableFuture<>();
            RobotCaputureReq req = new RobotCaputureReq();
            robotsClient.addCaptureFuture(req.getId(), future);
            robotsClient.send(req);
            return future;
        } catch (IOException e) {
            Log.error("Failed to send capture request: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    public static void main(String[] args) throws Exception {
        int robotPort = 55678;
        boolean robotEnabled = true;
        if (System.getProperty("robotPort") != null) {
            robotPort = Integer.parseInt(System.getProperty("robotPort"));
        }
        if (System.getProperty("robotEnabled") != null) {
            robotEnabled = Boolean.parseBoolean(System.getProperty("robotEnabled"));
        }
        String serverIp = "121.40.28.112";
        Integer serverPort = 54321;
        String clipboardServer = "http://121.40.28.112:12345/remote-desktop-control";
        String p2pServerIp = null;
        int p2pServerPort = 0;
        if (System.getProperty("configFile") != null) {
            Properties properties = new Properties();
            try (InputStream input = new FileInputStream(System.getProperty("configFile"))) {
                properties.load(input);
                if (properties.getProperty("serverIp") != null) {
                    serverIp = properties.getProperty("serverIp");
                }
                if (properties.getProperty("serverPort") != null) {
                    serverPort = Integer.parseInt(properties.getProperty("serverPort"));
                }
                if (properties.getProperty("clipboardServer") != null) {
                    clipboardServer = properties.getProperty("clipboardServer");
                }
                if (properties.getProperty("robotPort") != null) {
                    robotPort = Integer.parseInt(properties.getProperty("robotPort"));
                }
                if (properties.getProperty("robotEnabled") != null) {
                    robotEnabled = Boolean.parseBoolean(properties.getProperty("robotEnabled"));
                }
                if (properties.getProperty("p2pServerIp") != null) {
                    p2pServerIp = properties.getProperty("p2pServerIp");
                }
                if (properties.getProperty("p2pServerPort") != null) {
                    p2pServerPort = Integer.parseInt(properties.getProperty("p2pServerPort"));
                }
            } catch (Exception e) {
                Log.warn("Load config file error!", e);
            }
        }
        RemoteClient remoteClient = new RemoteClient(serverIp, serverPort, clipboardServer, robotPort, robotEnabled, p2pServerIp, p2pServerPort);
    }

}