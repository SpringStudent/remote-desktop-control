package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.client.capture.CaptureEngine;
import io.github.springstudent.dekstop.client.capture.RobotCaptureFactory;
import io.github.springstudent.dekstop.client.compress.CompressorEngine;
import io.github.springstudent.dekstop.client.compress.CompressorEngineListener;
import io.github.springstudent.dekstop.client.jni.WinDesktop;
import io.github.springstudent.dekstop.client.utils.ScreenUtilities;
import io.github.springstudent.dekstop.common.bean.CompressionMethod;
import io.github.springstudent.dekstop.common.bean.Constants;
import io.github.springstudent.dekstop.common.bean.MemByteBuffer;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.configuration.CaptureEngineConfiguration;
import io.github.springstudent.dekstop.common.configuration.CompressorEngineConfiguration;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.bean.RobotKeyControl;
import io.github.springstudent.dekstop.common.remote.bean.RobotMouseControl;
import io.github.springstudent.dekstop.common.remote.bean.SendClipboardRequest;
import io.github.springstudent.dekstop.common.remote.bean.SetClipboardRequest;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.awt.event.KeyEvent.*;

/**
 * 被控制方
 *
 * @author ZhouNing
 * @date 2024/12/9 8:40
 **/
public class RemoteControlled extends RemoteControll implements CompressorEngineListener {

    private CaptureEngine captureEngine;

    private CompressorEngine compressorEngine;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private CompressorEngineConfiguration compressorEngineConfiguration;

    private static final char UNIX_SEPARATOR_CHAR = '/';

    private final Set<Integer> pressedKeys = new HashSet<>();

    public RemoteControlled(RobotsClient robotsClient) {
        super(robotsClient);
        captureEngineConfiguration = new CaptureEngineConfiguration();
        compressorEngineConfiguration = new CompressorEngineConfiguration();
        captureEngine = new CaptureEngine(new RobotCaptureFactory(-1));
        captureEngine.configure(captureEngineConfiguration);
        compressorEngine = new CompressorEngine();
        compressorEngine.configure(compressorEngineConfiguration);
        captureEngine.addListener(compressorEngine);
        compressorEngine.addListener(this);

    }

    @Override
    public void stop() {
        captureEngine.stop();
        compressorEngine.stop();
        super.stop();
    }

    @Override
    public void start() {
        captureEngine.configure(new CaptureEngineConfiguration());
        compressorEngine.configure(new CompressorEngineConfiguration());
        captureEngine.start();
        compressorEngine.start(1);
        super.start();
    }

    public void closeSession(String deviceCode) {
        fireCmd(new CmdReqCapture(deviceCode, CmdReqCapture.STOP_CAPTURE_BY_CONTROLLED));
    }

