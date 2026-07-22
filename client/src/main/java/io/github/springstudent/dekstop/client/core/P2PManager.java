package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.protocol.NettyDecoder;
import io.github.springstudent.dekstop.common.protocol.NettyEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.String.format;

/**
 * Manages P2P direct connections between controller and controlled peers on the same LAN.
 * The controlled peer starts a temporary Netty server socket; the controller peer connects to it.
 * If the direct connection fails, both peers transparently fall back to server relay.
 *
 * @author ZhouNing
 * @date 2026/07/21
 */
public class P2PManager {

    private NioEventLoopGroup listenerGroup;
    private NioEventLoopGroup connectorGroup;
    private Channel listenerChannel;
    private int listenerPort;
    private Channel p2pChannel;
    private final Object lock = new Object();
    private String configuredBindHost;
    private int configuredBindPort;
    private volatile Consumer<Channel> onChannelAccepted;

    /**
     * Start a temporary Netty server socket.
     * Called by the controlled peer after receiving CmdResCapture.START_.
     *
     * @param bindHost the address to bind to, or null to bind to all interfaces
     * @param bindPort the port to bind to, or <= 0 to use a random port
     * @return the actual port number, or -1 if failed
     */
    public int startListener(String bindHost, int bindPort) {
        this.configuredBindHost = bindHost;
        this.configuredBindPort = bindPort;
        try {
            listenerGroup = new NioEventLoopGroup(1);
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(listenerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new NettyDecoder());
                            ch.pipeline().addLast(new NettyEncoder());
                            ch.pipeline().addLast(new P2PChannelHandler());
                            setAcceptedChannel(ch);
                        }
                    });

            // Bind to configured address or all interfaces
            java.net.InetSocketAddress bindAddr;
            if (bindHost != null && !bindHost.isEmpty()) {
                bindAddr = new java.net.InetSocketAddress(bindHost, bindPort > 0 ? bindPort : 0);
            } else {
                bindAddr = new java.net.InetSocketAddress(bindPort > 0 ? bindPort : 0);
            }
            Channel serverChannel = bootstrap.bind(bindAddr).sync().channel();
            listenerPort = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
            listenerChannel = serverChannel;
            Log.info(format("P2P listener started on port %d", listenerPort));
            return listenerPort;
        } catch (Exception e) {
            Log.error(format("Failed to start P2P listener: %s", e.getMessage()));
            shutdownListener();
            return -1;
        }
    }

    /**
     * Connect to the controlled peer's P2P listener.
     * Called by the controller peer after receiving CmdP2POffer.
     *
     * @param addresses list of candidate IP addresses
     * @param port      the listener port
     * @return the connected Channel, or null if all attempts failed
     */
    public Channel connect(List<String> addresses, int port) {
        for (String addr : addresses) {
            try {
                Log.info(format("P2P trying to connect to %s:%d", addr, port));
                Bootstrap bootstrap = new Bootstrap();
                connectorGroup = new NioEventLoopGroup(1);
                Channel ch = bootstrap.group(connectorGroup)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new NettyDecoder());
                                ch.pipeline().addLast(new NettyEncoder());
                                ch.pipeline().addLast(new P2PChannelHandler());
                            }
                        })
                        .connect(addr, port).sync().channel();

                if (ch.isActive()) {
                    synchronized (lock) {
                        this.p2pChannel = ch;
                    }
                    Log.info(format("P2P connection established to %s:%d", addr, port));
                    return ch;
                }
            } catch (Exception e) {
                Log.warn(format("P2P connection to %s:%d failed: %s", addr, port, e.getMessage()));
                if (connectorGroup != null) {
                    connectorGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
                    connectorGroup = null;
                }
            }
        }
        Log.warn(format("P2P connection failed to all %d addresses", addresses.size()));
        return null;
    }

    /**
     * Store a P2P channel that was accepted by the listener (controlled side).
     * Also invokes the onChannelAccepted callback to notify the controlled peer
     * immediately, without waiting for the relay-round-tripped CmdP2PAnswer.
     */
    public void setAcceptedChannel(Channel channel) {
        synchronized (lock) {
            this.p2pChannel = channel;
        }
        Log.info(format("P2P connection accepted from %s", channel.remoteAddress()));
        // Notify the controlled peer so it can activate P2P outbound routing immediately.
        // This avoids the race condition where CmdP2PAnswer (via relay) arrives before
        // the P2P listener event loop has processed this accept.
        Consumer<Channel> callback = onChannelAccepted;
        if (callback != null) {
            callback.accept(channel);
        }
    }

    /**
     * @return the active P2P channel, or null
     */
    public Channel getP2PChannel() {
        synchronized (lock) {
            if (p2pChannel != null && p2pChannel.isActive()) {
                return p2pChannel;
            }
            return null;
        }
    }

    /**
     * @return true if the P2P channel is established and active
     */
    public boolean isP2PActive() {
        return getP2PChannel() != null;
    }

    /**
     * Register a callback invoked when a P2P channel is accepted by the listener.
     * Called from the listener's event loop thread — the callback should be fast.
     */
    public void setOnChannelAccepted(Consumer<Channel> callback) {
        this.onChannelAccepted = callback;
    }

    /**
     * Get the addresses to advertise to the controller peer.
     * If a specific bind host was configured, returns only that address.
     * Otherwise enumerates all non-loopback site-local IPv4 addresses.
     */
    public List<String> getOfferAddresses() {
        if (configuredBindHost != null && !configuredBindHost.isEmpty()) {
            List<String> list = new ArrayList<>(1);
            list.add(configuredBindHost);
            return list;
        }
        return getLocalAddresses();
    }

    /**
     * Get all non-loopback site-local IPv4 addresses on this machine.
     */
    public static List<String> getLocalAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                        continue;
                    }
                    if (addr.getAddress().length == 4) { // IPv4 only
                        addresses.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            Log.error(format("Failed to enumerate network interfaces: %s", e.getMessage()));
        }
        return addresses;
    }

    /**
     * Clean up the P2P listener.
     */
    public void shutdownListener() {
        if (listenerChannel != null) {
            try {
                listenerChannel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            listenerChannel = null;
        }
        if (listenerGroup != null) {
            listenerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            listenerGroup = null;
        }
        listenerPort = -1;
    }

    /**
     * Shutdown everything: both listener and P2P channel.
     */
    public void shutdown() {
        shutdownListener();
        synchronized (lock) {
            if (p2pChannel != null) {
                try {
                    p2pChannel.close().sync();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                p2pChannel = null;
            }
        }
        if (connectorGroup != null) {
            connectorGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            connectorGroup = null;
        }
        onChannelAccepted = null;
    }
}
