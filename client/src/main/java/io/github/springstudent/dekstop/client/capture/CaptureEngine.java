package io.github.springstudent.dekstop.client.capture;

import io.github.springstudent.dekstop.client.bean.Capture;
import io.github.springstudent.dekstop.client.bean.CaptureTile;
import io.github.springstudent.dekstop.client.bean.Listeners;
import io.github.springstudent.dekstop.client.bean.Position;
import io.github.springstudent.dekstop.client.concurrent.RunnableEx;
import io.github.springstudent.dekstop.common.bean.Gray8Bits;
import io.github.springstudent.dekstop.common.configuration.CaptureEngineConfiguration;
import io.github.springstudent.dekstop.common.configuration.ReConfigurable;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.utils.UnitUtilities;

import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.min;
import static java.lang.String.format;

public class CaptureEngine implements ReConfigurable<CaptureEngineConfiguration> {

    private static final Dimension TILE_DIMENSION = new Dimension(32, 32);

    private final Dimension captureDimension;

    private final CaptureFactory captureFactory;

    private final Listeners<CaptureEngineListener> listeners = new Listeners<>();

    private Thread thread;

    /**
     * I keep only the checksum as I do not want to keep the referenceS to the
     * byte[] of the previous captureS.
     */
    private long[] previousCapture;

    private final Object reconfigurationLOCK = new Object();

    private CaptureEngineConfiguration configuration;

    private boolean reconfigured;

    public CaptureEngine(CaptureFactory captureFactory) {
        this.captureFactory = captureFactory;
        this.captureDimension = captureFactory.getDimension();
        final int x = (captureDimension.width + TILE_DIMENSION.width - 1) / TILE_DIMENSION.width;
        final int y = (captureDimension.height + TILE_DIMENSION.height - 1) / TILE_DIMENSION.height;
        this.previousCapture = new long[x * y];
        resetPreviousCapture();

    }

    @Override
    public void configure(CaptureEngineConfiguration configuration) {
        synchronized (reconfigurationLOCK) {
            this.configuration = configuration;
            this.reconfigured = true;
        }
    }

    @Override
    public void reconfigure(CaptureEngineConfiguration configuration) {
        configure(configuration);
    }

    public void addListener(CaptureEngineListener listener) {
        listeners.add(listener);
        // We're keeping locally a previous state, so we must be sure to send at
        // least once the previous capture state to the new listener.
        synchronized (reconfigurationLOCK) {
            this.reconfigured = true;
        }
    }

