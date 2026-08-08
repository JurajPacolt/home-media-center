package org.javerland.homecenter.metadata;

import java.time.Duration;

import org.springframework.web.client.RestClientException;

/**
 * Minimum spacing between calls to a public API. The first scan of a large library would
 * otherwise fire hundreds of requests in a few seconds and get rate limited.
 */
final class RequestThrottle {

    private final String provider;
    private final Duration delay;
    private long nextRequestNanos;

    RequestThrottle(String provider, Duration delay) {
        this.provider = provider;
        this.delay = delay;
    }

    synchronized void await() {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        long remaining = nextRequestNanos - System.nanoTime();
        if (remaining > 0) {
            try {
                Thread.sleep(Duration.ofNanos(remaining));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RestClientException("Čakanie na " + provider + " bolo prerušené", ex);
            }
        }
        nextRequestNanos = System.nanoTime() + delay.toNanos();
    }
}
