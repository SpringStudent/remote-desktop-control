package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.bean.SendClipboardResponse;
import io.github.springstudent.dekstop.common.remote.bean.SetClipboardResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 控制方客户端
 *
 * @author ZhouNing
 * @date 2025/9/30 8:39
 **/
public class RobotsClient {
    private final int port;
    private volatile boolean running = false;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final ConcurrentHashMap<Integer, CompletableFuture<SendClipboardResponse>> sendClipboardFutureMap;
    private final ConcurrentHashMap<Integer, CompletableFuture<SetClipboardResponse>> setClipboardFutureMap;

    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    private final int reconnectDelayMillis = 1500;

    public RobotsClient(int port) {
        this.port = port;
        this.sendClipboardFutureMap = new ConcurrentHashMap<>(32);
        this.setClipboardFutureMap = new ConcurrentHashMap<>(32);
        connectWithRetry();
    }

    /**
     * 尝试连接（可能失败）
     */
    private void connect() throws IOException {
        this.socket = new Socket("localhost", port);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        this.running = true;
        executor.execute(this::listenServer);
        Log.info("Connected to server :" + port);
    }

    /**
     * 自动重连
     */
    private void connectWithRetry() {
        executor.execute(() -> {
            while (!running) {
                try {
                    connect();
                    return;
                } catch (IOException e) {
                    Log.warn("Connect failed, retrying in " + reconnectDelayMillis + "ms", e);
                    try {
                        Thread.sleep(reconnectDelayMillis);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        });
    }

    /**
     * 监听服务端返回的消息
     */
    private void listenServer() {
        try {
            while (running && !socket.isClosed()) {
                Object obj = in.readObject();
                handleServerMessage(obj);
            }
        } catch (EOFException eof) {
            Log.info("Server closed connection.");
        } catch (Exception e) {
            Log.error("Error reading from server", e);
        } finally {
            close();
            Log.info("Try reconnecting...");
            connectWithRetry();
        }
    }

    /**
     * 处理不同的响应消息
     */
    private void handleServerMessage(Object obj) {
        Log.info("Received from server: " + obj);
        if (obj instanceof SendClipboardResponse) {
            SendClipboardResponse response = (SendClipboardResponse) obj;
            CompletableFuture<SendClipboardResponse> future = getSendClipboardFuture(response.getId());
            if (future != null) {
                future.complete(response);
            } else {
                Log.warn("No future found for SendClipboardResponse id=" + response.getId());
            }
        } else if (obj instanceof SetClipboardResponse) {
            SetClipboardResponse response = (SetClipboardResponse) obj;
            CompletableFuture<SetClipboardResponse> future = getSetClipboardFuture(response.getId());
            if (future != null) {
                future.complete(response);
            } else {
                Log.warn("No future found for SetClipboardResponse id=" + response.getId());
            }
        } else {
            Log.warn("Unknown message type received: " + obj.getClass().getName());
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        running = false;
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
        Log.info("Disconnected from server.");
    }

    /**
     * 通用发送方法
     */
    public synchronized void send(Object obj) throws IOException {
        if (!running) {
            throw new IOException("Not connected to server.");
        }
        out.writeObject(obj);
        out.flush();
    }

    /**
     * Future 管理
     */
    public void addSendClipboardFuture(int id, CompletableFuture<SendClipboardResponse> future) {
        sendClipboardFutureMap.put(id, future);
    }

    public void addSetClipboardFuture(int id, CompletableFuture<SetClipboardResponse> future) {
        setClipboardFutureMap.put(id, future);
    }

    public CompletableFuture<SendClipboardResponse> getSendClipboardFuture(int id) {
        return sendClipboardFutureMap.remove(id);
    }

    public CompletableFuture<SetClipboardResponse> getSetClipboardFuture(int id) {
        return setClipboardFutureMap.remove(id);
    }
}

