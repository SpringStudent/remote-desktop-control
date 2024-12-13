package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.client.bean.Capture;
import io.github.springstudent.dekstop.client.compress.DeCompressorEngine;
import io.github.springstudent.dekstop.client.compress.DeCompressorEngineListener;
import io.github.springstudent.dekstop.client.concurrent.DefaultThreadFactoryEx;
import io.github.springstudent.dekstop.client.concurrent.Executable;
import io.github.springstudent.dekstop.client.utils.DialogFactory;
import io.github.springstudent.dekstop.common.bean.CompressionMethod;
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
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

import static io.github.springstudent.dekstop.common.command.CmdKeyControl.KeyState.PRESSED;
import static io.github.springstudent.dekstop.common.command.CmdKeyControl.KeyState.RELEASED;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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

    private final ThreadPoolExecutor executor;

    public RemoteController() {
        executor = new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, new LinkedBlockingQueue<>());
        executor.setThreadFactory(new DefaultThreadFactoryEx("controller-executor"));
        captureEngineConfiguration = new CaptureEngineConfiguration();
        compressorEngineConfiguration = new CompressorEngineConfiguration();
        deCompressorEngine = new DeCompressorEngine(this);
        deCompressorEngine.start(8);
    }

    @Override
    public void stop() {

    }

    @Override
    public void start() {

    }

    public void openSession(String deviceCode) {
        this.deviceCode = deviceCode;
        fireCmd(new CmdReqCapture(deviceCode, CmdReqCapture.START_CAPTURE));
    }

    public void closeSession() {
        fireCmd(new CmdReqCapture(deviceCode, CmdReqCapture.STOP_CAPTURE));
    }


    @Override
    public void handleCmd(Cmd cmd) {
        if (cmd.getType().equals(CmdType.ResCapture)) {
            CmdResCapture cmdResCapture = (CmdResCapture) cmd;
            if (cmdResCapture.getCode() == CmdResCapture.START) {
                RemoteClient.getRemoteClient().getRemoteScreen().launch();
            } else if (cmdResCapture.getCode() == CmdResCapture.STOP) {
                RemoteClient.getRemoteClient().getRemoteScreen().close();
            } else if (cmdResCapture.getCode() == CmdResCapture.OFFLINE) {
                RemoteClient.getRemoteClient().showMessageDialog("被控制端不在线", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResCapture.getCode() == CmdResCapture.CONTROL) {
                RemoteClient.getRemoteClient().showMessageDialog("请先断开其他远程控制中的连接", JOptionPane.ERROR_MESSAGE);
            } else if (cmdResCapture.getCode() == CmdResCapture.FAIL) {
                RemoteClient.getRemoteClient().showMessageDialog("远程控制失败", JOptionPane.ERROR_MESSAGE);
            }
        } else if (cmd.getType().equals(CmdType.Capture)) {
            deCompressorEngine.handleCapture((CmdCapture) cmd);
        }
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
                        sendCaptureConfiguration(new CaptureEngineConfiguration(tickMillisSlider.getValue(),
                                Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()), captureEngineConfiguration.isCaptureColors()));
                    }
                });
                grayLevelsSlider.addChangeListener(e -> {
                    actualLevels.setText(format("%d", Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()).getLevels()));
                    if (!grayLevelsSlider.getValueIsAdjusting() && !captureEngineConfiguration.isCaptureColors()) {
                        sendCaptureConfiguration(new CaptureEngineConfiguration(tickMillisSlider.getValue(),
                                Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()), false));
                    }
                });
                colorsCb.addActionListener(e -> {
                    grayLevelsLbl.setEnabled(!colorsCb.isSelected());
                    grayLevelsSlider.setEnabled(!colorsCb.isSelected());
                });

                final boolean ok = DialogFactory.showOkCancel(captureFrame, "远程图像设置", pane, true, null);

                if (ok) {
                    final CaptureEngineConfiguration newCaptureEngineConfiguration = new CaptureEngineConfiguration(tickMillisSlider.getValue(),
                            Gray8Bits.toGrayLevel(grayLevelsSlider.getValue()), colorsCb.isSelected());
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

                final JLabel purgeSizeLbl = new JLabel("缓存充值值");
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
                    final CompressorEngineConfiguration newCompressorEngineConfiguration = new CompressorEngineConfiguration((CompressionMethod) methodCb.getSelectedItem(),
                            useCacheCb.isSelected(), Integer.parseInt(maxSizeTf.getText()), Integer.parseInt(purgeSizeTf.getText()));
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

    @Override
    public void onMouseMove(final int xs, final int ys) {
        executor.execute(new Executable(executor) {
            @Override
            protected void execute() {
                RemoteController.this.fireCmd(new CmdMouseControl(xs, ys));
            }
        });
    }

    @Override
    public void onMousePressed(final int xs, final int ys, final int button) {
        executor.execute(new Executable(executor) {
            @Override
            protected void execute() {
                int xbutton = getActingMouseButton(button);
                if (xbutton != CmdMouseControl.UNDEFINED) {
                    RemoteController.this.fireCmd(new CmdMouseControl(xs, ys, CmdMouseControl.ButtonState.PRESSED, xbutton));
                }
            }
        });
    }

    @Override
    public void onMouseReleased(final int x, final int y, final int button) {
        executor.execute(new Executable(executor) {
            @Override
            protected void execute() {
                int xbutton = getActingMouseButton(button);
                if (xbutton != CmdMouseControl.UNDEFINED) {
                    RemoteController.this.fireCmd(new CmdMouseControl(x, y, CmdMouseControl.ButtonState.RELEASED, xbutton));
                }
            }
        });
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
        executor.execute(new Executable(executor) {
            @Override
            protected void execute() {
                RemoteController.this.fireCmd(new CmdMouseControl(x, y, rotations));
            }
        });
    }


    private final HashMap<Integer, Character> pressedKeys = new HashMap<>();

    @Override
    public void onKeyPressed(final int keyCode, final char keyChar) {
        executor.execute(new Executable(executor) {
            @Override
            protected void execute() {
                pressedKeys.put(keyCode, keyChar);
                RemoteController.this.fireCmd(new CmdKeyControl(PRESSED, keyCode, keyChar));
            }
        });
    }

    /**
     * From AWT thread (!)
     */
    @Override
    public void onKeyReleased(final int keyCode, final char keyChar) {
        if (keyCode == -1) {
            Log.warn(format("Got keyCode %s keyChar '%s' - releasing all keys", keyCode, keyChar));
            pressedKeys.forEach(this::onKeyReleased);
            return;
        }
        if (!pressedKeys.containsKey(keyCode)) {
            Log.warn(format("Not releasing unpressed keyCode %s keyChar '%s'", keyCode, keyChar));
            return;
        }
        executor.execute(new Executable(executor) {
            @Override
            protected void execute() {
                pressedKeys.remove(keyCode);
                fireCmd(new CmdKeyControl(RELEASED, keyCode, keyChar));
            }
        });
    }
}
