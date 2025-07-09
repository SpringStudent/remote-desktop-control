package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.client.bean.Capture;
import io.github.springstudent.dekstop.client.compress.DeCompressorEngine;
import io.github.springstudent.dekstop.client.compress.DeCompressorEngineListener;
import io.github.springstudent.dekstop.client.monitor.*;
import io.github.springstudent.dekstop.client.utils.DialogFactory;
import io.github.springstudent.dekstop.common.bean.CompressionMethod;
import io.github.springstudent.dekstop.common.bean.Constants;
import io.github.springstudent.dekstop.common.bean.Gray8Bits;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.configuration.CaptureEngineConfiguration;
import io.github.springstudent.dekstop.common.configuration.CompressorEngineConfiguration;
import io.github.springstudent.dekstop.common.log.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static io.github.springstudent.dekstop.client.utils.ImageUtilities.getOrCreateIcon;
import static io.github.springstudent.dekstop.common.command.CmdKeyControl.KeyState.PRESSED;
import static io.github.springstudent.dekstop.common.command.CmdKeyControl.KeyState.RELEASED;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static javax.swing.SwingConstants.HORIZONTAL;

/**
 * 控制方
 *
 * @author ZhouNing
 * @date 2024/12/9 8:39
 **/
public class RemoteController extends RemoteControll implements DeCompressorEngineListener, RemoteScreenListener {

    private String deviceCode;

    private DeCompressorEngine deCompressorEngine;

    private CaptureEngineConfiguration captureEngineConfiguration;

    private CompressorEngineConfiguration compressorEngineConfiguration;

    private final Object prevBufferLOCK = new Object();

    private byte[] prevBuffer = null;

    private int prevWidth = -1;

    private int prevHeight = -1;

    private BitCounter receivedBitCounter;

    private TileCounter receivedTileCounter;

    private SkippedTileCounter skippedTileCounter;

    private MergedTileCounter mergedTileCounter;

    private CaptureCompressionCounter captureCompressionCounter;

    private CaptureRateCounter captureRateCounter;

    private ArrayList<Counter<?>> counters;