    @Override
    public void handleCmd(Cmd cmd) {
        if (cmd.getType().equals(CmdType.ResCapture)) {
            CmdResCapture cmdResCapture = (CmdResCapture) cmd;
            if (cmdResCapture.getCode() == CmdResCapture.START_) {
                RemoteClient.getRemoteClient().setControlledAndCloseSessionLabelVisible(true);
                start();
            } else if (cmdResCapture.getCode() == CmdResCapture.STOP_) {
                RemoteClient.getRemoteClient().setControlledAndCloseSessionLabelVisible(false);
                stop();
            }
        } else if (cmd.getType().equals(CmdType.CaptureConfig)) {
            this.captureEngineConfiguration = ((CmdCaptureConf) cmd).getConfiguration();
            captureEngine.reconfigure(this.captureEngineConfiguration);
        } else if (cmd.getType().equals(CmdType.CompressorConfig)) {
            this.compressorEngineConfiguration = ((CmdCompressorConf) cmd).getConfiguration();
            compressorEngine.reconfigure(compressorEngineConfiguration);
        } else if (cmd.getType().equals(CmdType.KeyControl)) {
            this.keyControl((CmdKeyControl) cmd);
        } else if (cmd.getType().equals(CmdType.MouseControl)) {
            this.mouseControl((CmdMouseControl) cmd);
        } else if (cmd.getType().equals(CmdType.ReqRemoteClipboard)) {
            super.sendClipboard(new SendClipboardRequest(getDeviceCode(), RemoteClient.getRemoteClient().getClipboardServer())).whenComplete((response, throwable) -> {
                if (throwable != null || response.getCode() != CmdResRemoteClipboard.OK) {
                    fireCmd(new CmdResRemoteClipboard());
                } else {
                    afterSendClipboard(response);
                }
            });
        } else if ((cmd.getType().equals(CmdType.ClipboardText) || cmd.getType().equals(CmdType.ClipboardTransfer))) {
            SetClipboardRequest setClipboardRequest = null;
            if ((setClipboardRequest = beforeSetClipboard(cmd)) != null) {
                super.setClipboard(setClipboardRequest).whenComplete((o, o2) -> {
                    fireCmd(new CmdResRemoteClipboard());
                });
            }
        } else if (cmd.getType().equals(CmdType.SelectScreen)) {
            int screenIndex = ((CmdSelectScreen) cmd).getScreenIndex();
            if (captureEngineConfiguration == null) {
                Log.error("CaptureEngineConfiguration is null");
                return;
            }
            if (captureEngine != null) {
                captureEngine.stop();
            }
            captureEngine = new CaptureEngine(new RobotCaptureFactory(screenIndex));
            captureEngine.configure(captureEngineConfiguration);
            if (compressorEngine != null) {
                captureEngine.addListener(compressorEngine);
            }
            captureEngine.start();
        }
    }

    @Override
    public String getType() {
        return Constants.CONTROLLED;
    }

    @Override
    public void onCompressed(int captureId, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration, MemByteBuffer compressed) {
        fireCmd(new CmdCapture(captureId, compressionMethod, compressionConfiguration, compressed));
    }

    public void mouseControl(CmdMouseControl message) {
        if (WinDesktop.isWindowsAndLockScreen()) {
            int info = 0;
            if (message.isPressed()) {
                if (message.isButton1()) info |= 0x1;
                if (message.isButton2()) info |= 0x4;
                if (message.isButton3()) info |= 0x10;
            } else if (message.isReleased()) {
                if (message.isButton1()) info |= 0x2;
                if (message.isButton2()) info |= 0x8;
                if (message.isButton3()) info |= 0x20;
            }
            final int fInfo = info;
            CompletableFuture.runAsync(() -> WinDesktop.INSTANCE.SimulateMouseEventJNA(message.getX(), message.getY(), fInfo, message.getRotations()));
        } else {
            int x = message.getX();
            int y = message.getY();
            if (!ScreenUtilities.inScreenBounds(x, y)) {
                x = x + ScreenUtilities.getSharedScreenSize().x;
                y = y + ScreenUtilities.getSharedScreenSize().y;
            }
            super.handleMessage(new RobotMouseControl(x, y, message.getInfo(), message.getRotations()));
        }
    }

    public void keyControl(CmdKeyControl message) {
        if (message.isPressed()) {
            try {
                pressKey(message);
            } catch (IllegalArgumentException ex) {
                Log.error("Error while handling " + message);
            }
        } else if (message.isReleased()) {
            try {
                releaseKey(message);
            } catch (IllegalArgumentException ex) {
                Log.error("Error while handling " + message);
            }
        }
    }


    private void pressKey(CmdKeyControl message) {
        int keyCode = escapeByOsId(message.getKeyCode());
        if (keyCode != VK_UNDEFINED) {
            if (keyCode == VK_ALT_GRAPH && File.separatorChar != UNIX_SEPARATOR_CHAR) {
                nativePressKey(VK_CONTROL);
                pressedKeys.add(VK_CONTROL);
                nativePressKey(VK_ALT);
                pressedKeys.add(VK_ALT);
                Log.debug("KeyCode ALT_GRAPH %s", () -> String.valueOf(message));
                return;
            }
            Log.debug("KeyCode %s", () -> String.valueOf(message));
            try {
                nativePressKey(keyCode);
                pressedKeys.add(keyCode);
                return;
            } catch (IllegalArgumentException ie) {
                Log.debug("Proceeding with plan B");
            }
        }
        Log.debug("Undefined KeyCode %s", () -> String.valueOf(message));
        if (message.getKeyChar() != CHAR_UNDEFINED) {
            int dec = message.getKeyChar();
            Log.debug("KeyChar as unicode " + dec + " %s", () -> String.valueOf(message));
            pressedKeys.forEach(kc -> nativeReleaseKey(kc));
            typeUnicode(dec);
            pressedKeys.forEach(kc -> nativeReleaseKey(kc));
            return;
        }
        Log.warn("Undefined KeyChar " + message);
    }

