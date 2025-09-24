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


}
