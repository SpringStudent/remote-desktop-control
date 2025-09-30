package io.github.springstudent.desktop.robots;

import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.bean.RobotCaptureResponse;
import io.github.springstudent.dekstop.common.remote.bean.RobotCaputureReq;
import io.github.springstudent.dekstop.common.remote.bean.RobotKeyControl;
import io.github.springstudent.dekstop.common.remote.bean.RobotMouseControl;

import javax.swing.*;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class RobotsServer {
    private volatile boolean running;

    private final int port;

    private ServerSocket serverSocket;

    private RobotsHandler remoteRobots;

    private static char osId = System.getProperty("os.name").toLowerCase().charAt(0);

    public RobotsServer(int port) {
        this.port = port;
        this.remoteRobots = new RobotsHandler();
    }

    /**
     * 开启服务
     */
    public void start() {
        running = true;
        // 创建 JFrame 绑定当前交互式桌面
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setUndecorated(true);
            frame.setSize(0, 0);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
        try {
            this.serverSocket = new ServerSocket(port);
            Log.info("Robots Server started on port " + port);
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    Log.info("Client connected");
                    new Thread(() -> handleClient(socket)).start();
                } catch (IOException e) {
                    if (running) {
                        Log.error("Error accepting client", e);
                    } else {
                        Log.info("Server stopped accepting connections.");
                    }
                }
            }
        } catch (IOException e) {
            Log.error("Failed to start Robots Server", e);
        } finally {
            stop();
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket; ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream()); ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            while (running) {
                Object obj = in.readObject();
                if (obj != null) {
                    if (obj instanceof RobotMouseControl) {
                        remoteRobots.handleMouseControl((RobotMouseControl) obj);
                    } else if (obj instanceof RobotKeyControl) {
                        remoteRobots.handleKeyControl((RobotKeyControl) obj);
                    } else if (obj instanceof RobotCaputureReq) {
                        new Thread(() -> {
                            try {
                                byte[] screenBytes = remoteRobots.captureScreen();
                                RobotCaptureResponse response = new RobotCaptureResponse(screenBytes, ((RobotCaputureReq) obj).getId());
                                synchronized (out) {
                                    out.writeObject(response);
                                    out.flush();
                                    out.reset();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                }
            }
        } catch (EOFException eof) {
            Log.info("Client disconnected: " + socket.getInetAddress());
        } catch (Exception e) {
            Log.error("Error handling client: " + socket.getInetAddress(), e);
        }
    }

    /**
     * 停止服务
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.error("Error closing server socket", e);
        }
        Log.info("Robots Server stopped.");
    }

    public static char getOsId() {
        return osId;
    }

    public static void main(String[] args) {
        RobotsServer server = new RobotsServer(55678);
        server.start();
    }
}