    private void nativePressKey(int keyCode) {
        if (WinDesktop.isWindowsAndLockScreen()) {
            CompletableFuture.runAsync(() -> WinDesktop.INSTANCE.SimulateKeyEventJNA(keyCode, 1));
        } else {
            super.handleMessage(new RobotKeyControl(keyCode, true));
        }
    }

    private void nativeReleaseKey(int keyCode) {
        if (WinDesktop.isWindowsAndLockScreen()) {
            CompletableFuture.runAsync(() -> WinDesktop.INSTANCE.SimulateKeyEventJNA(keyCode, 0));
        } else {
            super.handleMessage(new RobotKeyControl(keyCode, false));
        }
    }

    private void typeUnicode(int keyCode) {
        if (File.separatorChar == UNIX_SEPARATOR_CHAR) {
            typeLinuxUnicode(keyCode);
            return;
        }
        typeWindowsUnicode(keyCode);
    }

    private void releaseKey(CmdKeyControl message) {
        int keyCode = escapeByOsId(message.getKeyCode());
        if (keyCode != VK_UNDEFINED) {
            if (keyCode == VK_ALT_GRAPH && File.separatorChar != UNIX_SEPARATOR_CHAR) {
                nativeReleaseKey(VK_ALT);
                pressedKeys.remove(VK_ALT);
                nativeReleaseKey(VK_CONTROL);
                pressedKeys.remove(VK_CONTROL);
                Log.debug("KeyCode ALT_GRAPH %s", () -> String.valueOf(message));
                return;
            }
            Log.debug("KeyCode %s", () -> String.valueOf(message));
            try {
                nativeReleaseKey(keyCode);
                pressedKeys.remove(keyCode);
            } catch (IllegalArgumentException ie) {
                Log.warn("Error releasing KeyCode " + message);
            }
        }
    }

    private int escapeByOsId(int keyCode) {
        if (RemoteClient.getRemoteClient().getOsId() == 'm' && keyCode == VK_WINDOWS) {
            return VK_META;
        } else {
            return keyCode;
        }
    }

    /**
     * Unicode characters are typed in decimal on Windows ä => 228
     */
    private void typeWindowsUnicode(int keyCode) {
        nativePressKey(VK_ALT);
        // simulate a numpad key press for each digit
        for (int i = 3; i >= 0; --i) {
            int code = keyCode / (int) (Math.pow(10, i)) % 10 + VK_NUMPAD0;
            nativePressKey(code);
            nativeReleaseKey(code);
        }
//        robot.keyRelease(VK_ALT);
        nativeReleaseKey(VK_ALT);
    }

    /**
     * Unicode characters are typed in hex on Linux ä => e4
     */
    private void typeLinuxUnicode(int keyCode) {
        nativePressKey(VK_CONTROL);
        nativePressKey(VK_SHIFT);
        nativePressKey(VK_U);
        nativeReleaseKey(VK_U);
        char[] charArray = Integer.toHexString(keyCode).toCharArray();
        // simulate a key press/release for each char
        // char[] { 'e', '4' }  => keyPress(69), keyRelease(69), keyPress(52), keyRelease(52)
        for (char c : charArray) {
            int code = Character.toUpperCase(c);
            nativePressKey(code);
            nativeReleaseKey(code);
        }
        nativeReleaseKey(VK_SHIFT);
        nativeReleaseKey(VK_CONTROL);
    }
}
