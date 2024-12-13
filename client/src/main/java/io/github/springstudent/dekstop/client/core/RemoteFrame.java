package io.github.springstudent.dekstop.client.core;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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

    public RemoteFrame() {
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
        this.passwordField = new JTextField(10);
        passwordField.setText("");
        topPanel.add(passwordField, gbc);

        JPanel bottomPanel = new JPanel(new GridBagLayout());
        bottomPanel.setBorder(new EmptyBorder(20, 0, 0, 0));
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(0, 10, 0, 10);
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        bottomPanel.add(new JLabel("远程控制设备:"), gbc);
        gbc.gridx = 1;
        this.remoteDeviceField = new JTextField(15);
        bottomPanel.add(remoteDeviceField, gbc);

        gbc.gridx = 2;
        this.connectButton = new JButton("连接");
        this.connectButton.addActionListener(e -> {
            openRemoteScreen(remoteDeviceField.getText());
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
                closeRemoteScreen(remoteDeviceField.getText());
            }
        });
        closeSessionLabel.setVisible(false);
        bottomPanel.add(closeSessionLabel, gbc);
        mainPanel.add(topPanel);
        mainPanel.add(Box.createVerticalStrut(5)); // 间距
        mainPanel.add(bottomPanel);
        this.add(mainPanel, BorderLayout.CENTER);
    }


    public void showMessageDialog(Object msg, int messageType) {
        JOptionPane.showMessageDialog(this, msg, "提示", messageType);
    }


    public abstract void openRemoteScreen(String remoteName);

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
        }
    }

    public final void setControlledAndCloseSessionLabelVisible(boolean flag) {
        this.controlledLabel.setVisible(flag);
        this.closeSessionLabel.setVisible(flag);
    }

}