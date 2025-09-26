package io.github.springstudent.dekstop.client.core;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import io.github.springstudent.dekstop.common.utils.EmptyUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author ZhouNing
 * @date 2024/12/9 10:53
 **/
public abstract class RemoteFrame extends JFrame {
    private JLabel titleLabel;
    private JTextField deviceCodeField;
    private JTextField passwordField;
    private JTextField remoteDeviceField;
    private JButton connectButton;
    private JLabel controlledLabel;
    private JLabel closeSessionLabel;

    private char osId;

    public RemoteFrame() {
        osId = System.getProperty("os.name").toLowerCase().charAt(0);
        initFrame();
        initTitle();
        initPannel();
        this.setVisible(true);
    }

    private void initFrame() {
        this.setUndecorated(true);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(600, 400);
        this.setLayout(new BorderLayout());
        this.setLocationRelativeTo(null);
    }

    private void initTitle() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBorder(new EmptyBorder(5, 10, 5, 10));
        titleBar.setBackground(Color.WHITE);
        titleLabel = new JLabel();
        titleLabel.setText("<html>远程桌面控制<span style='color:red;'>（连接中）</span></html>");
        titleLabel.setForeground(Color.black);
        titleBar.add(titleLabel, BorderLayout.WEST);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);
        JButton minimizeButton = createTitleBarButton("最小化(-)");
        minimizeButton.addActionListener(e -> this.setState(JFrame.ICONIFIED));
        JButton closeButton = createTitleBarButton("关闭(x)");
        closeButton.addActionListener(e -> System.exit(0));
        buttonPanel.add(minimizeButton);
        buttonPanel.add(closeButton);
        titleBar.add(buttonPanel, BorderLayout.EAST);
        MouseAdapter dragListener = new MouseAdapter() {
            private Point initialClick;

            @Override
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int xMoved = e.getXOnScreen() - initialClick.x;
                int yMoved = e.getYOnScreen() - initialClick.y;
                setLocation(xMoved, yMoved);
            }
        };
        titleBar.addMouseListener(dragListener);
        titleBar.addMouseMotionListener(dragListener);
        this.add(titleBar, BorderLayout.NORTH);
    }

    /**
     * 创建标题栏按钮
     */
    private JButton createTitleBarButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        button.setBackground(new Color(255, 255, 255));
        button.setForeground(Color.BLACK);
        return button;
    }

    private void initPannel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(5, 20, 5, 20));

        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(new EmptyBorder(0, 0, 20, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 10, 0, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        topPanel.add(new JLabel("设备代码:"), gbc);
        gbc.gridx = 1;
        this.deviceCodeField = new JTextField(10);
        deviceCodeField.setText("");
        deviceCodeField.setEditable(false);
        topPanel.add(deviceCodeField, gbc);
        gbc.gridx = 2;
        topPanel.add(new JLabel("临时密码:"), gbc);
        gbc.gridx = 3;
        JPanel passwordPanel = new JPanel(new BorderLayout());
        this.passwordField = new JTextField(10);
        passwordField.setText("");
        passwordField.setEditable(false);
        passwordField.setDocument(new PlainDocument() {
            @Override
            public void insertString(int offs, String str, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
                if (str != null && ReUtil.isMatch("^[a-zA-Z0-9]+$", str)) {
                    super.insertString(offs, str, a);
                } else {
                    showMessageDialog("密码只能为数字与字母的组合", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        passwordPanel.add(passwordField, BorderLayout.CENTER);
        JButton editButton = new JButton("✎");
        editButton.setMargin(new Insets(0, 2, 0, 2));
        editButton.setFocusPainted(false);
        editButton.setBorder(BorderFactory.createEmptyBorder());
        editButton.setContentAreaFilled(false);
        editButton.addActionListener(e -> {
            if (isConnect()) {
                passwordField.setEditable(true);
                passwordField.requestFocusInWindow();
            } else {
                showMessageDialog("请等待连接连接服务器成功", JOptionPane.ERROR_MESSAGE);
            }
        });
        passwordField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (EmptyUtils.isEmpty(passwordField.getText())) {
                    showMessageDialog("密码不能为空", JOptionPane.ERROR_MESSAGE);
                } else {
                    passwordField.setEditable(false);
                    passwordField.transferFocus();
                    changePassword(deviceCodeField.getText(), passwordField.getText());
                }
            }
        });
        passwordPanel.add(editButton, BorderLayout.EAST);
        topPanel.add(passwordPanel, gbc);

        JPanel bottomPanel = new JPanel(new GridBagLayout());
        bottomPanel.setBorder(new EmptyBorder(20, 0, 0, 0));
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(0, 10, 0, 10);
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        bottomPanel.add(new JLabel("远程设备代码:"), gbc);
        gbc.gridx = 1;
        this.remoteDeviceField = new JTextField(15);
        bottomPanel.add(remoteDeviceField, gbc);

        gbc.gridx = 2;
        this.connectButton = new JButton("连接");
        this.connectButton.addActionListener(e -> {
            if (StrUtil.isEmpty(remoteDeviceField.getText())) {
                showMessageDialog("请输入远程设备代码", JOptionPane.ERROR_MESSAGE);
            } else if (remoteDeviceField.getText().equals(deviceCodeField.getText())) {
                showMessageDialog("远程设备代码不能为自己", JOptionPane.ERROR_MESSAGE);
            } else if (!ReUtil.isMatch("^[a-zA-Z0-9]+$", remoteDeviceField.getText())) {
                showMessageDialog("远程设备代码只能为数字与字母的组合", JOptionPane.ERROR_MESSAGE);
            } else {
                if (!isConnect()) {
                    showMessageDialog("请等待连接连接服务器成功", JOptionPane.ERROR_MESSAGE);
                } else {
                    beforeOpenRemoteScreen(remoteDeviceField.getText());
                }
            }
        });
        bottomPanel.add(connectButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(-55, 10, 0, 10);
        this.controlledLabel = new JLabel("正在被远程控制中...");
        bottomPanel.add(controlledLabel, gbc);
        controlledLabel.setVisible(false);
        gbc.gridx = 2;
        this.closeSessionLabel = new JLabel("<html><u>断开连接</u></html>");
        closeSessionLabel.setForeground(Color.BLUE);
        closeSessionLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                closeRemoteScreen(deviceCodeField.getText());
            }
        });
        closeSessionLabel.setVisible(false);
        bottomPanel.add(closeSessionLabel, gbc);
        mainPanel.add(topPanel);
        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(bottomPanel);
        this.add(mainPanel, BorderLayout.CENTER);
        this.getContentPane().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Component comp = getContentPane().getComponentAt(e.getPoint());
                if (!(comp instanceof JTextField)) {
                    // 如果点击的不是文本框，则将焦点设置到内容面板上
                    getContentPane().requestFocusInWindow();
                }
            }
        });
    }

    public abstract void changePassword(String deviceCode, String password);

    protected abstract void beforeOpenRemoteScreen(String text);

    public final void openRemoteScreen() {
        String password = JOptionPane.showInputDialog(
                this,
                "请输入远程设备密码：",
                "输入密码",
                JOptionPane.PLAIN_MESSAGE
        );
        if (password != null && !password.trim().isEmpty()) {
            openRemoteScreen(remoteDeviceField.getText(), password.trim());
        } else if (password != null) {
            showMessageDialog("密码不能为空", JOptionPane.ERROR_MESSAGE);
        }
    }

    public abstract boolean isConnect();

    public abstract void openRemoteScreen(String deviceCode, String password);

    public abstract void closeRemoteScreen();

    public abstract void closeRemoteScreen(String deviceCode);

    public final void setDeviceCodeAndPassword(String deviceCode, String password) {
        this.deviceCodeField.setText(deviceCode);
        this.passwordField.setText(password);
    }

    public final void updateConnectionStatus(boolean connected) {
        if (connected) {
            titleLabel.setText("<html>远程桌面控制<span style='color:blue;'>（已就绪）</span></html>");
        } else {
            titleLabel.setText("<html>远程桌面控制<span style='color:red;'>（连接中）</span></html>");
            passwordField.setEditable(false);
        }
    }

    public final void setControlledAndCloseSessionLabelVisible(boolean flag) {
        this.controlledLabel.setVisible(flag);
        this.closeSessionLabel.setVisible(flag);
    }

    public void showMessageDialog(Object msg, int messageType) {
        JOptionPane.showMessageDialog(this, msg, "提示", messageType);
    }

    public char getOsId() {
        return osId;
    }
}