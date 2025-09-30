package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.common.command.Cmd;
import io.github.springstudent.dekstop.common.command.CmdClipboardText;
import io.github.springstudent.dekstop.common.command.CmdClipboardTransfer;
import io.github.springstudent.dekstop.common.command.CmdType;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.RemoteClpboardListener;
import io.github.springstudent.dekstop.common.remote.RemoteScreenRobot;
import io.github.springstudent.dekstop.common.remote.bean.*;
import io.github.springstudent.dekstop.common.utils.EmptyUtils;
import io.github.springstudent.dekstop.common.utils.NettyUtils;
import io.netty.channel.Channel;

import javax.swing.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * @author ZhouNing
 * @date 2024/12/10 14:20
 **/
public abstract class RemoteControll implements RemoteScreenRobot, RemoteClpboardListener {
    private Channel channel;

    private RemoteRobotsClient robotsClient;

    public RemoteControll(RemoteRobotsClient robotsClient) {
        this.robotsClient = robotsClient;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }


    public void fireCmd(Cmd cmd) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(cmd);
        } else {
            Log.error("client fireCmd error,please check network connect");
        }
    }

    @Override
    public CompletableFuture<SendClipboardResponse> sendClipboard(SendClipboardRequest request) {
        CompletableFuture<SendClipboardResponse> sendClipboardFuture = new CompletableFuture<>();
        try {
            robotsClient.addSendClipboardFuture(request.getId(), sendClipboardFuture);
            robotsClient.send(request);
        } catch (IOException e) {
            Log.error("Failed to send clipboard data: " + e.getMessage());
            robotsClient.getSendClipboardFuture(request.getId()).completeExceptionally(e);
        }
        return sendClipboardFuture;
    }

    @Override
    public CompletableFuture<SetClipboardResponse> setClipboard(SetClipboardRequest request) {
        CompletableFuture<SetClipboardResponse> setClipboardFuture = new CompletableFuture<>();
        try {
            robotsClient.addSetClipboardFuture(request.getId(), setClipboardFuture);
            robotsClient.send(request);
        } catch (IOException e) {
            Log.error("Failed to set clipboard data: " + e.getMessage());
            robotsClient.getSetClipboardFuture(request.getId()).completeExceptionally(e);
        }
        return setClipboardFuture;
    }

    @Override
    public void handleMessage(RobotMouseControl message) {
        try {
            robotsClient.send(message);
        } catch (IOException e) {
            Log.error("Failed to send mouse control message: " + e.getMessage());
        }
    }

    @Override
    public void handleMessage(RobotKeyControl message) {
        try {
            robotsClient.send(message);
        } catch (IOException e) {
            Log.error("Failed to send key control message: " + e.getMessage());
        }
    }

    protected void showMessageDialog(Object msg, int messageType) {
        SwingUtilities.invokeLater(() -> RemoteClient.getRemoteClient().showMessageDialog(msg, messageType));
    }

    protected String getDeviceCode() {
        if (channel != null) {
            String deviceCode = NettyUtils.getDeviceCode(this.channel);
            if (EmptyUtils.isEmpty(deviceCode)) {
                throw new IllegalStateException("cannot get device code,please check client connect status");
            } else {
                return deviceCode;
            }
        } else {
            throw new IllegalStateException("cannot get device code,please check client connect status");
        }
    }

    protected SetClipboardRequest beforeSetClipboard(Cmd cmd) {
        if (cmd.getType().equals(CmdType.ClipboardText) && !((CmdClipboardText) cmd).getControlType().equals(getType())) {
            CmdClipboardText cmdClipboardText = (CmdClipboardText) cmd;
            return new SetClipboardRequest("text", cmdClipboardText.getPayload(), RemoteClient.getRemoteClient().getClipboardServer());
        } else if (cmd.getType().equals(CmdType.ClipboardTransfer) && !((CmdClipboardTransfer) cmd).getControlType().equals(getType())) {
            return new SetClipboardRequest("file", ((CmdClipboardTransfer) cmd).getDeviceCode(), RemoteClient.getRemoteClient().getClipboardServer());
        }
        return null;
    }

    protected void afterSendClipboard(SendClipboardResponse response) {
        if (response.getClipboarType().equals("text")) {
            fireCmd(new CmdClipboardText(response.getContent(), getType()));
        } else {
            fireCmd(new CmdClipboardTransfer(response.getContent(), getType()));
        }
    }

    public abstract void handleCmd(Cmd cmd);

    public abstract String getType();

    public void start() {
    }

    public void stop() {
    }

}
