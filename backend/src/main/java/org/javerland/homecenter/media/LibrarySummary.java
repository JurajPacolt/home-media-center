package org.javerland.homecenter.media;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Library overview used for three TV tiles and the management UI dashboard. */
public record LibrarySummary(Map<MediaCategory, Long> countsByCategory, long totalItems, long totalSizeBytes) {

    public List<Tile> tiles() {
        return Arrays.stream(MediaCategory.values())
                .map(category -> new Tile(category, category.label(), countsByCategory.getOrDefault(category, 0L)))
                .toList();
    }

    public record Tile(MediaCategory category, String label, long count) {
    }
}
