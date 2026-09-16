package io.github.springstudent.dekstop.common.bean;
/**
 * @author ZhouNing
 * @date 2024/12/10 14:57
 **/
public class Constants {

    /**
     * 心跳间隔时长
     */
    public static final int HEARTBEAT_DURATION_SECONDS = 3;

    /**
     * 客户端心跳超时时长
     */
    public static final int CLIENT_SESSION_TIMEOUT_MILLS = 1000 * 15;

    public static final String CONTROLLER = "CONTROLLER";

    public static final String CONTROLLED = "CONTROLLED";

    /**
     * Maximum number of tiles; currently a tile is basically a 32x32 byte array (i.e., 1K for gray, 4K for color).
     */
    public static final int DEFAULT_MAX_SIZE = 32 * 1024;

    /**
     * Number of tiles after a purge.
     */
    public static final int DEFAULT_PURGE_SIZE = 16 * 1024;

    /**
     * Zstd compression level (1=fastest, 3=default, 19-22=maximum).
     * Level 1 is preferred for real-time screen capture — LAN bandwidth is plentiful.
     */
    public static final int DEFAULT_ZSTD_COMPRESSION_LEVEL = 1;

    /**
     * 中转服务器转发画面数据时的写缓冲水位线(字节)。
     * <p>
     * 超过高水位即认为控制端消费不过来:服务器停止读取被控端数据,让 TCP 接收窗口关闭,
     * 把背压一路传回被控端(被控端随之暂停采集),而不是把帧无限制地堆在服务器内存里 ——
     * 那样做的结果是画面延迟越积越大,然后突然追平(卡一下再跳一大片)。
     * <p>
     * 取值是"允许的积压量"与"链路带宽"之间的折中:积压 256KB 在 2Mbps 链路上约 1 秒,
     * 在 20Mbps 上约 0.1 秒。链路慢时可以调小,链路快但抖动大时可以调大。
     */
    public static final int RELAY_WRITE_BUFFER_LOW_WATER_MARK = 128 * 1024;

    public static final int RELAY_WRITE_BUFFER_HIGH_WATER_MARK = 256 * 1024;

}
