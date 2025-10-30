package io.github.springstudent.dekstop.client.utils;

import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.client.jni.WinDesktop;
import io.github.springstudent.dekstop.common.bean.Gray8Bits;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.bean.RobotCaptureResponse;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static java.util.Arrays.stream;

public final class ScreenUtilities {

    private static final int NUMBER_OF_SCREENS;

    private static final Rectangle COMBINED_SCREEN_SIZE;

    private static final Rectangle DEFAULT_SIZE;

    private static final Robot ROBOT;

    private static Rectangle sharedScreenSize;

    private static int[] rgb;

    private static byte[] gray;

    private ScreenUtilities() {
    }

    static {
        NUMBER_OF_SCREENS = countScreens();
        DEFAULT_SIZE = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        COMBINED_SCREEN_SIZE = getCombinedScreenSize();
        sharedScreenSize = DEFAULT_SIZE;
        rgb = new int[sharedScreenSize.height * sharedScreenSize.width];
        gray = new byte[rgb.length];
        try {
            ROBOT = new Robot();
        } catch (AWTException ex) {
            throw new IllegalStateException("Could not initialize the AWT robot!", ex);
        }
    }

    public static synchronized void setShareAllScreens(int screenIndex) {
        if (screenIndex == -1) {
            sharedScreenSize = DEFAULT_SIZE;
        } else {
            GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            if (screenIndex < 0 || screenIndex >= screenDevices.length) {
                throw new IllegalArgumentException("Invalid screen index.");
            }
            sharedScreenSize = screenDevices[screenIndex].getDefaultConfiguration().getBounds();
        }
        rgb = new int[sharedScreenSize.height * sharedScreenSize.width];
        gray = new byte[rgb.length];
    }

    public static Rectangle getSharedScreenSize() {
        return new Rectangle(sharedScreenSize);
    }

    public static boolean inScreenBounds(int x, int y) {
        return sharedScreenSize.contains(x, y);
    }

    public static int getNumberOfScreens() {
        return NUMBER_OF_SCREENS;
    }

    private static int countScreens() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
    }

    private static Rectangle getCombinedScreenSize() {
        Rectangle fullSize = new Rectangle();
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        stream(environment.getScreenDevices()).flatMap(gd -> stream(gd.getConfigurations())).forEach(graphicsConfiguration -> Rectangle2D.union(fullSize, graphicsConfiguration.getBounds(), fullSize));
        return fullSize.getBounds();
    }

    public static byte[] captureGray(Gray8Bits quantization) {
        return rgbToGray8(quantization, captureRGB(sharedScreenSize));
    }

    public static byte[] captureColors() {
        final int[] ints = captureRGB(sharedScreenSize);
        ByteBuffer bb = ByteBuffer.allocate(4 * ints.length);
        bb.asIntBuffer().put(ints);
        return bb.array();
    }


    private static int[] robotCaptureRGB(Rectangle bounds) {
        BufferedImage image = ROBOT.createScreenCapture(bounds);
        final int imageHeight = min(image.getHeight(), bounds.height);
        final int imageWidth = min(image.getWidth(), bounds.width);
        return image.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);
    }

    private static int[] captureRGB(Rectangle bounds) {
        if (WinDesktop.isWindowsAndLockScreen()) {
            try {
                CompletableFuture<RobotCaptureResponse> captureFuture = RemoteClient.getRemoteClient().sendRobotCapture();
                RobotCaptureResponse response = captureFuture.get(1500, TimeUnit.MILLISECONDS);
                if (response != null && response.getScreenBytes() != null) {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(captureFuture.get().getScreenBytes())) {
                        BufferedImage image = ImageIO.read(bais);
                        if (image != null) {
                            int width = Math.min(image.getWidth(), bounds.width);
                            int height = Math.min(image.getHeight(), bounds.height);
                            return image.getRGB(0, 0, width, height, null, 0, width);
                        }
                    }
                }
            } catch (Exception e) {
                Log.error("WinDesktop capture failed, fallback to Robot", e);
            }
            return robotCaptureRGB(bounds);
        } else {
            return robotCaptureRGB(bounds);
        }
    }

    private static byte[] rgbToGray8(Gray8Bits quantization, int[] rgb) {
        return doRgbToGray8(quantization, rgb);
    }

    private static byte[] doRgbToGray8(Gray8Bits quantization, int[] rgb) {
        final byte[] xLevels = grays[quantization.ordinal()];
        final int length = min(rgb.length, gray.length);
        for (int idx = 0; idx < length; idx++) {
            final int pixel = rgb[idx];
            final int red = (pixel >> 16) & 0xFF;
            final int greenBlue = pixel & 0xFFFF;
            final int level = (red_levels[red] + green_blue_levels[greenBlue]) >> 7;
            gray[idx] = xLevels[level];
        }
        return gray;
    }

    private static final short[] red_levels;
    private static final short[] green_blue_levels;

    /*
     Cache the conversion from red/green/blue into gray levels.
     */
    static {
        red_levels = new short[256];
        for (int red = 0; red < 256; red++) {
            red_levels[red] = (short) (128.0 * 0.212671 * red);
        }
        green_blue_levels = new short[65536];
        for (int green = 0; green < 256; green++) {
            for (int blue = 0; blue < 256; blue++) {
                green_blue_levels[(green << 8) + blue] = (short) ((128.0 * 0.715160 * green) + (128.0 * 0.072169 * blue));
            }
        }
    }

    private static final byte[][] grays;

    /*
     Cache the quantization of all the gray levels (256).
     */
    static {
        final Gray8Bits[] quantizations = Gray8Bits.values();
        grays = new byte[quantizations.length][];
        for (final Gray8Bits quantization : quantizations) {
            grays[quantization.ordinal()] = new byte[256];
            final int factor = 256 / quantization.getLevels();
            for (int idx = 0; idx < 256; idx++) {
                // DOWN (0, 32, 64, 96, ...)
                // levels[quantization.ordinal()][idx] = (byte) (idx / factor * factor);
                // UP (31, 63,, 95, ...)
                grays[quantization.ordinal()][idx] = (byte) (((1 + (idx / factor)) * factor) - 1);
            }
        }
    }
}