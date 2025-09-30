package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.bean.SendClipboardResponse;
import io.github.springstudent.dekstop.common.remote.bean.SetClipboardResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 控制方客户端
 *
 * @author ZhouNing
 * @date 2025/9/30
 **/
public class RobotsClient {
    private final int port;
    private volatile boolean connected = false;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final ConcurrentHashMap<Integer, CompletableFuture<SendClipboardResponse>> sendClipboardFutureMap;
    private final ConcurrentHashMap<Integer, CompletableFuture<SetClipboardResponse>> setClipboardFutureMap;

    /**
     * 监听线程池
     */
    private final ExecutorService listenExecutor = Executors.newCachedThreadPool();

    /**
     * 重连线程池
     */
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();

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
    private synchronized void connect() throws IOException {
        if (!running.get() || connected) {
            return;
        }
        this.socket = new Socket("localhost", port);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        this.connected = true;
        listenExecutor.submit(this::listenServer);
        Log.info("Connected to server :" + port);
    }

    /**
     * 定时重连
     */
    private void connectWithRetry() {
        retryExecutor.scheduleWithFixedDelay(() -> {
            if (!running.get() || connected) {
                return;
            }
            try {
                connect();
            } catch (IOException e) {
                Log.warn("Connect failed, will retry in " + reconnectDelayMillis + "ms", e);
            }
        }, 0, reconnectDelayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 监听服务端返回的消息
     */
    private void listenServer() {
        try {
            while (running.get() && connected && !socket.isClosed()) {
                Object obj = in.readObject();
                handleServerMessage(obj);
            }
        } catch (EOFException | SocketException eof) {
            Log.info("Server closed connection.");
        } catch (Exception e) {
            Log.error("Error reading from server", e);
        } finally {
            disconnectAndCleanup();
        }
    }

    /**
     * 处理不同的响应消息
     */
    private void handleServerMessage(Object obj) {
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
     * 关闭连接并清理资源
     */
    private synchronized void disconnectAndCleanup() {
        if (!connected) {
            return;
        }
        connected = false;
        failAllPendingFutures(new IOException("Connection lost"));
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        Log.info("Disconnected from server. Waiting for reconnect...");
    }

    /**
     * 完全关闭客户端
     */
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        disconnectAndCleanup();
        listenExecutor.shutdownNow();
        retryExecutor.shutdownNow();
        Log.info("RobotsClient stopped.");
    }

    /**
     * 未完成的 future 在断开时全部失败
     */
    private void failAllPendingFutures(Throwable t) {
        sendClipboardFutureMap.forEach((id, f) -> f.completeExceptionally(t));
        sendClipboardFutureMap.clear();
        setClipboardFutureMap.forEach((id, f) -> f.completeExceptionally(t));
        setClipboardFutureMap.clear();
    }

    /**
     * 通用发送方法
     */
    public synchronized void send(Object obj) throws IOException {
        if (!connected || socket == null || socket.isClosed()) {
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