    public void start() {
        Log.debug("CaptureEngine start");
        this.thread = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                try {
                    CaptureEngine.this.mainLoop();
                } catch (InterruptedException e) {
                    thread.interrupt();
                }
            }
        }, "CaptureEngine");
        thread.start();
    }

    public void stop() {
        Log.debug("CaptureEngine stop");
        if (thread == null) {
            return;
        }
        thread.interrupt();
    }

    private void mainLoop() throws InterruptedException {
        Gray8Bits quantization = null;
        boolean captureColors = false;
        int tick = -1;
        long start = -1L;
        int captureId = 0;
        int captureCount = 0;
        int skipped = 0;
        AtomicBoolean reset = new AtomicBoolean(false);

        while (true) {
            synchronized (reconfigurationLOCK) {
                if (reconfigured) {
                    // assuming everything has changed (!)
                    quantization = configuration.getCaptureQuantization();
                    captureColors = configuration.isCaptureColors();
                    tick = configuration.getCaptureTick();
                    start = System.currentTimeMillis();
                    captureCount = 0;
                    skipped = 0;
                    resetPreviousCapture();
                    // I'm using a flag to tag the capture as a RESET - it is then easier
                    // to handle the reset message until the assistant without having to
                    // change anything (e.g., merging mechanism in the compressor engine).
                    reset.set(true);
                    Log.info(format("Capture engine has been reconfigured [tile: %d] %s", captureId, configuration));
                    reconfigured = false;
                }
            }
            ++captureCount;
            ++captureId;
            final byte[] pixels = captureColors ? captureFactory.captureScreen(null) : captureFactory.captureScreen(quantization);

            if (pixels == null) {
                // testing purpose (!)
                Log.info("CaptureFactory has finished!");
                break;
            }
            fireOnRawCaptured(captureId, pixels); // debugging purpose (!)
            final CaptureTile[] dirty = computeDirtyTiles(pixels);

            if (dirty != null) {
                final Capture capture = new Capture(captureId, reset.get(), skipped, 0, captureDimension, TILE_DIMENSION, dirty);
                fireOnCaptured(capture); // might update the capture (i.e., merging with previous not sent yet)
                updatePreviousCapture(capture);
                reset.set(false);
            }

            skipped = syncOnTick(start, captureCount, captureId, tick);
            captureCount += skipped;
            captureId += skipped;
        }
        Log.info("The capture engine has been stopped!");
    }

    private static int syncOnTick(final long start, final int captureCount, final int captureId, final long tick) throws InterruptedException {
        int delayedCaptureCount = 0;
        while (true) {
            final long captureMaxEnd = start + (captureCount + delayedCaptureCount) * tick;
            final long capturePause = captureMaxEnd - System.currentTimeMillis();
            if (capturePause < 0) {
                ++delayedCaptureCount;
                Log.warn(format("Skipping capture (%d) %s", captureId + delayedCaptureCount, UnitUtilities.toElapsedTime(-capturePause)));
            } else if (capturePause > 0) {
                Thread.sleep(capturePause);
                return delayedCaptureCount;
            }
        }
    }

    private void resetPreviousCapture() {
        Arrays.fill(previousCapture, Long.MIN_VALUE);
    }

    private void updatePreviousCapture(Capture capture) {
        final CaptureTile[] dirtyTiles = capture.getDirtyTiles();
        for (int idx = 0; idx < dirtyTiles.length; idx++) {
            final CaptureTile dirtyTile = dirtyTiles[idx];
            if (dirtyTile != null) {
                previousCapture[idx] = dirtyTile.getChecksum();
            }
        }
    }

    private CaptureTile[] computeDirtyTiles(byte[] capture) {
        final int x = (captureDimension.width + TILE_DIMENSION.width - 1) / TILE_DIMENSION.width;
        final int y = (captureDimension.height + TILE_DIMENSION.height - 1) / TILE_DIMENSION.height;
        final int length = x * y;
        // change in screen resolution?
        if (length != previousCapture.length) {
            previousCapture = new long[length];
            resetPreviousCapture();
        }
        CaptureTile[] dirty = new CaptureTile[length];
        byte[] tileData;
        boolean hasDirty = false;
        int pixelSize = configuration.isCaptureColors() ? 4 : 1;
        int tileId = 0;
        for (int ty = 0; ty < captureDimension.height; ty += TILE_DIMENSION.height) {
            final int th = min(captureDimension.height - ty, TILE_DIMENSION.height);
            for (int tx = 0; tx < captureDimension.width; tx += TILE_DIMENSION.width) {
                final int tw = Math.min(captureDimension.width - tx, TILE_DIMENSION.width);
                tileData = createTile(capture, captureDimension.width, tw, th, tx, ty, pixelSize);
                final long cs = CaptureTile.computeChecksum(tileData, 0, tileData.length);
                if (cs != previousCapture[tileId]) {
                    dirty[tileId] = new CaptureTile(cs, new Position(tx, ty), tw, th, tileData);
                    hasDirty = true;
                }
                ++tileId;
            }
        }
        return hasDirty ? dirty : null;
    }

    /**
     * Screen-rectangle buffer to tile-rectangle buffer. Use pixelSize 4 for colored and 1 for gray pixels.
     */
    private static byte[] createTile(byte[] capture, int width, int tw, int th, int tx, int ty, int pixelSize) {
        final int capacity = tw * th * pixelSize;
        final byte[] tile = new byte[capacity];
        final int maxSrcPos = capture.length;
        final int maxDestPos = capacity - tw * pixelSize + 1;
        int srcPos = ty * width * pixelSize + tx * pixelSize;
        int destPos = 0;
        while (destPos < maxDestPos && srcPos < maxSrcPos) {
            System.arraycopy(capture, srcPos, tile, destPos, tw * pixelSize);
            srcPos += width * pixelSize;
            destPos += tw * pixelSize;
        }
        return tile;
    }

    private void fireOnCaptured(Capture capture) {
        listeners.getListeners().forEach(listener -> listener.onCaptured(capture));
    }

    private void fireOnRawCaptured(int id, byte[] grays) {
        listeners.getListeners().forEach(listener -> listener.onRawCaptured(id, grays));
    }

}
