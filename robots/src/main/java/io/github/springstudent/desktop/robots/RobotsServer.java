package io.github.springstudent.desktop.robots;

import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.bean.RobotKeyControl;
import io.github.springstudent.dekstop.common.remote.bean.RobotMouseControl;
import io.github.springstudent.dekstop.common.remote.bean.SendClipboardRequest;
import io.github.springstudent.dekstop.common.remote.bean.SetClipboardRequest;

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

    public RobotsServer(int port) {
        this.port = port;
        this.remoteRobots = new RobotsHandler();
    }

    /**
     * 开启服务
     */
    public void start() {
        running = true;
        try (ServerSocket server = new ServerSocket(port)) {
            this.serverSocket = server;
            Log.info("Robots Server started on port " + port);

            while (running) {
                try {
                    Socket socket = server.accept();
                    Log.info("Client connected: " + socket.getInetAddress());
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
        try (Socket client = socket;
             ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            while (running) {
                Object obj = in.readObject();
                if (obj != null) {
                    Log.info("Received obj: " + obj);
                    if (obj instanceof RobotMouseControl) {
                        remoteRobots.handleMessage((RobotMouseControl) obj);
                    } else if (obj instanceof RobotKeyControl) {
                        remoteRobots.handleMessage((RobotKeyControl) obj);
                    } else if (obj instanceof SendClipboardRequest) {
                        remoteRobots.sendClipboard((SendClipboardRequest) obj).whenComplete((response, ex) -> {
                            try {
                                if (ex == null && !socket.isClosed()) {
                                    synchronized (out) {
                                        out.writeObject(response);
                                        out.flush();
                                        out.reset();
                                        Log.info("Sent response: " + response);
                                    }
                                }
                            } catch (IOException e) {
                                Log.error("Failed to send SendClipboardResponse", e);
                            }
                        });

                    } else if (obj instanceof SetClipboardRequest) {
                        remoteRobots.setClipboard((SetClipboardRequest) obj).whenComplete((response, ex) -> {
                            try {
                                if (ex == null && !socket.isClosed()) {
                                    synchronized (out) {
                                        out.writeObject(response);
                                        out.flush();
                                        out.reset();
                                        Log.info("Sent response: " + response);
                                    }
                                }
                            } catch (IOException e) {
                                Log.error("Failed to send SetClipboardResponse", e);
                            }
                        });
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

    public static void main(String[] args) {
        new RobotsServer(56789).start();
    }
}
