package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.client.bean.Listeners;
import io.github.springstudent.dekstop.client.bean.RepeatingReleasedEventsFixer;
import io.github.springstudent.dekstop.client.bean.StatusBar;
import io.github.springstudent.dekstop.client.monitor.Counter;
import io.github.springstudent.dekstop.common.remote.RemoteScreenListener;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.utils.EmptyUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputContext;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.springstudent.dekstop.client.utils.ImageUtilities.getOrCreateIcon;
import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_WINDOWS;
import static java.lang.Math.abs;
import static java.lang.String.format;

/**
 * @author ZhouNing
 * @date 2024/12/9 8:42
 */
public class RemoteScreen extends JFrame {

    private static final int OFFSET = 6;

    private transient Listeners<RemoteScreenListener> listeners = new Listeners();

    private ArrayList<Counter<?>> counters = new ArrayList<>();

    private static final int DEFAULT_FACTOR = 1;
    private double xFactor = DEFAULT_FACTOR;
    private double yFactor = DEFAULT_FACTOR;

    private StatusBar statusBar;
    private Dimension canvas;

    private CanvasPannel screenPannel;

    private JScrollPane screenPanelWrapper;

    private Timer sessionTimer;

    private JButton reqClipboardButton;

    private JButton sendClipboardButton;

    private JToggleButton windowsKeyToggleButton;

    private JToggleButton ctrlKeyToggleButton;

    private JMenu optionsMenu;

    private JMenu selectScreenMenu;

    private JCheckBoxMenuItem fitToScreenItem;

    private JCheckBoxMenuItem keepAspectRatioItem;
    private final AtomicBoolean fitToScreenActivated = new AtomicBoolean(false);

    private final AtomicBoolean keepAspectRatioActivated = new AtomicBoolean(false);

    private final AtomicBoolean isImmutableWindowsSize = new AtomicBoolean(false);

    private final AtomicBoolean windowsKeyActivated = new AtomicBoolean(false);

    private final AtomicBoolean ctrlKeyActivated = new AtomicBoolean(false);

    public RemoteScreen() {
        super("远程桌面");
        RepeatingReleasedEventsFixer.install();
        listeners.add(RemoteClient.getRemoteClient().getController());
        counters.addAll(RemoteClient.getRemoteClient().getController().getCounters());
        initFrame();
        initCanvasPanel();
        //allows for seeing the TAB with a regular KEY listener ...
        setFocusTraversalKeysEnabled(false);
        initMenuBar();
        initListeners();
        initStatusBar();
    }