    public RemoteController() {
        captureEngineConfiguration = new CaptureEngineConfiguration();
        compressorEngineConfiguration = new CompressorEngineConfiguration();
        deCompressorEngine = new DeCompressorEngine(this);
        deCompressorEngine.start(8);
        receivedBitCounter = new BitCounter("receivedBits", "网络宽带使用量");
        receivedBitCounter.start(1000);
        receivedTileCounter = new TileCounter("receivedTiles", "收到的块数量(缓存命中率)");
        receivedTileCounter.start(1000);
        skippedTileCounter = new SkippedTileCounter("skippedTiles", "跳过块的数量");
        skippedTileCounter.start(1000);
        mergedTileCounter = new MergedTileCounter("mergedTiles", "合并的块数量");
        mergedTileCounter.start(1000);
        captureCompressionCounter = new CaptureCompressionCounter("captureCompression", "压缩比");
        captureCompressionCounter.start(1000);
        captureRateCounter = new CaptureRateCounter("captureRate", "每秒画面帧数");
        captureRateCounter.start(1000);
        counters = new ArrayList<>(Arrays.asList(captureRateCounter, receivedBitCounter, receivedTileCounter, skippedTileCounter, mergedTileCounter, captureCompressionCounter));
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void start() {
        super.start();
        synchronized (prevBufferLOCK) {
            prevBuffer = null;
            prevWidth = -1;
            prevHeight = -1;
        }
    }

    public void openSession(String deviceCode, String password) {
        this.deviceCode = deviceCode;
        fireCmd(new CmdReqCapture(deviceCode, CmdReqCapture.START_CAPTURE, password));
    }

    public void closeSession() {
        fireCmd(new CmdReqCapture(deviceCode, CmdReqCapture.STOP_CAPTURE));
    }

    public ArrayList<Counter<?>> getCounters() {
        return counters;
    }

    @Override
    public void handleCmd(Cmd cmd) {
        if (cmd.getType().equals(CmdType.ResOpen)) {
            CmdResOpen cmdResOpen = (CmdResOpen) cmd;
            if (cmdResOpen.getCode() == CmdResOpen.OFFLINE) {
                showMessageDialog("被控制端不在线", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResOpen.getCode() == CmdResOpen.CONTROL) {
                showMessageDialog("请先断开其他远程控制中的连接", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResOpen.getCode() == CmdResOpen.OK) {
                RemoteClient.getRemoteClient().openRemoteScreen();
            }
        } else if (cmd.getType().equals(CmdType.ResCapture)) {
            CmdResCapture cmdResCapture = (CmdResCapture) cmd;
            if (cmdResCapture.getCode() == CmdResCapture.START) {
                RemoteClient.getRemoteClient().getRemoteScreen().launch(cmdResCapture.getScreenNum());
                start();
            } else if (cmdResCapture.getCode() == CmdResCapture.STOP) {
                RemoteClient.getRemoteClient().getRemoteScreen().close();
                stop();
            } else if (cmdResCapture.getCode() == CmdResCapture.STOP_BYCONTROLLED) {
                RemoteClient.getRemoteClient().getRemoteScreen().close();
                stop();
                showMessageDialog("被控制端断开了连接", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResCapture.getCode() == CmdResCapture.STOP_CHANNELINACTIVE) {
                RemoteClient.getRemoteClient().getRemoteScreen().close();
                stop();
                showMessageDialog("被控制端不在线", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResCapture.getCode() == CmdResCapture.OFFLINE) {
                showMessageDialog("被控制端不在线", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResCapture.getCode() == CmdResCapture.CONTROL) {
                showMessageDialog("请先断开其他远程控制中的连接", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResCapture.getCode() == CmdResCapture.PWDERROR) {
                showMessageDialog("密码错误", JOptionPane.ERROR_MESSAGE);
            }
        } else if (cmd.getType().equals(CmdType.Capture)) {
            deCompressorEngine.handleCapture((CmdCapture) cmd);
            countReceivedBit(cmd);
        } else if (cmd.getType().equals(CmdType.ClipboardText) || cmd.getType().equals(CmdType.ClipboardTransfer)) {
            if (needSetClipboard(cmd)) {
                super.setClipboard(cmd).whenComplete((o, o2) -> RemoteClient.getRemoteClient().getRemoteScreen().transferClipboarButton(true));
            }
        } else if (cmd.getType().equals(CmdType.ResRemoteClipboard)) {
            RemoteClient.getRemoteClient().getRemoteScreen().transferClipboarButton(true);
        }
    }

    @Override
    public String getType() {
        return Constants.CONTROLLER;
    }

    private void countReceivedBit(Cmd cmd) {
        receivedBitCounter.add(8d * cmd.getWireSize());
    }

    @Override
    public void onDeCompressed(Capture capture, int cacheHits, double compressionRatio) {
        RemoteScreen remoteScreen = RemoteClient.getRemoteClient().getRemoteScreen();
        final AbstractMap.SimpleEntry<BufferedImage, byte[]> image;
        synchronized (prevBufferLOCK) {
            image = capture.createBufferedImage(prevBuffer, prevWidth, prevHeight);
            prevBuffer = image.getValue();
            prevWidth = image.getKey().getWidth();
            prevHeight = image.getKey().getHeight();
        }
        if (remoteScreen.getFitToScreenActivated()) {
            if (remoteScreen.getCanvas() == null) {
                Log.debug(format("ComputeScaleFactors for w: %s h: %s", prevWidth, prevHeight));
                remoteScreen.computeScaleFactors(prevWidth, prevHeight, remoteScreen.getKeepAspectRatioActivated());
            }
            // required as the canvas might have been reset if keepAspectRatio caused a resizing of the window
            final Dimension canvasDimension = remoteScreen.getCanvas();
            if (canvasDimension != null) {
                remoteScreen.getScreenPannel().onCaptureUpdated(scaleImage(image.getKey(), canvasDimension.width, canvasDimension.height));
            }
        } else {
            remoteScreen.getScreenPannel().onCaptureUpdated(image.getKey());
        }
        receivedTileCounter.add(capture.getDirtyTileCount(), cacheHits);
        skippedTileCounter.add(capture.getSkipped());
        mergedTileCounter.add(capture.getMerged());
        captureRateCounter.add(1);
        captureCompressionCounter.add(capture.getDirtyTileCount(), compressionRatio);
    }

    private BufferedImage scaleImage(BufferedImage image, int width, int height) {
        RemoteScreen remoteScreen = RemoteClient.getRemoteClient().getRemoteScreen();
        AffineTransform scaleTransform = AffineTransform.getScaleInstance(remoteScreen.getxFactor(), remoteScreen.getyFactor());
        try {
            AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);
            return bilinearScaleOp.filter(image, new BufferedImage(abs(width), abs(height), image.getType() == 0 ? TYPE_INT_ARGB_PRE : TYPE_BYTE_GRAY));
        } catch (ImagingOpException e) {
            Log.error(e.getMessage());
            return image;
        }
    }

    public Action createCaptureConfigurationAction() {
        final Action configure = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame captureFrame = (JFrame) SwingUtilities.getRoot(RemoteClient.getRemoteClient().getRemoteScreen());

                final JPanel pane = new JPanel();
                pane.setLayout(new GridLayout(3, 2, 10, 10));

                final JLabel tickLbl = new JLabel("屏幕捕获间隔");
                tickLbl.setToolTipText("屏幕捕获间隔");
                final JSlider tickMillisSlider = new JSlider(HORIZONTAL, 30, 1000, captureEngineConfiguration.getCaptureTick());
                final Properties tickLabelTable = new Properties();
                JLabel actualTick = new JLabel(format("  %dms  ", tickMillisSlider.getValue()));
                tickLabelTable.put(30, new JLabel("最小值"));
                tickLabelTable.put(550, actualTick);
                tickLabelTable.put(1000, new JLabel("最大值"));
                tickMillisSlider.setLabelTable(tickLabelTable);
                tickMillisSlider.setMajorTickSpacing(50);
                tickMillisSlider.setPaintTicks(true);
                tickMillisSlider.setPaintLabels(true);
                pane.add(tickLbl);
                pane.add(tickMillisSlider);

                final JLabel grayLevelsLbl = new JLabel("灰度值");
                final JSlider grayLevelsSlider = new JSlider(HORIZONTAL, 0, 6, 6 - captureEngineConfiguration.getCaptureQuantization().ordinal());
                final Properties grayLabelTable = new Properties();
                JLabel actualLevels = new JLabel(format("  %d  ", Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()).getLevels()));
                grayLabelTable.put(0, new JLabel("最小"));
                grayLabelTable.put(3, actualLevels);
                grayLabelTable.put(6, new JLabel("最大"));
                grayLevelsSlider.setLabelTable(grayLabelTable);
                grayLevelsSlider.setMajorTickSpacing(1);
                grayLevelsSlider.setPaintTicks(true);
                grayLevelsSlider.setPaintLabels(true);
                grayLevelsSlider.setSnapToTicks(true);
                pane.add(grayLevelsLbl).setEnabled(!captureEngineConfiguration.isCaptureColors());
                pane.add(grayLevelsSlider).setEnabled(!captureEngineConfiguration.isCaptureColors());

                final JLabel colorsLbl = new JLabel("彩色模式");
                final JCheckBox colorsCb = new JCheckBox();
                colorsCb.setSelected(captureEngineConfiguration.isCaptureColors());
                pane.add(colorsLbl).setEnabled(!false);
                pane.add(colorsCb).setEnabled(!false);
                tickMillisSlider.addChangeListener(e -> {
                    actualTick.setText(tickMillisSlider.getValue() < 1000 ? format("%dms", tickMillisSlider.getValue()) : "1s");
                    if (!tickMillisSlider.getValueIsAdjusting()) {
                        sendCaptureConfiguration(new CaptureEngineConfiguration(tickMillisSlider.getValue(), Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()), captureEngineConfiguration.isCaptureColors()));
                    }
                });
                grayLevelsSlider.addChangeListener(e -> {
                    actualLevels.setText(format("%d", Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()).getLevels()));
                    if (!grayLevelsSlider.getValueIsAdjusting() && !captureEngineConfiguration.isCaptureColors()) {
                        sendCaptureConfiguration(new CaptureEngineConfiguration(tickMillisSlider.getValue(), Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()), false));
                    }
                });
                colorsCb.addActionListener(e -> {
                    grayLevelsLbl.setEnabled(!colorsCb.isSelected());
                    grayLevelsSlider.setEnabled(!colorsCb.isSelected());
                });

                final boolean ok = DialogFactory.showOkCancel(captureFrame, "远程图像设置", pane, true, null);

                if (ok) {
                    final CaptureEngineConfiguration newCaptureEngineConfiguration = new CaptureEngineConfiguration(tickMillisSlider.getValue(), Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()), colorsCb.isSelected());
                    updateCaptureConfiguration(newCaptureEngineConfiguration);
                }
            }
        };
        configure.putValue(Action.NAME, "抓图设置");
        return configure;
    }

    private void updateCaptureConfiguration(CaptureEngineConfiguration newCaptureEngineConfiguration) {
        if (!newCaptureEngineConfiguration.equals(captureEngineConfiguration)) {
            captureEngineConfiguration = newCaptureEngineConfiguration;
            captureEngineConfiguration.persist();
            sendCaptureConfiguration(captureEngineConfiguration);
        }
    }

    private void sendCaptureConfiguration(final CaptureEngineConfiguration captureEngineConfiguration) {
        new Thread(() -> this.fireCmd(new CmdCaptureConf(captureEngineConfiguration))).start();
    }

    public Action createCompressionConfigurationAction() {
        final Action configure = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ev) {
                JFrame compressionFrame = (JFrame) SwingUtilities.getRoot(RemoteClient.getRemoteClient().getRemoteScreen());

                final JPanel pane = new JPanel();
                pane.setLayout(new GridLayout(4, 2, 10, 10));

                final JLabel methodLbl = new JLabel("压缩算法");
                final JComboBox<CompressionMethod> methodCb = new JComboBox<>(Stream.of(CompressionMethod.values()).filter(e -> !e.equals(CompressionMethod.NONE)).toArray(CompressionMethod[]::new));
                methodCb.setSelectedItem(compressorEngineConfiguration.getMethod());
                pane.add(methodLbl);
                pane.add(methodCb);

                final JLabel useCacheLbl = new JLabel("使用缓存");
                final JCheckBox useCacheCb = new JCheckBox();
                useCacheCb.setSelected(compressorEngineConfiguration.useCache());
                pane.add(useCacheLbl);
                pane.add(useCacheCb);

                final JLabel maxSizeLbl = new JLabel("最大缓存值");
                final JTextField maxSizeTf = new JTextField(valueOf(compressorEngineConfiguration.getCacheMaxSize()));
                pane.add(maxSizeLbl);
                pane.add(maxSizeTf);

                final JLabel purgeSizeLbl = new JLabel("缓存重置值");
                final JTextField purgeSizeTf = new JTextField(valueOf(compressorEngineConfiguration.getCachePurgeSize()));
                pane.add(purgeSizeLbl);
                pane.add(purgeSizeTf);

                useCacheCb.addActionListener(ev1 -> {
                    maxSizeLbl.setEnabled(useCacheCb.isSelected());
                    maxSizeTf.setEnabled(useCacheCb.isSelected());
                    purgeSizeLbl.setEnabled(useCacheCb.isSelected());
                    purgeSizeTf.setEnabled(useCacheCb.isSelected());
                });

                maxSizeLbl.setEnabled(useCacheCb.isSelected());
                maxSizeTf.setEnabled(useCacheCb.isSelected());
                purgeSizeLbl.setEnabled(useCacheCb.isSelected());
                purgeSizeTf.setEnabled(useCacheCb.isSelected());

                final boolean ok = DialogFactory.showOkCancel(compressionFrame, "压缩", pane, true, () -> {
                    final String max = maxSizeTf.getText();
                    if (max.isEmpty()) {
                        return "缓存最大值不能为空";
                    }
                    final int maxValue;
                    try {
                        maxValue = Integer.parseInt(max);
                    } catch (NumberFormatException ex) {
                        return "缓存最大值只能为数字";
                    }
                    if (maxValue <= 0) {
                        return "缓存最大值必须为正整数";
                    }
                    final String purge = purgeSizeTf.getText();
                    if (purge.isEmpty()) {
                        return "缓存重置值不能为空";
                    }
                    final int purgeValue;
                    try {
                        purgeValue = Integer.parseInt(purge);
                    } catch (NumberFormatException ex) {
                        return "缓存重置值必须为数字";
                    }
                    if (purgeValue <= 0) {
                        return "缓存重置值必须为正整数";
                    }
                    if (purgeValue >= maxValue) {
                        return "缓存重置值不能大于缓存最大值";
                    }
                    return null;
                });

                if (ok) {
                    final CompressorEngineConfiguration newCompressorEngineConfiguration = new CompressorEngineConfiguration((CompressionMethod) methodCb.getSelectedItem(), useCacheCb.isSelected(), Integer.parseInt(maxSizeTf.getText()), Integer.parseInt(purgeSizeTf.getText()));
                    if (!newCompressorEngineConfiguration.equals(compressorEngineConfiguration)) {
                        compressorEngineConfiguration = newCompressorEngineConfiguration;
                        compressorEngineConfiguration.persist();

                        sendCompressorConfiguration(compressorEngineConfiguration);
                    }
                }
            }
        };
        configure.putValue(Action.NAME, "压缩设置");
        return configure;
    }

    private void sendCompressorConfiguration(final CompressorEngineConfiguration compressorEngineConfiguration) {
        new Thread(() -> this.fireCmd(new CmdCompressorConf(compressorEngineConfiguration))).start();
    }

    public Action createResetAction() {
        final Action configure = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                sendCaptureConfiguration(captureEngineConfiguration);
            }
        };
        configure.putValue(Action.SHORT_DESCRIPTION, "重置屏幕捕获");
        configure.putValue(Action.SMALL_ICON, getOrCreateIcon("reset_capture.png"));
        return configure;
    }

    @Override
    public void onMouseMove(final int xs, final int ys) {
        fireCmd(new CmdMouseControl(xs, ys));
    }

    @Override
    public void onMousePressed(final int xs, final int ys, final int button) {
        int xbutton = getActingMouseButton(button);
        if (xbutton != CmdMouseControl.UNDEFINED) {
            fireCmd(new CmdMouseControl(xs, ys, CmdMouseControl.ButtonState.PRESSED, xbutton));
        }
    }

    @Override
    public void onMouseReleased(final int x, final int y, final int button) {
        int xbutton = getActingMouseButton(button);
        if (xbutton != CmdMouseControl.UNDEFINED) {
            fireCmd(new CmdMouseControl(x, y, CmdMouseControl.ButtonState.RELEASED, xbutton));
        }
    }

    private int getActingMouseButton(final int button) {
        if (MouseEvent.BUTTON1 == button) {
            return CmdMouseControl.BUTTON1;
        }
        if (MouseEvent.BUTTON2 == button) {
            return CmdMouseControl.BUTTON2;
        }
        if (MouseEvent.BUTTON3 == button) {
            return CmdMouseControl.BUTTON3;
        }
        return CmdMouseControl.UNDEFINED;
    }

    @Override
    public void onMouseWheeled(final int x, final int y, final int rotations) {
        fireCmd(new CmdMouseControl(x, y, rotations));
    }


    private final Map<Integer, Character> pressedKeys = new ConcurrentHashMap<>();

    @Override
    public void onKeyPressed(final int keyCode, final char keyChar) {
        pressedKeys.put(keyCode, keyChar);
        fireCmd(new CmdKeyControl(PRESSED, keyCode, keyChar));
    }

    /**
     * From AWT thread (!)
     */
    @Override
    public void onKeyReleased(final int keyCode, final char keyChar) {
        // -------------------------------------------------------------------------------------------------------------
        // E.g., Windows + R : [Windows.PRESSED] and then the focus is LOST =>
        // missing RELEASED events
        //
        // Currently trying to lease the 'assisted' in a consistent state - not
        // sure I should send the
        // [Windows] key and the like (e.g.,CTRL-ALT-DEL, etc...) at all ...
        // -------------------------------------------------------------------------------------------------------------
        if (keyCode == -1) {
            Log.warn(format("Got keyCode %s keyChar '%s' - releasing all keys", keyCode, keyChar));
            pressedKeys.forEach(this::onKeyReleased);
            return;
        }
        if (!pressedKeys.containsKey(keyCode)) {
            Log.warn(format("Not releasing unpressed keyCode %s keyChar '%s'", keyCode, keyChar));
            return;
        }
        pressedKeys.remove(keyCode);
        fireCmd(new CmdKeyControl(RELEASED, keyCode, keyChar));
    }

    public Action createRequireRemoteClipboardAction() {
        final Action getRemoteClipboard = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                requireRemoteClipboard();
            }
        };
        getRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, "获取远程粘贴板");
        getRemoteClipboard.putValue(Action.SMALL_ICON, getOrCreateIcon("down.png"));
        return getRemoteClipboard;
    }

    private void requireRemoteClipboard() {
        RemoteClient.getRemoteClient().getRemoteScreen().transferClipboarButton(false);
        fireCmd(new CmdReqRemoteClipboard());
    }

    public Action createSendLoacalClibboardAction() {
        final Action setRemoteClipboard = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                RemoteClient.getRemoteClient().getRemoteScreen().transferClipboarButton(false);
                RemoteController.this.sendClipboard().whenComplete((aByte, throwable) -> {
                    if (throwable != null || aByte != CmdResRemoteClipboard.OK) {
                        RemoteClient.getRemoteClient().getRemoteScreen().transferClipboarButton(true);
                    }
                });
            }
        };
        setRemoteClipboard.putValue(Action.SHORT_DESCRIPTION, "发送本机粘贴板");
        setRemoteClipboard.putValue(Action.SMALL_ICON, getOrCreateIcon("up.png"));
        return setRemoteClipboard;
    }

    public void sendScreenSelect(int screenIndex) {
        fireCmd(new CmdSelectScreen(screenIndex));
    }
}
