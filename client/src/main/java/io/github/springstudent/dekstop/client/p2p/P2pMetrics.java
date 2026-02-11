package io.github.springstudent.dekstop.client.p2p;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public class P2pMetrics {

    private final AtomicLong negotiationSuccess = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();
    private final AtomicLong firstFrameLatencyMills = new AtomicLong(-1);
    private final AtomicLong negotiationStartMills = new AtomicLong(-1);

    public void markNegotiationStart() {
        negotiationStartMills.set(System.currentTimeMillis());
        firstFrameLatencyMills.set(-1);
    }

    public void markP2pActive() {
        negotiationSuccess.incrementAndGet();
    }

    public void markFallback() {
        fallbackCount.incrementAndGet();
    }

    public void markFirstFrame() {
        if (firstFrameLatencyMills.get() >= 0) {
            return;
        }
        long start = negotiationStartMills.get();
        if (start > 0) {
            firstFrameLatencyMills.compareAndSet(-1, System.currentTimeMillis() - start);
        }
    }

    public long getNegotiationSuccess() {
        return negotiationSuccess.get();
    }

    public long getFallbackCount() {
        return fallbackCount.get();
    }

    public long getFirstFrameLatencyMills() {
        return firstFrameLatencyMills.get();
    }
}