    private void initFrame() {
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setSize(800, 600);
        this.setLayout(new BorderLayout());
        this.setLocationRelativeTo(null);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                RemoteClient.getRemoteClient().closeRemoteScreen();
            }
        });
    }

    private void initCanvasPanel() {
        this.screenPannel = new CanvasPannel();
        screenPannel.setBackground(Color.WHITE);
        screenPannel.setFocusable(false);
        this.screenPanelWrapper = new JScrollPane(screenPannel);
        this.add(screenPanelWrapper, BorderLayout.CENTER);
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        // 适配屏幕菜单项
        fitToScreenItem = new JCheckBoxMenuItem(new AbstractAction("适配屏幕") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                fitToScreenActivated.set(!fitToScreenActivated.get());
                fitToScreenItem.setSelected(fitToScreenActivated.get());
                if (fitToScreenActivated.get()) {
                    resetCanvas();
                } else {
                    resetFactors();
                }
                repaint();
            }
        });
        // 保持宽高比菜单项
        keepAspectRatioItem = new JCheckBoxMenuItem(new AbstractAction("保持宽高比") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                keepAspectRatioActivated.set(!keepAspectRatioActivated.get());
                keepAspectRatioItem.setSelected(keepAspectRatioActivated.get());
                resetCanvas();
                repaint();
            }
        });
        keepAspectRatioItem.setEnabled(false);
        // 根据适配屏幕的状态动态控制保持宽高比的可见性
        fitToScreenItem.addActionListener(e -> keepAspectRatioItem.setEnabled(fitToScreenActivated.get()));
        // 会话配置
        JMenuItem sessionConfigItem = new JMenuItem(RemoteClient.getRemoteClient().getController().createCaptureConfigurationAction());
        // 压缩设置
        JMenuItem compressionConfigItem = new JMenuItem(RemoteClient.getRemoteClient().getController().createCompressionConfigurationAction());

        // 菜单分组
        this.optionsMenu = new JMenu("选项");
        optionsMenu.add(fitToScreenItem);
        optionsMenu.add(keepAspectRatioItem);
        optionsMenu.addSeparator();
        optionsMenu.add(sessionConfigItem);
        optionsMenu.add(compressionConfigItem);
        menuBar.add(optionsMenu);
        //发送win键
        this.windowsKeyToggleButton = createToggleButton(createSendWindowsKeyAction());
        menuBar.add(windowsKeyToggleButton);
        menuBar.add(Box.createHorizontalStrut(5));
        this.ctrlKeyToggleButton = createToggleButton(createSendCtrlKeyAction());
        menuBar.add(ctrlKeyToggleButton);
        menuBar.add(Box.createHorizontalStrut(5));
        //粘贴板按钮
        if (EmptyUtils.isNotEmpty(RemoteClient.getRemoteClient().getClipboardServer())) {
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
            this.reqClipboardButton = createButton(RemoteClient.getRemoteClient().getController().createRequireRemoteClipboardAction());
            this.sendClipboardButton = createButton(RemoteClient.getRemoteClient().getController().createSendLoacalClibboardAction());
            buttonPanel.add(reqClipboardButton);
            buttonPanel.add(Box.createHorizontalStrut(5));
            buttonPanel.add(sendClipboardButton);
            menuBar.add(buttonPanel);
        }
        //重置捕获按钮
        menuBar.add(Box.createHorizontalStrut(5));
        JButton resetCaptureButton = createButton(RemoteClient.getRemoteClient().getController().createResetAction());
        menuBar.add(resetCaptureButton);
        this.setJMenuBar(menuBar);
    }

    private JMenu createScreenSelectionMenu(int screenNum) {
        this.selectScreenMenu = new JMenu("屏幕切换");
        ButtonGroup screenGroup = new ButtonGroup();
        for (int i = 0; i < screenNum; i++) {
            JRadioButtonMenuItem screenItem = new JRadioButtonMenuItem("屏幕 " + (i + 1));
            final int screenIndex = i;
            screenItem.addActionListener(e -> RemoteClient.getRemoteClient().getController().sendScreenSelect(screenIndex));
            screenGroup.add(screenItem);
            selectScreenMenu.add(screenItem);
        }
        return selectScreenMenu;
    }

    private Action createSendWindowsKeyAction() {
        final Action sendWindowsKey = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                if (windowsKeyActivated.get()) {
                    fireOnKeyReleased(VK_WINDOWS, ' ');
                } else {
                    fireOnKeyPressed(VK_WINDOWS, ' ');
                }
                windowsKeyActivated.set(!windowsKeyActivated.get());
            }
        };
        sendWindowsKey.putValue(Action.SHORT_DESCRIPTION, "发送win键(mac的command)");
        sendWindowsKey.putValue(Action.SMALL_ICON, getOrCreateIcon("win.png"));
        return sendWindowsKey;
    }

    private Action createSendCtrlKeyAction() {
        final Action sendCtrlKey = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                if (ctrlKeyActivated.get()) {
                    fireOnKeyReleased(VK_CONTROL, ' ');
                } else {
                    fireOnKeyPressed(VK_CONTROL, ' ');
                }
                ctrlKeyActivated.set(!ctrlKeyActivated.get());
            }
        };
        sendCtrlKey.putValue(Action.SHORT_DESCRIPTION, "发送ctrl键");
        sendCtrlKey.putValue(Action.SMALL_ICON, getOrCreateIcon("ctrl.png"));
        return sendCtrlKey;
    }


    public final void transferClipboarButton(boolean enabled) {
        this.sendClipboardButton.setEnabled(enabled);
        this.reqClipboardButton.setEnabled(enabled);
    }

    protected JToggleButton createToggleButton(Action action) {
        final JToggleButton button = new JToggleButton();
        addButtonProperties(action, button);
        return button;
    }

    private JButton createButton(Action action) {
        final JButton button = new JButton();
        addButtonProperties(action, button);
        return button;
    }

    private void addButtonProperties(Action action, AbstractButton button) {
        button.setMargin(new Insets(1, 1, 1, 1));
        button.setHideActionText(true);
        button.setAction(action);
        button.setFocusable(false);
        button.setDisabledIcon(null);
        button.setSelected(false);
        button.setVisible(true);
    }

    private void initListeners() {
        addFocusListener();
        addKeyListeners();
        addMouseListeners();
        addResizeListener();
        addMinMaximizedListener();
    }

    private void initStatusBar() {
        final StatusBar statusBar = new StatusBar();
        final Component horizontalStrut = Box.createHorizontalStrut(20);
        statusBar.add(horizontalStrut);
        for (Counter<?> counter : counters) {
            statusBar.addSeparator();
            statusBar.addCounter(counter, counter.getWidth());
        }
        statusBar.addSeparator();
        statusBar.addRamInfo();
        statusBar.addSeparator();
        statusBar.addConnectionDuration();
        statusBar.add(horizontalStrut);
        statusBar.add(Box.createHorizontalStrut(10));
        add(statusBar, BorderLayout.SOUTH);
        this.statusBar = statusBar;
        updateInputLocale();
        new Timer(5000, e -> updateInputLocale()).start();
    }

    private void updateInputLocale() {
        String currentKeyboardLayout = InputContext.getInstance().getLocale().toString();
        if (!currentKeyboardLayout.equals(statusBar.getKeyboardLayout())) {
            statusBar.setKeyboardLayout(currentKeyboardLayout);
        }
    }

    private void addFocusListener() {
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent ev) {
                fireOnKeyReleased(-1, Character.MIN_VALUE);
                if (windowsKeyActivated.get()) {
                    windowsKeyToggleButton.setSelected(false);
                    windowsKeyActivated.set(!windowsKeyActivated.get());
                }
                if (ctrlKeyActivated.get()) {
                    ctrlKeyToggleButton.setSelected(false);
                    ctrlKeyActivated.set(!ctrlKeyActivated.get());
                }
            }
        });
    }

    private void addMouseListeners() {
        screenPannel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent ev) {
                fireOnMousePressed(ev.getX(), ev.getY(), ev.getButton());
            }

            @Override
            public void mouseReleased(MouseEvent ev) {
                fireOnMouseReleased(ev.getX(), ev.getY(), ev.getButton());
            }
        });

        screenPannel.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent ev) {
                fireOnMouseMove(ev.getX(), ev.getY());
            }

            @Override
            public void mouseMoved(MouseEvent ev) {
                fireOnMouseMove(ev.getX(), ev.getY());
            }
        });

        screenPannel.addMouseWheelListener(ev -> {
            fireOnMouseWheeled(ev.getX(), ev.getY(), ev.getWheelRotation());
        });
    }

    private void addKeyListeners() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent ev) {
                fireOnKeyPressed(ev.getKeyCode(), ev.getKeyChar());
            }

            @Override
            public void keyReleased(KeyEvent ev) {
                fireOnKeyReleased(ev.getKeyCode(), ev.getKeyChar());
            }
        });
    }

    private void addResizeListener() {
        addComponentListener(new ComponentAdapter() {
            private Timer resizeTimer;

            @Override
            public void componentResized(ComponentEvent ev) {
                if (resizeTimer != null) {
                    resizeTimer.stop();
                }
                resizeTimer = new Timer(500, e -> resetCanvas());
                resizeTimer.setRepeats(false);
                resizeTimer.start();
            }
        });
    }

    private void addMinMaximizedListener() {
        addWindowStateListener(event -> isImmutableWindowsSize.set((event.getNewState() & Frame.ICONIFIED) == Frame.ICONIFIED || (event.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH));
    }

    public boolean getFitToScreenActivated() {
        return fitToScreenActivated.get();
    }

    public boolean getKeepAspectRatioActivated() {
        return keepAspectRatioActivated.get();
    }

    public double getxFactor() {
        return xFactor;
    }

    public double getyFactor() {
        return yFactor;
    }

    public Dimension getCanvas() {
        return canvas;
    }

    public CanvasPannel getScreenPannel() {
        return screenPannel;
    }

    public void launch(int screenNum) {
        if (selectScreenMenu != null) {
            optionsMenu.remove(selectScreenMenu);
        }
        if (screenNum > 0) {
            this.optionsMenu.add(createScreenSelectionMenu(screenNum));
        }
        long sessionStartTime = Instant.now().getEpochSecond();
        sessionTimer = new Timer(1000, e -> {
            final long seconds = Instant.now().getEpochSecond() - sessionStartTime;
            statusBar.setSessionDuration(format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60));
        });
        sessionTimer.start();
        sendClipboardButton.setEnabled(true);
        reqClipboardButton.setEnabled(true);
        SwingUtilities.invokeLater(() -> this.setVisible(true));
    }

    public void close() {
        if (sessionTimer != null) {
            sessionTimer.stop();
        }
        SwingUtilities.invokeLater(() -> {
            this.setVisible(false);
            this.dispose();
        });
    }


    public void computeScaleFactors(int sourceWidth, int sourceHeight, boolean keepAspectRatio) {
        Log.debug(format("ComputeScaleFactors for w: %d h: %d", sourceWidth, sourceHeight));
        canvas = screenPanelWrapper.getSize();
        canvas.setSize(canvas.getWidth() - OFFSET, canvas.getHeight() - OFFSET);
        xFactor = canvas.getWidth() / sourceWidth;
        yFactor = canvas.getHeight() / sourceHeight;
        if (keepAspectRatio && abs(xFactor - yFactor) > 0.01) {
            resizeWindow(sourceWidth, sourceHeight);
        }
    }

    private void resizeWindow(int sourceWidth, int sourceHeight) {
        Log.debug("%s", () -> format("Resize  W:H %d:%d x:y %f:%f", this.getWidth(), this.getHeight(), xFactor, yFactor));
        int menuHeight = this.getHeight() - canvas.height;
        final Rectangle maximumWindowBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (xFactor < yFactor) {
            if ((sourceWidth * yFactor) + OFFSET < maximumWindowBounds.width) {
                xFactor = yFactor;
                this.setSize((int) (sourceWidth * xFactor) + OFFSET, this.getHeight());
            } else {
                yFactor = xFactor;
                this.setSize(this.getWidth(), (int) (sourceHeight * yFactor) + menuHeight + OFFSET);
            }
        } else {
            if ((sourceHeight * xFactor) + menuHeight + OFFSET < maximumWindowBounds.height) {
                yFactor = xFactor;
                this.setSize(this.getWidth(), (int) (sourceHeight * yFactor) + menuHeight + OFFSET);
            } else {
                xFactor = yFactor;
                this.setSize((int) (sourceWidth * xFactor) + OFFSET, this.getHeight());
            }
        }
        Log.debug("%s", () -> format("Resized W:H %d:%d x:y %f:%f", this.getWidth(), this.getHeight(), xFactor, yFactor));
    }

    private void resetFactors() {
        xFactor = DEFAULT_FACTOR;
        yFactor = DEFAULT_FACTOR;
    }

    void resetCanvas() {
        canvas = null;
    }

    private void fireOnMouseMove(int x, int y) {
        listeners.getListeners().forEach(listener -> listener.onMouseMove(scaleXPosition(x), scaleYPosition(y)));
    }

    private void fireOnMousePressed(int x, int y, int button) {
        listeners.getListeners().forEach(listener -> listener.onMousePressed(scaleXPosition(x), scaleYPosition(y), button));
    }

    private void fireOnMouseReleased(int x, int y, int button) {
        listeners.getListeners().forEach(listener -> listener.onMouseReleased(scaleXPosition(x), scaleYPosition(y), button));
    }

    private void fireOnMouseWheeled(int x, int y, int rotations) {
        listeners.getListeners().forEach(listener -> listener.onMouseWheeled(scaleXPosition(x), scaleYPosition(y), rotations));
    }

    private int scaleYPosition(int y) {
        return (int) Math.round(y / yFactor);
    }

    private int scaleXPosition(int x) {
        return (int) Math.round(x / xFactor);
    }

    private void fireOnKeyPressed(int keyCode, char keyChar) {
        listeners.getListeners().forEach(listener -> listener.onKeyPressed(keyCode, keyChar));
    }

    private void fireOnKeyReleased(int keyCode, char keyChar) {
        listeners.getListeners().forEach(listener -> listener.onKeyReleased(keyCode, keyChar));
    }

    static class CanvasPannel extends JPanel {

        private int captureWidth = -1;
        private int captureHeight = -1;

        private transient BufferedImage captureImage;

        CanvasPannel() {
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (captureImage != null) {
                g.drawImage(captureImage, 0, 0, this);
            }
        }

        void onCaptureUpdated(final BufferedImage captureImage) {
            SwingUtilities.invokeLater(() -> {
                final int captureImageWidth = captureImage.getWidth();
                final int captureImageHeight = captureImage.getHeight();
                if (captureWidth != captureImageWidth || captureHeight != captureImageHeight) {
                    this.captureWidth = captureImageWidth;
                    this.captureHeight = captureImageHeight;
                    final Dimension size = new Dimension(captureImageWidth, captureImageHeight);
                    setSize(size);
                    setPreferredSize(size);
                }
                this.captureImage = captureImage;
                repaint();
            });
        }
    }
}
