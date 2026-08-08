package org.javerland.homecenter.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * State of a single scan: the chosen provider, its lookup cache, and a kill switch.
 *
 * <p>The cache exists so a series is searched once and not again for every episode. It is
 * deliberately valid for one scan only — a longer-lived cache would keep serving a title the
 * provider has since corrected.
 *
 * <p>The provider is chosen when the session is created and never changes mid-scan. Once the
 * network fails, {@link #disable()} stops all further calls, and the remaining files keep their
 * previous metadata status so the next scan retries them.
 */
public final class MetadataSession {

    private final @Nullable MetadataProvider provider;
    private final Map<Object, Object> cache = new HashMap<>();
    private boolean available;

    MetadataSession(@Nullable MetadataProvider provider) {
        this.provider = provider;
        this.available = provider != null;
    }

    void disable() {
        available = false;
    }

    boolean available() {
        return available;
    }

    @Nullable MetadataProvider provider() {
        return provider;
    }

    /**
     * Returns a cached value, computing it once per key. Providers use their own key records,
     * so entries of two providers can never collide.
     */
    @SuppressWarnings("unchecked")
    <K, V> V cached(K key, Function<K, V> loader) {
        return (V) cache.computeIfAbsent(key, stored -> loader.apply((K) stored));
    }
}
