package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.bean.RobotCaptureResponse;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

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

    /**
     * 监听线程池
     */
    private final ExecutorService listenExecutor = Executors.newCachedThreadPool();

    /**
     * 重连线程池
     */
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();

    private final int reconnectDelayMillis = 1500;

    private ConcurrentHashMap<Long, CompletableFuture<RobotCaptureResponse>> captureFutureMap = new ConcurrentHashMap<>();

    private final Timer timer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 512);

    public RobotsClient(int port) {
        this.port = port;
        if (RemoteClient.getRemoteClient().getOsId() == 'w') {
            connectWithRetry();
        }
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
                if (obj instanceof RobotCaptureResponse) {
                    RobotCaptureResponse response = (RobotCaptureResponse) obj;
                    CompletableFuture<RobotCaptureResponse> future = removeCaptureFuture(response.getId());
                    if (future != null) {
                        future.complete(response);
                    }
                }
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
     * 关闭连接并清理资源
     */
    private synchronized void disconnectAndCleanup() {
        if (!connected) {
            return;
        }
        connected = false;
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
        timer.stop();
        listenExecutor.shutdownNow();
        retryExecutor.shutdownNow();
        Log.info("RobotsClient stopped.");
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

    public void addCaptureFuture(Long id, CompletableFuture<RobotCaptureResponse> future) {
        captureFutureMap.put(id, future);
        timer.newTimeout(t -> {
            CompletableFuture<RobotCaptureResponse> f = captureFutureMap.remove(id);
            if (f != null && !f.isDone()) {
                f.completeExceptionally(new TimeoutException("Request " + id + " timeout after " + 1500 + "ms"));
            }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<RobotCaptureResponse> removeCaptureFuture(Long id) {
        return captureFutureMap.remove(id);
    }
}
